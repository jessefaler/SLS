package server

import (
	"context"
	"fmt"
	"strings"
	"sync"

	"github.com/apex/log"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/events"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/remote"
	"protoxon.com/sls/daemon/server/filesystem"
	"protoxon.com/sls/daemon/system"
	"protoxon.com/sls/daemon/system/id"
)

// Server is the high level definition for a server instance being controlled
// by SLS.
type Server struct {
	powerLock *system.Locker
	// Internal mutex used to block actions that need to occur sequentially, as
	// writing the configuration to the disk.such
	sync.RWMutex
	ctx       context.Context
	ctxCancel *context.CancelFunc

	client remote.Client

	// The console throttler instance used to control outputs.
	throttler    *ConsoleThrottle
	throttleOnce sync.Once

	// The unique identifier for the server
	id string

	emitterLock sync.Mutex

	sinks map[system.SinkName]*system.SinkPool

	// Maintains the configuration for the server. This is the data that gets returned by protocube
	// such as build settings and container images.
	cfg Configuration

	filesystem *filesystem.Filesystem

	// Events emitted by the server instance.
	emitter *events.Bus

	resources   ResourceUsage
	Environment environment.ProcessEnvironment `json:"-"`

	// Defines the process configuration for the server instance.
	procConfig *models.ProcessConfiguration

	// Tracks if we've already emitted the very first status update.
	initialStateBroadcast *system.AtomicBool
}

func New(client remote.Client) (*Server, error) {
	ctx, cancel := context.WithCancel(context.Background())

	server := &Server{
		id:        id.New(),
		ctx:       ctx,
		ctxCancel: &cancel,
		client:    client,
		powerLock: system.NewLocker(),
		sinks: map[system.SinkName]*system.SinkPool{
			system.LogSink: system.NewSinkPool(),
		},
		resources: ResourceUsage{
			State: system.NewAtomicString("offline"),
		},
		initialStateBroadcast: system.NewAtomicBool(false),
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

// SyncConfigurationToEnvironment syncs the server's configuration (limits, mounts, etc.)
// to the environment configuration. This ensures that when containers are created or
// updated, they use the latest configuration from the server.
//
// If the container is already running, this will also attempt to update the container's
// resource limits in place using InSituUpdate.
func (s *Server) SyncConfigurationToEnvironment() error {
	if s.Environment == nil {
		return nil
	}

	s.cfg.mu.RLock()
	settings := environment.Settings{
		Mounts:      s.Mounts(),
		Allocations: s.cfg.Allocations,
		Limits:      s.cfg.Limits,
		Labels:      s.cfg.Labels,
	}
	s.cfg.mu.RUnlock()

	s.Environment.Config().SetSettings(settings)
	s.Environment.Config().SetEnvironmentVariables(s.GetEnvironmentVariables())

	// If the container is already running, try to update its limits in place.
	// This allows limits to be changed without restarting the container.
	if s.IsRunning() {
		if err := s.Environment.InSituUpdate(); err != nil {
			s.Log().WithError(err).Warn("failed to update container limits in place, limits will be applied on next restart")
			// Don't return the error - we'll apply limits on next restart
		}
	}

	return nil
}

/*
func New(bp blueprint.Blueprint) (*Server, error) {

	/*
		// Get an allocation
		allocation, err := environment.Get().Allocations.Allocate()
		if err != nil {
			return nil, errors.Wrap(err, "server: failed to retrieve an allocation")
		}

		server := Server{
			Uuid:       uuid.New(),
			Allocation: allocation,
			blueprint:  bp,
			State:      Offline,
		}

	var server = new(Server)
	server.Uuid = uuid.New()
	return server, nil
}
*/

// Reads the log file for a server up to a specified number of bytes.
func (s *Server) ReadLogfile(len int) ([]string, error) {
	return s.Environment.Readlog(len)
}

// Checks if the server is marked as being suspended or not on the system.
func (s *Server) IsSuspended() bool {
	return s.Config().Suspended
}

// Filesystem returns an instance of the mounts for this server.
func (s *Server) Filesystem() *filesystem.Filesystem {
	return s.filesystem
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
