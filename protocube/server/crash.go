package server

import (
	"sync"
	"time"
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

type CrashData struct {
	Reason    string    `json:"reason"`
	ExitCode  int32     `json:"exit_code"`
	Timestamp time.Time `json:"timestamp"`
}

func (s *Server) HandleServerCrash(data CrashData) {
	s.crasher.SetLastCrash(data.Timestamp)
	s.PublishEvent(CrashEvent, data)
}
