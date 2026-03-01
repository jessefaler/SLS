package server

import (
	"context"
	"fmt"
	"os"
	"time"

	"emperror.dev/errors"
	"github.com/google/uuid"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/environment/docker"
)

type PowerAction string

// The power actions that can be performed for a given server. This taps into the given server
// environment and performs them in a way that prevents a race condition from occurring. For
// example, sending two "start" actions back to back will not process the second action until
// the first action has been completed.
//
// This utilizes a workerpool with a limit of one worker so that all the actions execute
// in a sync manner.
const (
	PowerActionStart     = "start"
	PowerActionStop      = "stop"
	PowerActionRestart   = "restart"
	PowerActionTerminate = "kill"
	PowerActionPause     = "pause"
	PowerActionUnpause   = "unpause"
)

// IsValid checks if the power action being received is valid.
func (pa PowerAction) IsValid() bool {
	return pa == PowerActionStart ||
		pa == PowerActionStop ||
		pa == PowerActionTerminate ||
		pa == PowerActionRestart ||
		pa == PowerActionPause ||
		pa == PowerActionUnpause
}

func (pa PowerAction) IsStart() bool {
	return pa == PowerActionStart || pa == PowerActionRestart
}

// ExecutingPowerAction checks if there is currently a power action being
// processed for the server.
func (s *Server) ExecutingPowerAction() bool {
	return s.powerLock.IsLocked()
}

