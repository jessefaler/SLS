package server

import (
	"context"
	"fmt"
	"time"

	"emperror.dev/errors"
	"github.com/google/uuid"
	"protoxon.com/sls/daemon/environment"
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
)

// IsValid checks if the power action being received is valid.
func (pa PowerAction) IsValid() bool {
	return pa == PowerActionStart ||
		pa == PowerActionStop ||
		pa == PowerActionTerminate ||
		pa == PowerActionRestart
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
	if action != PowerActionTerminate {
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
		if s.Environment.State() != environment.ProcessOfflineState {
			return ErrIsRunning
		}

		return s.Environment.Start(s.Context())
	case PowerActionStop:
		fallthrough
	case PowerActionRestart:
		// We're specifically waiting for the process to be stopped here, otherwise the lock is
		// released too soon, and you can rack up all sorts of issues.
		if err := s.Environment.WaitForStop(s.Context(), time.Minute*10, true); err != nil {
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
			return nil
		}

		return s.Environment.Start(s.Context())
	case PowerActionTerminate:
		return s.Environment.Terminate(s.Context(), "SIGKILL")
	}

	return errors.New("attempting to handle unknown power action")
}
