package server

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"strings"
	"sync"

	"emperror.dev/errors"
	"github.com/apex/log"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/events"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/remote"
	"protoxon.com/sls/daemon/server/filesystem"
	"protoxon.com/sls/daemon/system"
)

// Server is the high level definition for a server instance being controlled
// by SLS.
type Server struct {
	powerLock *system.Locker
	// Internal mutex used to block actions that need to occur sequentially, such as
	// writing the configuration to the disk.
	sync.RWMutex
	installLock *system.Locker
	ctx         context.Context
	ctxCancel   *context.CancelFunc

	client remote.Client

	// The console throttler instance used to control outputs.
	throttler    *ConsoleThrottle
	throttleOnce sync.Once

	// The unique identifier for the server
	id string

	// Whether to save this server when its shutdown
	save bool

	// Removes the server from the manager
	Remove func()

	emitterLock sync.Mutex

	sinks map[system.SinkName]*system.SinkPool

	// Maintains the configuration for the server. This is the data that gets returned by protocube
	// such as build settings and container images.
	cfg Configuration

	fs *filesystem.Filesystem

	// Events emitted by the server instance.
	emitter *events.Bus

	resources   ResourceUsage
	Environment environment.ProcessEnvironment `json:"-"`

	// Defines the process configuration for the server instance.
	procConfig *models.ProcessConfiguration

	// Tracks if we've already emitted the very first status update.
	initialStateBroadcast *system.AtomicBool

	installState *installState
}

func New(client remote.Client) (*Server, error) {
	ctx, cancel := context.WithCancel(context.Background())

	server := &Server{
		ctx:         ctx,
		ctxCancel:   &cancel,
		installLock: system.NewLocker(),
		client:      client,
		powerLock:   system.NewLocker(),
		sinks: map[system.SinkName]*system.SinkPool{
			system.LogSink:     system.NewSinkPool(),
			system.InstallSink: system.NewSinkPool(),
		},
		resources: ResourceUsage{
			State: system.NewAtomicString("offline"),
		},
		initialStateBroadcast: system.NewAtomicBool(false),
		installState:          newInstallState(),
	}

	return server, nil
}

// GetEnvironmentVariables Returns all the environment variables that should be assigned to a running
// server instance.
func (s *Server) GetEnvironmentVariables() []string {
	out := []string{
		fmt.Sprintf("TZ=%s", config.Get().System.Timezone),
		fmt.Sprintf("STARTUP=%s", s.Config().Invocation),
		fmt.Sprintf("SERVER_MEMORY=%d", s.MemoryLimit()),
		fmt.Sprintf("SERVER_IP=%s", s.Config().Allocations.DefaultMapping.Ip),
		fmt.Sprintf(" %d", s.Config().Allocations.DefaultMapping.Port),
	}
	if v := s.Config().SoftwareVersion; v != "" {
		out = append(out, fmt.Sprintf("VERSION=%s", v))
	}

eloop:
	for k := range s.Config().EnvVars {
		// Don't allow any environment variables that we have already set above.
		for _, e := range out {
			if strings.HasPrefix(e, strings.ToUpper(k)+"=") {
				continue eloop
			}
		}

		out = append(out, fmt.Sprintf("%s=%s", strings.ToUpper(k), s.Config().EnvVars.Get(k)))
	}

	return out
}

func (s *Server) ProcessConfiguration() *models.ProcessConfiguration {
	s.RLock()
	defer s.RUnlock()

	return s.procConfig
}

func (s *Server) SetProcessConfiguration(cfg *models.ProcessConfiguration) {
	s.Lock()
	defer s.Unlock()
	s.procConfig = cfg
}

// IsRunning determines if the server state is running or not. This is different
// from the environment state, it is simply the tracked state from this daemon
// instance, and not the response from Docker.
func (s *Server) IsRunning() bool {
	st := s.Environment.State()

	return st == environment.ProcessRunningState || st == environment.ProcessStartingState
}