// HandlePowerAction is a helper function that can receive a power action and then process the
// actions that need to occur for it. This guards against someone calling Start() twice at the
// same time, or trying to restart while another restart process is currently running.
//
// However, the code design for the daemon does depend on the user correctly calling this
// function rather than making direct calls to the start/stop/restart functions on the
// environment struct.
func (s *Server) HandlePowerAction(action PowerAction, waitSeconds ...int) error {

	lockId, _ := uuid.NewUUID()
	log := s.Log().WithField("lock_id", lockId.String()).WithField("action", action)

	cleanup := func() {
		log.Info("releasing exclusive lock for power action")
		s.powerLock.Release()
	}

	var wait int
	if len(waitSeconds) > 0 && waitSeconds[0] > 0 {
		wait = waitSeconds[0]
	}

	log.WithField("wait_seconds", wait).Debug("acquiring power action lock for instance")
	// Only attempt to acquire a lock on the process if this is not a termination event. We want to
	// just allow those events to pass right through for good reason. If a server is currently trying
	// to process a power action but has gotten stuck you still should be able to pass through the
	// terminate event. The good news here is that doing that oftentimes will get the stuck process to
	// move again, and naturally continue through the process.
	if action != PowerActionTerminate && action != PowerActionPause && action != PowerActionUnpause {
		// Determines if we should wait for the lock or not. If a value greater than 0 is passed
		// into this function we will wait that long for a lock to be acquired.
		if wait > 0 {
			ctx, cancel := context.WithTimeout(s.ctx, time.Second*time.Duration(wait))
			defer cancel()

			// Attempt to acquire a lock on the power action lock for up to 30 seconds. If more
			// time than that passes an error will be propagated back up the chain and this
			// request will be aborted.
			if err := s.powerLock.TryAcquire(ctx); err != nil {
				return errors.Wrap(err, fmt.Sprintf("could not acquire lock on power action after %d seconds", wait))
			}
		} else {
			// If no wait duration was provided we will attempt to immediately acquire the lock
			// and bail out with a context deadline error if it is not acquired immediately.
			if err := s.powerLock.Acquire(); err != nil {
				return errors.Wrap(err, "failed to acquire exclusive lock for power actions")
			}
		}

		log.Info("acquired exclusive lock on power actions, processing event...")
		defer cleanup()
	} else {
		// Still try to acquire the lock if terminating, and it is available, just so that
		// other power actions are blocked until it has completed. However, if it cannot be
		// acquired we won't stop the entire process.
		//
		// If we did successfully acquire the lock, make sure we release it once we're done
		// executiong the power actions.
		if err := s.powerLock.Acquire(); err == nil {
			log.Info("acquired exclusive lock on power actions, processing event...")
			defer cleanup()
		} else {
			log.Warn("failed to acquire exclusive lock, ignoring failure for termination event")
		}
	}

	switch action {
	case PowerActionStart:
		switch s.Environment.State() {
		case environment.ProcessPausedState:
			// Start on a paused container means unpause (resume).
			return s.Environment.Unpause(s.Context())
		case environment.ProcessOfflineState:
			// continue
		default:
			return ErrIsRunning
		}

		// Run the pre-boot logic for the server before processing the environment start.
		if err := s.onBeforeStart(); err != nil {
			if !s.save {
				// If saving is false delete the server if startup failed
				go s.Delete()
			}
			return err
		}

		err := s.Environment.Start(s.Context())
		// If the server failed to start and saving is disabled delete the server
		if err != nil && !s.save {
			go s.Delete()
		}
		return err
	case PowerActionStop:
		fallthrough
	case PowerActionRestart:
		// We're specifically waiting for the process to be stopped here, otherwise the lock is
		// released too soon, and you can rack up all sorts of issues.
		if err := s.Environment.WaitForStop(s.Context(), time.Minute*2, true); err != nil {
			// Even timeout errors should be bubbled back up the stack. If the process didn't stop
			// nicely, but the terminate argument was passed then the server is stopped without an
			// error being returned.
			//
			// However, if terminate is not passed you'll get a context deadline error. We could
			// probably handle that nicely here, but I'd rather just pass it back up the stack for now.
			// Either way, any type of error indicates we should not attempt to start the server back
			// up.
			return err
		}

		if action == PowerActionStop {
			// delete the server if saving is disabled
			if !s.save {
				go s.Delete()
			}
			return nil
		}

		// Now actually try to start the process by executing the normal pre-boot logic.
		if err := s.onBeforeStart(); err != nil {
			if !s.save {
				// If saving is false delete the server if startup failed
				go s.Delete()
			}
			return err
		}

		err := s.Environment.Start(s.Context())
		// If restart failed and saving is disabled delete the server
		if err != nil && !s.save {
			go s.Delete()
		}
		return err
	case PowerActionTerminate:
		err := s.Environment.Terminate(s.Context(), "SIGKILL")
		// If saving is disabled delete the server after termination
		if err == nil && !s.save {
			go s.Delete()
		}
		return err
	case PowerActionPause:
		if s.Environment.State() == environment.ProcessPausedState {
			return nil // already paused
		}
		if s.Environment.State() != environment.ProcessRunningState {
			// Sync state in case we're running but state was stale
			if running, _ := s.Environment.IsRunning(s.Context()); !running {
				return nil // not running (or already paused)
			}
		}
		return s.Environment.Pause(s.Context())
	case PowerActionUnpause:
		if s.Environment.State() != environment.ProcessPausedState {
			// Sync state in case we're paused but state was stale
			if running, _ := s.Environment.IsRunning(s.Context()); running {
				return nil // already running (not paused)
			}
			// IsRunning may have synced state to ProcessPausedState
			if s.Environment.State() != environment.ProcessPausedState {
				return nil // not paused
			}
		}
		return s.Environment.Unpause(s.Context())
	}

	return errors.New("attempting to handle unknown power action")
}

