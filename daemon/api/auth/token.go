package auth

import (
	"crypto/subtle"
	"sync"

	"emperror.dev/errors"
)

// Basic token verification
// todo improve token verification

var (
	token   string
	isSet   bool = false
	tokenMu sync.RWMutex
)

func SetToken(t string) error {
	tokenMu.Lock()
	if t == "" {
		return errors.New("token is empty")
	}
	token = t
	isSet = true
	tokenMu.Unlock()
	return nil
}

func Verify(key string) bool {
	tokenMu.RLock()
	defer tokenMu.RUnlock()
	if !isSet {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(key), []byte(token)) == 1
}
