package server

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/docker/docker/client"
	"github.com/gammazero/workerpool"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/environment/docker"
	"protoxon.com/sls/daemon/internal/overlay"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/remote"
	"protoxon.com/sls/daemon/server/filesystem"
	"protoxon.com/sls/daemon/system"
)

type Manager struct {
	mutex   sync.RWMutex
	client  remote.Client
	servers map[string]*Server // key = server ID
}

// NewManager returns a new server manager instance.
func NewManager(client remote.Client) *Manager {
	return &Manager{
		client:  client,
		servers: make(map[string]*Server),
	}
}

// Add a server to the collection
func (m *Manager) Add(server *Server) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	m.servers[server.id] = server
}

// Get returns a single server instance and a boolean value indicating if it was
// found in the global collection or not.
func (m *Manager) Get(id string) (*Server, bool) {
	match := m.Find(func(server *Server) bool {
		return server.id == id
	})
	return match, match != nil
}

func (m *Manager) Client() remote.Client {
	return m.client
}

// Find returns a single element from the collection matching the filter. If
// nothing is found, a nil result is returned.
func (m *Manager) Find(filter func(match *Server) bool) *Server {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	for _, server := range m.servers {
		if filter(server) {
			return server
		}
	}
	return nil
}

// Remove removes a server from the collection by its ID.
func (m *Manager) Remove(id string) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	delete(m.servers, id)
}

// All returns a snapshot of all servers managed by this instance.
func (m *Manager) All() []*Server {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	servers := make([]*Server, 0, len(m.servers))
	for _, s := range m.servers {
		servers = append(servers, s)
	}
	return servers
}