// HandleReset resets the server's overlay filesystem. If the server is running,
// it will stop the server, wait for it to fully stop, reset the overlay, and
// then start it back up if it was running before.
func (s *Server) HandleReset() error {
	// Check if server is running first - we only need the lock if it's running
	running, err := s.Environment.IsRunning(s.Context())
	if err != nil {
		return errors.Wrap(err, "failed to check if server is running during reset")
	}

	wasRunning := running
	var lockAcquired bool

	if running {
		// Only acquire the power lock if the server is running - this prevents
		// deletion when saving is false during the stop operation
		if err := s.powerLock.Acquire(); err != nil {
			return errors.Wrap(err, "failed to acquire exclusive lock for reset")
		}
		lockAcquired = true
		s.Log().Info("acquired power lock for reset, stopping server...")

		// Server is running, stop it first
		if err := s.Environment.Stop(s.Context()); err != nil {
			s.powerLock.Release()
			return errors.Wrap(err, "failed to stop server instance during reset")
		}

		// Wait for the server to fully stop before resetting
		if err := s.Environment.WaitForStop(s.Context(), time.Minute*10, true); err != nil {
			s.powerLock.Release()
			return errors.Wrap(err, "failed to wait for server to stop during reset")
		}
	}

	// Once the server is fully stopped (or wasn't running), call reset
	if err := s.Filesystem().Overlay().Reset(); err != nil {
		if lockAcquired {
			s.powerLock.Release()
		}
		return errors.Wrap(err, "failed to reset server instance")
	}

	// If the server was running before the reset, start it back up
	// Release the lock first so HandlePowerAction can acquire it
	if wasRunning {
		s.powerLock.Release()
		if err := s.HandlePowerAction(PowerActionStart); err != nil {
			return errors.Wrap(err, "failed to start server after reset")
		}
	}

	return nil
}

// Execute a few functions before actually calling the environment start commands. This ensures
// that everything is ready to go for environment booting, and that the server can even be started.
func (s *Server) onBeforeStart() error {
	s.Log().Info("syncing server configuration with protocube")
	if err := s.Sync(); err != nil {
		return errors.WithMessage(err, "unable to sync server data from Protocube")
	}

	// Disallow start & restart if the server is suspended. Do this check after performing a sync
	// action with the Panel to ensure that we have the most up-to-date information for that server.
	if s.IsSuspended() {
		return ErrSuspended
	}

	// Ensure we sync the server information with the environment so that any new environment variables
	// and process resource limits are correctly applied.
	s.SyncWithEnvironment()

	// Install server base files only if the folder does not exist. Use a lock
	// file serverPath/.lock so only one process installs; others wait and then
	// skip when the folder has content (installed). If the lock is held longer
	// than InstallLockTimeout, installers exit with timeout (and remove folder)
	// and waiters remove the folder and retry.
	serverPath := s.Filesystem().Overlay().ServerPath
	for {
		if !IsBaseInstalled(serverPath) {
			release, needInstall, err := AcquireInstallLock(serverPath)
			if err != nil {
				return errors.Wrap(err, "install lock")
			}
			if needInstall {
				installCtx, cancel := context.WithTimeout(s.Context(), InstallLockTimeout)
				installErr := s.Install(installCtx)
				cancel()
				if installErr != nil {
					release()
					_ = os.RemoveAll(serverPath)
					if errors.Is(installErr, context.DeadlineExceeded) {
						return errors.Wrap(installErr, "installation timed out after 10 minutes")
					}
					return installErr
				}
				release()
			} else {
				release()
			}
		}

		// If the folder exists but .lock is present, another server is still installing
		// this base. Wait for it to finish (or for InstallLockTimeout); if timeout,
		// WaitForInstallLockReleased removes the folder and returns ErrInstallLockStale
		// and we retry so we can install ourselves.
		err := WaitForInstallLockReleased(s.Context(), serverPath, InstallLockTimeout)
		if errors.Is(err, ErrInstallLockStale) {
			continue
		}
		if err != nil {
			return errors.Wrap(err, "wait for install lock")
		}
		break
	}

	// Mount the overlay filesystem
	err := s.Filesystem().Overlay().Mount()
	if err != nil {
		return errors.Wrap(err, "failed to mount filesystem")
	}

	// Copy files into the server filesystem from state configuration (source:destination)
	if err := s.PerformCopy(); err != nil {
		s.Log().WithError(err).Warn("failed to perform state copy entries")
	}

	// Update the configuration files defined for the server before beginning the boot process.
	// This process executes a bunch of parallel updates, so we just block until that process
	// is complete. Any errors as a result of this will just be bubbled out in the logger,
	// we don't need to actively do anything about it at this point, worse comes to worst the
	// server starts in a weird state and the user can manually adjust.
	s.PublishConsoleOutputFromDaemon("Updating process configuration files...")
	s.Log().Debug("updating server configuration files...")
	s.UpdateConfigurationFiles()
	s.Log().Debug("updated server configuration files")

	s.Log().Info("completed server preflight, starting boot process...")

	// Check a servers disk space asynchronously that way we can start the server up as fast as possible
	go func() {
		// If a server has unlimited disk space, we don't care enough to block the startup to check remaining.
		// However, we should trigger a size anyway, as it'd be good to kick it off for other processes.
		if s.DiskSpace() <= 0 {
			s.Filesystem().HasSpaceAvailable(true)
		} else {
			s.PublishConsoleOutputFromDaemon("Checking server disk space usage, this could take a few seconds...")
			if err := s.Filesystem().HasSpaceErr(false); err != nil {
				s.PublishConsoleOutputFromDaemon("Disk space error: " + err.Error())
			}
			// Update the cached usage of the overlay filesystem
			_, err := s.Filesystem().Overlay().DiskUsage(false)
			if err != nil {
				s.Log().WithError(err).Error("failed to update overlay disk usage")
			}
		}
	}()

	return nil
}

