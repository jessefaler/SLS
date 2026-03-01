package server

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"time"
)

const (
	// InstallLockFile is the lock file name inside the server directory.
	// A leading dot keeps it hidden in typical directory listings.
	InstallLockFile = ".lock"

	// InstallLockTimeout is the max time an install may take, and the max time
	// to wait for another server's install lock. After this, the lock is treated
	// as stale (installer times out and removes folder; waiter removes folder and retries).
	InstallLockTimeout = 10 * time.Minute
)

// ErrInstallLockStale is returned by WaitForInstallLockReleased when the lock
// still exists after the timeout; the waiter has removed the server folder so
// the caller should retry the install flow.
var ErrInstallLockStale = errors.New("install lock timed out (stale)")

// IsBaseInstalled reports whether the server base at serverPath already exists
// (i.e. a previous install completed successfully). No separate marker file is
// used; folder existence means installed.
func IsBaseInstalled(serverPath string) bool {
	info, err := os.Stat(serverPath)
	return err == nil && info.IsDir()
}

// InstallLockExists reports whether serverPath/.lock exists, meaning an
// installation is currently in progress for this base path.
func InstallLockExists(serverPath string) bool {
	_, err := os.Stat(lockPath(serverPath))
	return err == nil
}

func lockPath(serverPath string) string {
	return filepath.Join(serverPath, InstallLockFile)
}

// WaitForInstallLockReleased blocks until serverPath/.lock does not exist (install
// finished or failed), the timeout expires, or ctx is canceled. If the lock still
// exists after timeout, the server folder is removed (stale lock) and
// ErrInstallLockStale is returned so the caller can retry the install flow.
func WaitForInstallLockReleased(ctx context.Context, serverPath string, timeout time.Duration) error {
	deadlineCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	ticker := time.NewTicker(500 * time.Millisecond)
	defer ticker.Stop()
	for {
		if !InstallLockExists(serverPath) {
			return nil
		}
		select {
		case <-deadlineCtx.Done():
			if InstallLockExists(serverPath) {
				_ = os.RemoveAll(serverPath)
				return ErrInstallLockStale
			}
			return nil
		case <-ticker.C:
			// re-check lock above
		}
	}
}

// AcquireInstallLock tries to take an exclusive install lock for serverPath by
// creating serverPath/.lock with O_CREATE|O_EXCL. The lock lives inside the
// server directory so it is hidden and removed when the folder is deleted.
//
// Only call when the folder does not exist. AcquireInstallLock creates the
// folder, then tries to create .lock. The first process to create .lock runs
// install; others wait. When the lock is released, waiters that then acquire
// the lock check whether the directory already has installed content; if so
// they release the lock and return needInstall false.
//
// Caller with needInstall true must run install then release(). The folder
// already exists (created here). On install failure, caller must RemoveAll(serverPath)
// then release(). On success, just release().
func AcquireInstallLock(serverPath string) (release func(), needInstall bool, err error) {
	for {
		if IsBaseInstalled(serverPath) {
			return func() {}, false, nil
		}
		if err := os.MkdirAll(serverPath, 0o755); err != nil {
			return nil, false, err
		}
		lp := lockPath(serverPath)
		f, err := os.OpenFile(lp, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o644)
		if err == nil {
			releaseFn := func() {
				_ = f.Close()
				_ = os.Remove(lp)
			}
			// Another process may have created the folder, installed, and released
			// while we were waiting; we just acquired the lock. If the dir has
			// content other than .lock, they finished first.
			entries, err := os.ReadDir(serverPath)
			if err != nil {
				releaseFn()
				return nil, false, err
			}
			for _, e := range entries {
				if e.Name() != InstallLockFile {
					releaseFn()
					return func() {}, false, nil
				}
			}
			return releaseFn, true, nil
		}
		if !os.IsExist(err) {
			return nil, false, err
		}
		time.Sleep(500 * time.Millisecond)
	}
}