// InitServer initializes a server using the provided server configuration data
func (m *Manager) InitServer(req models.ServerConfiguration) (*Server, error) {
	s, err := New(m.client)
	if err != nil {
		return nil, err
	}

	// This will delete the server if saving is false and any of the following steps fail
	created := false
	defer func() {
		if !created {
			s.Events().Destroy()
			s.DestroyAllSinks()
		}
	}()

	s.save = req.Save
	s.Config().SkipInstallScripts = req.SkipInstallScript
	s.Remove = func() {
		m.Remove(s.id)
	}
	s.Config().Build = req.Limits
	s.Config().Allocations = req.Allocations
	s.id = req.Id

	// Replace the server.build.default.port variable with the servers actual port
	// todo add support for other variables in the invocation
	invocation := req.Invocation
	invocation = strings.ReplaceAll(invocation, "{{server.build.default.port}}", fmt.Sprintf("%d", s.Config().Allocations.DefaultMapping.Port))

	s.Config().Invocation = invocation
	s.SetProcessConfiguration(req.ProcessConfiguration)
	s.Config().Container.Image = req.Image
	s.Config().SoftwareVersion = req.SoftwareVersion

	// Get the path of the base server folder
	serverFolder := filepath.Join(config.Get().System.Servers, req.ServerFolder)

	// If the server does not have an installation script or is configured to skip it check to ensure
	// that the server folder exists before proceeding that way if it doesn't exist we can send an error back to the user
	if !req.HasInstallScript || req.SkipInstallScript {
		if exists, err := overlay.DirExists(serverFolder); err != nil {
			return nil, errors.Wrapf(err, "failed to check server folder: %s", serverFolder)
		} else if !exists {
			return nil, errors.Wrapf(ErrInvalidServerConfig,
				"server folder does not exist: either add an install script to your software configuration or manually create the server folder. path=%s", serverFolder)
		}
	}

	// Create the path to the servers volume
	volume := filepath.Join(config.Get().System.Data, s.id)

	// create the overlay volume
	ov, err := filesystem.NewOverlayVolume(filepath.Join(config.Get().System.RootDirectory, "internal", "overlay", s.id), volume, serverFolder)
	if err != nil {
		return nil, err
	}

	// Apply custom host mounts from the state configuration (validated against AllowedMounts in s.Mounts()).
	mounts := make([]Mount, 0, len(req.State.Mounts))
	for _, m := range req.State.Mounts {
		mounts = append(mounts, Mount(m))
	}
	s.cfg.Mounts = mounts

	// Set environment variables from the state configuration
	if len(req.State.Env) > 0 {
		envVars := make(environment.Variables, len(req.State.Env))
		for k, v := range req.State.Env {
			envVars[k] = v
		}
		s.cfg.EnvVars = envVars
	}

	// Set volume mounts from the state configuration
	volumesRoot := filepath.Join(config.Get().System.Volumes)
	volumeMounts := make([]Mount, 0, len(req.State.Volumes))
	for _, v := range req.State.Volumes {
		switch v.Mode {
		case models.VolumeModeCOW:
			continue
		case models.VolumeModeRO, models.VolumeModeRW:
			// RW/RO mounts
			resolved := filepath.Join(volumesRoot, filepath.Clean(v.Source))
			absResolved, err := filepath.Abs(resolved)
			if err != nil {
				return nil, errors.Wrapf(ErrInvalidServerConfig, "volume '%s': invalid source path: %s", v.Name, v.Source)
			}
			if !filesystem.WithinPath(absResolved, volumesRoot) {
				return nil, errors.Wrapf(ErrInvalidServerConfig, "volume '%s': invalid source path: %s source path must be under %s", v.Name, v.Source, volumesRoot)
			}
			if exists, err := overlay.DirExists(absResolved); err != nil {
				return nil, errors.Wrapf(err, "volume '%s': failed to check source path: %s", v.Name, absResolved)
			} else if !exists {
				return nil, errors.Wrapf(ErrInvalidServerConfig, "volume '%s': source path does not exist: %s", v.Name, absResolved)
			}
			target := filepath.Clean(v.Target)
			if target == "." {
				target = "/"
			}
			// Target must be the path inside the container; the server root in the container is /home/container
			containerTarget := filepath.Join("/home/container", strings.TrimPrefix(target, "/"))
			volumeMounts = append(volumeMounts, Mount(environment.Mount{
				Source:   absResolved,
				Target:   containerTarget,
				ReadOnly: v.Mode == models.VolumeModeRO,
			}))
		default:
			return nil, errors.Wrapf(ErrInvalidServerConfig, "invalid volume mode %s for volume %s", v.Mode, v.Name)
		}
	}
	s.cfg.VolumeMounts = volumeMounts

	// Create the server's main overlay
	serverOverlay := ov.NewOverlay(system.PathId("/"), []string{serverFolder}, volume)

	// Group COW volumes by their target path
	cowGroups := make(map[string][]models.Volume)
	for _, v := range req.State.Volumes {
		if v.Mode == models.VolumeModeCOW {
			cowGroups[v.Target] = append(cowGroups[v.Target], v)
		}
	}

	// Create overlays for each target path
	for target, vols := range cowGroups {
		if len(vols) == 0 {
			continue
		}

		// Combine sources (validate each COW source is under volumesRoot)
		sources := make([]string, 0, len(vols))
		for _, v := range vols {
			resolved := filepath.Join(volumesRoot, filepath.Clean(v.Source))
			absResolved, err := filepath.Abs(resolved)
			if err != nil {
				return nil, errors.Wrapf(ErrInvalidServerConfig, "volume '%s': invalid source path: %s", v.Name, v.Source)
			}
			if !filesystem.WithinPath(absResolved, volumesRoot) {
				return nil, errors.Wrapf(ErrInvalidServerConfig, "volume '%s': invalid source path: %s source path must be under %s", v.Name, resolved, volumesRoot)
			}
			if exists, err := overlay.DirExists(absResolved); err != nil {
				return nil, errors.Wrapf(err, "volume '%s': failed to check source path: %s", v.Name, absResolved)
			} else if !exists {
				return nil, errors.Wrapf(ErrInvalidServerConfig, "volume '%s': source path does not exist: %s", v.Name, absResolved)
			}
			sources = append(sources, absResolved)
		}

		// If the target is root ("/", ".", ""), append to the server overlay's lowerdirs
		cleanTarget := filepath.Clean(target)
		if cleanTarget == "/" || cleanTarget == "." || cleanTarget == "" {
			serverOverlay.AddLower(sources...)
			continue
		}

		// create a new overlay
		name := system.PathId(target)
		overlayTarget := filepath.Join(volume, strings.TrimPrefix(cleanTarget, "/"))
		ov.NewOverlay(name, sources, overlayTarget)
	}

	// Set files to copy into the server filesystem
	// These will be copied when the server starts
	s.cfg.Copy = req.State.Copy

	// create the filesystem
	// denylist is not used for now so it is set to nil
	s.fs, err = filesystem.New(volume, ov, s.DiskSpace(), nil)
	if err != nil {
		return nil, errors.WithStackIf(err)
	}

	// set the servers environment settings
	settings := environment.Settings{
		Mounts:      s.Mounts(),
		Allocations: s.Config().Allocations,
		Limits:      s.cfg.Build,
		Labels:      s.cfg.Labels,
	}

	envCfg := environment.NewConfiguration(settings, s.GetEnvironmentVariables())
	meta := docker.Metadata{
		Image: s.Config().Container.Image,
		Stop:  req.ProcessConfiguration.Stop,
	}

	env, err := docker.New(s.id, &meta, envCfg)
	if err != nil {
		return nil, err
	}

	s.Environment = env
	s.StartEventListeners()

	// Indicate that the server was successfully created so that it is not removed by the defer function
	created = true
	// Add the server to the manager
	m.Add(s)
	return s, nil
}