// SyncWithEnvironment updates the environment for the server to match any of
// the changed data. This pushes new settings and environment variables to the
// environment. In addition, the in-situ update method is called on the
// environment which will allow environments that make use of it (such as Docker)
// to immediately apply some settings without having to wait on a server to
// restart.
//
// This functionality allows a server's resources limits to be modified on the
// fly and have them apply right away allowing for dynamic resource allocation
// and responses to abusive server processes.
func (s *Server) SyncWithEnvironment() {
	s.Log().Debug("syncing server settings with environment")

	cfg := s.Config()

	// Update the environment settings using the new information from this server.
	s.Environment.Config().SetSettings(environment.Settings{
		Mounts:      s.Mounts(),
		Allocations: cfg.Allocations,
		Limits:      cfg.Build,
		Labels:      cfg.Labels,
	})

	// For Docker specific environments we also want to update the configured image
	// and stop configuration.
	if e, ok := s.Environment.(*docker.Environment); ok {
		s.Log().Debug("syncing stop configuration with configured docker environment")
		e.SetImage(cfg.Container.Image)
		e.SetStopConfiguration(s.ProcessConfiguration().Stop)
	}

	// If build limits are changed, environment variables also change. Plus, any modifications to
	// the startup command also need to be properly propagated to this environment.
	s.Environment.Config().SetEnvironmentVariables(s.GetEnvironmentVariables())

	if !s.IsSuspended() {
		// Update the environment in place, allowing memory and CPU usage to be adjusted
		// on the fly without the user needing to reboot (theoretically).
		s.Log().Info("performing server limit modification on-the-fly")
		if err := s.Environment.InSituUpdate(); err != nil {
			// This is not a failure, the process is still running fine and will fix itself on the
			// next boot, or fail out entirely in a more logical position.
			s.Log().WithField("error", err).Warn("failed to perform on-the-fly update of the server environment")
		}
	} else {
		// Checks if the server is now in a suspended state. If so and a server process is currently running it
		// will be gracefully stopped (and terminated if it refuses to stop).
		if s.Environment.State() != environment.ProcessOfflineState {
			s.Log().Info("server suspended with running process state, terminating now")

			go func(s *Server) {
				if err := s.Environment.WaitForStop(s.Context(), time.Minute, true); err != nil {
					s.Log().WithField("error", err).Warn("failed to terminate server environment after suspension")
				}
			}(s)
		}
	}
}