// Reads the log file for a server up to a specified number of lines.
func (s *Server) ReadLogfile(ctx context.Context, lines int) ([]string, error) {
	out, err := s.Environment.Readlog(lines)
	if err != nil {
		out = nil
	}

	installLogs, installErr := s.installLogsForServer(ctx, lines)
	if installErr != nil && err != nil {
		return nil, err
	}

	out = append(out, installLogs...)
	if len(out) > lines {
		out = out[len(out)-lines:]
	}
	return out, nil
}

// Checks if the server is marked as being suspended or not on the system.
func (s *Server) IsSuspended() bool {
	return s.Config().Suspended
}

// Filesystem returns an instance of the mounts for this server.
func (s *Server) Filesystem() *filesystem.Filesystem {
	return s.fs
}

// OnStateChange sets the state of the server internally. This function handles crash detection as
// well as reporting to event listeners for the server.
func (s *Server) OnStateChange() {
	prevState := s.resources.State.Load()

	st := s.Environment.State()
	firstState := false
	if s.initialStateBroadcast != nil && !s.initialStateBroadcast.Load() {
		firstState = true
		s.initialStateBroadcast.Store(true)
	}

	// Update the currently tracked state for the server.
	s.resources.State.Store(st)
	// Skip emitting the initial offline state during server bootstrap.
	if firstState && st == environment.ProcessOfflineState {
		return
	}

	// Emit the event to any listeners that are currently registered.
	if prevState != st {
		err := s.client.StatusUpdate(s.ctx, st, s.id)
		if err != nil {
			log.WithError(err).Warnf("Failed to send status update for server %s", s.ID())
		}
		s.Log().WithField("status", st).Debug("saw server status change event")
		s.Events().Publish(StatusEvent, st)
	}

	// Reset the resource usage to 0 when the process fully stops so that all the UI
	// views in protocube correctly display 0.
	if st == environment.ProcessOfflineState {
		s.resources.Reset()
		s.Events().Publish(StatsEvent, s.Proc())
	}

	// If server was in an online state, and is now in an offline state we should handle
	// that as a crash event. In that scenario, check the last crash time, and the crash
	// counter.
	//
	// In the event that we have passed the thresholds, don't do anything, otherwise
	// automatically attempt to start the process back up for the user. This is done in a
	// separate thread as to not block any actions currently taking place in the flow
	// that called this function.
	if (prevState == environment.ProcessStartingState || prevState == environment.ProcessRunningState) && st == environment.ProcessOfflineState {
		s.Log().Info("detected server as entering a crashed state; running crash handler")

		s.handleServerCrash()
	}

	// If server was running/starting and is now offline, and saving is false, delete it
	// Only delete if there's no active power action (to avoid deleting during restart/stop actions)
	// This handles crashes, console stop commands, disk space limiter, and other edge cases
	if st == environment.ProcessOfflineState {
		if (prevState == environment.ProcessStartingState || prevState == environment.ProcessRunningState) && !s.save && !s.ExecutingPowerAction() {
			go s.Delete()
			return
		}
	}
}

// Cancels the context assigned to this server instance. Assuming background tasks
// are using this server's context for things, all of the background tasks will be
// stopped as a result.
func (s *Server) CtxCancel() {
	if s.ctxCancel != nil {
		(*s.ctxCancel)()
	}
}

// CleanupForDestroy stops all running background tasks for this server that are
// using the context on the server struct. This will cancel any running install
// processes for the server as well.
func (s *Server) CleanupForDestroy() {
	if err := s.client.ServerDeleted(s.ctx, s.id); err != nil {
		log.WithError(err).Warnf("Failed to send deletion event for server %s", s.ID())
	}
	s.CtxCancel()
	s.Events().Destroy()
	s.DestroyAllSinks()
	if s.installLock != nil {
		s.installLock.Destroy()
	}
	// per-server websockets are not implemented yet
	// this will be needed when they are implemented
	//s.Websockets().CancelAll()
	s.powerLock.Destroy()
}