// PersistStates writes the current environment states to the disk for each
// server that has saving enabled. This is generally called at a specific interval defined in the root
// runner command to avoid hammering disk I/O when tons of server switch states
// at once. It is fine if this file falls slightly out of sync, it is just here
// to make recovering from an unexpected system reboot a little easier.
func (m *Manager) PersistStates() error {
	states := map[string]string{}
	for _, s := range m.All() {
		if s.save == true {
			// only write the state for servers that have saving enabled
			states[s.ID()] = s.Environment.State()
		}
	}
	data, err := json.Marshal(states)
	if err != nil {
		return errors.WithStack(err)
	}
	if err := os.WriteFile(config.Get().System.GetStatesPath(), data, 0o644); err != nil {
		return errors.WithStack(err)
	}
	return nil
}

// ReadStates returns the state of the servers.
func (m *Manager) ReadStates() (map[string]string, error) {
	f, err := os.OpenFile(config.Get().System.GetStatesPath(), os.O_RDONLY|os.O_CREATE, 0o644)
	if err != nil {
		return nil, errors.WithStack(err)
	}
	defer f.Close()
	var states map[string]string
	if err := json.NewDecoder(f).Decode(&states); err != nil && err != io.EOF {
		return nil, errors.WithStack(err)
	}
	out := make(map[string]string, 0)
	// Only return states for servers that we're currently tracking in the system.
	for id, state := range states {
		if _, ok := m.Get(id); ok {
			out[id] = state
		}
	}
	return out, nil
}

// Sync syncs all servers on the daemon with protocube
func (m *Manager) Sync(ctx context.Context) error {
	log.Info("fetching list of servers from API")
	servers, err := m.client.GetServers(ctx, config.Get().RemoteQuery.BootServersPerPage)
	if err != nil {
		if !remote.IsRequestError(err) {
			return errors.WithStackIf(err)
		}
		return errors.WrapIf(err, "manager: failed to retrieve server configurations")
	}

	start := time.Now()
	log.WithField("total_configs", len(servers)).Info("processing servers returned by the API")

	pool := workerpool.New(runtime.NumCPU())
	log.Debugf("using %d workerpools to instantiate server instances", runtime.NumCPU())
	for _, data := range servers {
		s := data
		pool.Submit(func() {
			_, err := m.InitServer(s)
			if err != nil {
				log.WithField("server", s.Id).WithField("error", err).Error("failed to load server, skipping...")
				return
			}
		})
	}

	// Wait until we've processed all the configuration files in the directory
	// before continuing.
	pool.StopWait()

	err = m.LoadServers(ctx)
	if err != nil {
		return err
	}

	diff := time.Now().Sub(start)
	log.WithField("duration", fmt.Sprintf("%s", diff)).Info("finished processing server configurations")

	return nil
}

