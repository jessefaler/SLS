package server

import (
	"fmt"
	"sync"
	"time"

	"github.com/apex/log"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/remote"
)

type CrashHandler struct {
	mu sync.RWMutex

	// Tracks the time of the last server crash event.
	lastCrash time.Time
}

// Returns the time of the last crash for this server instance.
func (cd *CrashHandler) LastCrashTime() time.Time {
	cd.mu.RLock()
	defer cd.mu.RUnlock()

	return cd.lastCrash
}

// Sets the last crash time for a server.
func (cd *CrashHandler) SetLastCrash(t time.Time) {
	cd.mu.Lock()
	cd.lastCrash = t
	cd.mu.Unlock()
}

// Looks at the environment exit state to determine if the process exited cleanly or
// if it was the result of an event that we should try to recover from.
//
// This function assumes it is called under circumstances where a crash is suspected
// of occurring. It will not do anything to determine if it was actually a crash, just
// look at the exit state and check if it meets the criteria of being called a crash
// by the daemon.
//
// If the server is determined to have crashed, the process will be restarted and the
// counter for the server will be incremented.
func (s *Server) handleServerCrash() {
	// No point in doing anything here if the server isn't currently offline, there
	// is no reason to do a crash detection event. If the server crash detection is
	// disabled we want to skip anything after this as well.
	if s.Environment.State() != environment.ProcessOfflineState || !s.Config().CrashDetectionEnabled {
		if !s.Config().CrashDetectionEnabled {
			s.Log().Debug("server triggered crash detection but handler is disabled for server process")
			s.PublishConsoleOutputFromDaemon("Aborting automatic restart, crash detection is disabled for this instance.")
		}
	}

	exitCode, oomKilled, _ := s.Environment.ExitState()

	s.PublishConsoleOutputFromDaemon("---------- Detected server process in a crashed state! ----------")
	s.PublishConsoleOutputFromDaemon(fmt.Sprintf("Exit code: %d", exitCode))
	s.PublishConsoleOutputFromDaemon(fmt.Sprintf("Out of memory: %t", oomKilled))

	// Build crash reason
	reason := fmt.Sprintf("Server process exited with code %d", exitCode)
	if oomKilled {
		reason = "Server process was killed due to out of memory"
	}

	// Send crash report to protocube
	crashData := remote.CrashData{
		Reason:    reason,
		ExitCode:  int32(exitCode),
		Timestamp: time.Now(),
	}

	if err := s.client.CrashReport(s.ctx, crashData, s.id); err != nil {
		log.WithError(err).Warnf("Failed to send crash report for server %s", s.ID())
	}
}