// Delete Deletes a server from the daemon and dissociate its objects.
func (s *Server) Delete() error {

	// Immediately suspend the server to prevent a user from attempting
	// to start it while this process is running.
	s.Config().SetSuspended(true)

	// Notify all websocket clients that the server is being deleted.
	s.Events().Publish(DeletedEvent, nil)

	s.CleanupForDestroy()

	// Destroy the environment; in Docker this will handle a running container and
	// forcibly terminate it before removing the container, so we do not need to handle
	// that here.
	if err := s.Environment.Destroy(); err != nil {
		return err
	}

	// Once the environment is terminated, remove the server files from the system. This is
	// done in a separate process since failure is not the end of the world and can be
	// manually cleaned up after the fact.
	//
	// In addition, servers with large amounts of files can take some time to finish deleting,
	// so we don't want to block the HTTP call while waiting on this.
	go func() {
		fs := s.Filesystem()
		err := fs.Destroy()
		if err != nil {
			log.WithFields(log.Fields{"error": err}).Warn("failed to remove server files during deletion process")
		}
	}()

	// Remove the server from the manager
	s.Remove()
	return nil
}

// Context Returns a context instance for the server. This should be used to allow background
// tasks to be canceled if the server is removed. It will only be canceled when the
// application is stopped or if the server gets deleted.
func (s *Server) Context() context.Context {
	return s.ctx
}

func (s *Server) Log() *log.Entry {
	return log.WithField("server", s.id)
}

func (s *Server) ID() string {
	return s.id
}

// APIResponse is a type returned when requesting details about a single server
// instance on the daemon.
type APIResponse struct {
	State         string        `json:"state"`
	IsSuspended   bool          `json:"is_suspended"`
	Utilization   ResourceUsage `json:"utilization"`
	Configuration Configuration `json:"configuration"`
}

// ToAPIResponse returns the server struct as an API object that can be consumed
// by callers.
func (s *Server) ToAPIResponse() APIResponse {
	return APIResponse{
		State:         s.Environment.State(),
		IsSuspended:   s.IsSuspended(),
		Utilization:   s.Proc(),
		Configuration: *s.Config(),
	}
}

// Sync syncs the state of the server on Protocube with the daemon. This ensures that
// we're always using the state of the server from Protocube and allows us to
// not require successful API calls to the daemon to do things.
//
// This also means mass actions can be performed against servers on Protocube
// and they will automatically sync with the daemon when the server is started.
func (s *Server) Sync() error {
	cfg, err := s.client.GetServerConfiguration(s.Context(), s.ID())
	if err != nil {
		if err := remote.AsRequestError(err); err != nil && err.StatusCode() == http.StatusNotFound {
			return &serverDoesNotExist{}
		}
		return errors.WithStackIf(err)
	}

	if err := s.SyncWithConfiguration(cfg); err != nil {
		return errors.WithStackIf(err)
	}

	// Update the disk space limits for the server whenever the configuration for
	// it changes.
	s.fs.SetDiskLimit(s.DiskSpace())

	s.SyncWithEnvironment()

	return nil
}

// SyncWithConfiguration accepts a configuration object for a server and will
// sync all of the values with the existing server state. This only replaces the
// existing configuration and process configuration for the server. The
// underlying environment will not be affected. This is because this function
// can be called from scoped where the server may not be fully initialized,
// therefore other things like the filesystem and environment may not exist yet.
func (s *Server) SyncWithConfiguration(cfg models.ServerConfiguration) error {
	s.Lock()
	s.procConfig = cfg.ProcessConfiguration
	s.cfg.mu.Lock()
	s.cfg.SoftwareVersion = cfg.SoftwareVersion
	s.cfg.mu.Unlock()
	s.Unlock()
	return nil
}

// EnsureDataDirectoryExists ensures that the data directory for the server
// instance exists.
func (s *Server) EnsureDataDirectoryExists() error {
	if _, err := os.Lstat(s.fs.Path()); err != nil {
		if os.IsNotExist(err) {
			s.Log().Debug("server: creating root directory and setting permissions")
			if err := os.MkdirAll(s.fs.Path(), 0o700); err != nil {
				return errors.WithStack(err)
			}
			if err := s.fs.Chown("/"); err != nil {
				s.Log().WithField("error", err).Warn("server: failed to chown server data directory")
			}
		} else {
			return errors.WrapIf(err, "server: failed to stat server root directory")
		}
	}
	return nil
}