func (m *Manager) LoadServers(ctx context.Context) error {
	// Create a new workerpool that limits us to 4 servers being bootstrapped at a time
	// on the daemon. This allows us to ensure the environment exists, write configurations,
	// and reboot processes without causing a slow-down due to sequential booting.
	pool := workerpool.New(4)
	for _, serv := range m.All() {
		s := serv

		// For each server we encounter make sure the root data directory exists.
		if err := s.EnsureDataDirectoryExists(); err != nil {
			s.Log().Error("could not create root data directory for server: not loading server...")
			continue
		}

		states, err := m.ReadStates()
		if err != nil {
			log.WithField("error", err).Error("failed to retrieve locally cached server states from disk, assuming all servers in offline state")
		}

		pool.Submit(func() {
			var st string
			if state, exists := states[s.ID()]; exists {
				st = state
			}

			// Ephemeral servers (save=false) should be deleted on daemon boot if they're offline.
			if !s.save {
				ctx, cancel := context.WithTimeout(ctx, time.Second*30)
				defer cancel()

				r, err := s.Environment.IsRunning(ctx)
				if err != nil && !client.IsErrNotFound(err) {
					s.Log().WithField("error", err).Error("error checking server environment status")
				}

				// If ephemeral server is offline, delete it immediately
				if !r {
					go s.Delete()
					return
				}

				// If server is still running reconfigure it
				s.Log().Info("configuring server environment")
				s.Environment.SetState(environment.ProcessRunningState)
				if err := s.Environment.Attach(ctx); err != nil {
					s.Log().WithField("error", err).Warn("failed to attach to running server environment")
				}
				return
			}

			s.Log().Info("configuring server environment and restoring to previous state")
			// Use a timed context here to avoid booting issues where Docker hangs for a
			// specific container that would cause the daemon to be un-bootable until the entire
			// machine is rebooted. It is much better for us to just have a single failed
			// server instance than an entire offline node.
			ctx, cancel := context.WithTimeout(ctx, time.Second*30)
			defer cancel()

			r, err := s.Environment.IsRunning(ctx)
			// We ignore missing containers because we don't want to actually block booting of the daemon at this
			// point. If we didn't do this, and you pruned all the images and then started the daemon you could
			// end up waiting a long period of time for all the images to be re-pulled on the daemon boot rather
			// than when the server itself is started.
			if err != nil && !client.IsErrNotFound(err) {
				s.Log().WithField("error", err).Error("error checking server environment status")
			}

			// Check if the server was previously running. If so, attempt to start the server now so that the daemon
			// can pick up where it left off. If the environment does not exist at all, just create it and then allow
			// the normal flow to execute.
			//
			// This does mean that booting the daemon after a catastrophic machine crash and wiping out the Docker images
			// as a result will result in a slow boot.
			if !r && (st == environment.ProcessRunningState || st == environment.ProcessStartingState) {
				if err := s.HandlePowerAction(PowerActionStart); err != nil {
					s.Log().WithField("error", err).Warn("failed to return server to running state")
				}
			} else if r || (!r && s.IsRunning()) {
				// If the server is currently running on Docker, mark the process as being in that state.
				// We never want to stop an instance that is currently running external from the daemon since
				// that is a good way of keeping things running even if the daemon gets in a very corrupted state.
				//
				// This will also validate that a server process is running if the last tracked state we have
				// is that it was running, but we see that the container process is not currently running.
				s.Log().Info("detected server is running, re-attaching to process...")

				s.Environment.SetState(environment.ProcessRunningState)
				if err := s.Environment.Attach(ctx); err != nil {
					s.Log().WithField("error", err).Warn("failed to attach to running server environment")
				}
			} else {
				// At this point we've determined that the server should indeed be in an offline state, so we'll
				// make a call to set that state just to ensure we don't ever accidentally end up with some invalid
				// state being tracked.
				s.Environment.SetState(environment.ProcessOfflineState)
			}

			if state := s.Environment.State(); state == environment.ProcessStartingState || state == environment.ProcessRunningState {
				s.Log().Debug("re-syncing server configuration for already running server")
				if err := s.Sync(); err != nil {
					s.Log().WithError(err).Error("failed to re-sync server configuration")
				}
			}
		})
	}
	return nil
}
