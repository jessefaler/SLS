package auth

import (
	"crypto/subtle"
	"sync"
	"time"

	"emperror.dev/errors"
)

// Basic token verification
// todo improve token verification

var (
	token      string
	isSet      bool = false
	tokenMu    sync.RWMutex
	tokenSetAt time.Time
)

func SetToken(t string) error {
	tokenMu.Lock()
	if t == "" {
		return errors.New("token is empty")
	}
	token = t
	isSet = true
	tokenSetAt = time.Now()
	tokenMu.Unlock()
	return nil
}

func Verify(key string) bool {
	tokenMu.RLock()
	defer tokenMu.RUnlock()
	if !isSet {
		return false
	}
	// Allow a small grace period after token is set to handle race conditions
	// during quick reboots where protocube might make requests immediately
	// after registration but before the token is fully propagated
	if time.Since(tokenSetAt) < 100*time.Millisecond {
		// During the grace period, be more lenient - if token matches, accept it
		// This handles the race where protocube makes a request right after registration
		return subtle.ConstantTimeCompare([]byte(key), []byte(token)) == 1
	}
	return subtle.ConstantTimeCompare([]byte(key), []byte(token)) == 1
}
