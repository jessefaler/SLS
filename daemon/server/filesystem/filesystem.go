package filesystem

import (
	"fmt"
	"os"
	"path/filepath"
	"syscall"

	"emperror.dev/errors"
	"golang.org/x/sys/unix"
	"protoxon.com/sls/daemon/config"
)

type Filesystem struct {
	path    string
	overlay string
}

// New creates a new Filesystem instance for a given server.
func New(root string, overlay string) (*Filesystem, error) {
	if err := os.MkdirAll(root, 0o755); err != nil {
		return nil, err
	}
	if err := chownPath(root); err != nil {
		return nil, err
	}

	return &Filesystem{
		path:    root,
		overlay: overlay,
	}, nil
}

func (s *Filesystem) Path() string {
	return s.path
}

// Overlay returns the path to the servers overlay folder
func (s *Filesystem) Overlay() string {
	return s.overlay
}

func chownPath(path string) error {
	cfg := config.Get()
	if cfg == nil {
		return nil
	}

	return os.Chown(path, cfg.System.User.Uid, cfg.System.User.Gid)
}

// Delete removes all mounts and directories associated with this filesystem.
// It unmounts all overlay filesystems and bind mounts in the correct order,
// then deletes the overlay directory and volume directory.
// If unmount operations fail, it will attempt forced unmounts and continue
// with deletion regardless of unmount errors.
func (s *Filesystem) Delete() error {
	return CleanupServerVolume(s.path, s.overlay)
}

// CleanupServerVolume removes all mounts and directories created by BuildServerVolume.
// It unmounts all overlay filesystems and bind mounts in the correct order,
// then deletes the overlay directory and volume directory.
// This function is used by Filesystem.Delete() and can also be called directly
// from the server package if volume creation succeeds but subsequent steps fail.
func CleanupServerVolume(volumePath string, overlayRoot string) error {
	// Define paths for unmounting
	serverOverlayMerged := filepath.Join(overlayRoot, "server", "merged")
	serverOverlayWorld := filepath.Join(serverOverlayMerged, "world")
	worldOverlayMerged := filepath.Join(overlayRoot, "world", "merged")
	serverLowerdir := filepath.Join(overlayRoot, "server", "lowerdir")
	worldLowerdir := filepath.Join(overlayRoot, "world", "lowerdir")

	var unmountErrors []error

	// Unmount in reverse order of mounting (innermost to outermost)
	// Collect errors but continue processing all mounts

	// Unmount the volume (bind mount from serverOverlay to volume)
	if err := unmountIfMounted(volumePath); err != nil {
		unmountErrors = append(unmountErrors, errors.Wrap(err, "failed to unmount volume"))
	}

	// Unmount the world overlay from serverOverlay/world (bind mount)
	if err := unmountIfMounted(serverOverlayWorld); err != nil {
		unmountErrors = append(unmountErrors, errors.Wrap(err, "failed to unmount world overlay from server overlay"))
	}

	// Unmount the server overlay filesystem
	if err := unmountIfMounted(serverOverlayMerged); err != nil {
		unmountErrors = append(unmountErrors, errors.Wrap(err, "failed to unmount server overlay filesystem"))
	}

	// Unmount the world overlay filesystem
	if err := unmountIfMounted(worldOverlayMerged); err != nil {
		unmountErrors = append(unmountErrors, errors.Wrap(err, "failed to unmount world overlay filesystem"))
	}

	// Unmount server lowerdir bind mount
	if err := unmountIfMounted(serverLowerdir); err != nil {
		unmountErrors = append(unmountErrors, errors.Wrap(err, "failed to unmount server lowerdir"))
	}

	// Unmount world lowerdir bind mount
	if err := unmountIfMounted(worldLowerdir); err != nil {
		unmountErrors = append(unmountErrors, errors.Wrap(err, "failed to unmount world lowerdir"))
	}

	// Continue with deletion even if unmounts failed
	var deleteErrors []error

	// Delete the overlay root directory
	if overlayRoot != "" {
		if err := os.RemoveAll(overlayRoot); err != nil {
			deleteErrors = append(deleteErrors, errors.Wrapf(err, "failed to delete overlay directory %s", overlayRoot))
		}
	}

	// Delete the volume directory
	if volumePath != "" {
		if err := os.RemoveAll(volumePath); err != nil {
			deleteErrors = append(deleteErrors, errors.Wrapf(err, "failed to delete volume directory %s", volumePath))
		}
	}

	// Combine all errors if any occurred
	if len(unmountErrors) > 0 || len(deleteErrors) > 0 {
		var allErrors []error
		allErrors = append(allErrors, unmountErrors...)
		allErrors = append(allErrors, deleteErrors...)

		errorMsg := "cleanup completed with errors"
		if len(unmountErrors) > 0 {
			errorMsg += fmt.Sprintf(" (%d unmount error(s))", len(unmountErrors))
		}
		if len(deleteErrors) > 0 {
			errorMsg += fmt.Sprintf(" (%d delete error(s))", len(deleteErrors))
		}

		return errors.WithMessage(
			errors.Combine(allErrors...),
			errorMsg,
		)
	}

	return nil
}

// unmountIfMounted attempts to unmount a path if it is currently mounted.
// It tries multiple strategies: lazy unmount (MNT_DETACH), regular unmount, and forced unmount.
// If the path is not mounted or doesn't exist, it returns nil (no error).
// Returns an error only if all unmount strategies fail and the path is definitely mounted.
func unmountIfMounted(path string) error {
	// Check if the path exists
	if _, err := os.Stat(path); os.IsNotExist(err) {
		// Path doesn't exist, nothing to unmount
		return nil
	} else if err != nil {
		// Some other error occurred, but we'll still try to unmount
		// in case it's a permission issue but the mount exists
	}

	// Helper to check if error is EINVAL (not mounted)
	isNotMounted := func(err error) bool {
		if err == nil {
			return false
		}
		if errno, ok := err.(syscall.Errno); ok && errno == unix.EINVAL {
			return true
		}
		return false
	}

	var lastErr error
	var err error

	// Strategy 1: Try lazy unmount (MNT_DETACH) - this allows processes to continue using
	// the filesystem while unmounting happens in the background
	if err = unix.Unmount(path, unix.MNT_DETACH); err == nil {
		return nil
	}
	if isNotMounted(err) {
		return nil
	}
	lastErr = err

	// Strategy 2: Try regular unmount
	if err = unix.Unmount(path, 0); err == nil {
		return nil
	}
	if isNotMounted(err) {
		return nil
	}
	lastErr = err

	// Strategy 3: Try forced unmount (MNT_FORCE) - this will force unmount even if busy
	// Note: This requires CAP_SYS_ADMIN, but we try it anyway
	if err = unix.Unmount(path, unix.MNT_FORCE); err == nil {
		return nil
	}
	if isNotMounted(err) {
		return nil
	}
	lastErr = err

	// All strategies failed with a non-EINVAL error
	// Return the last error but note that we tried multiple strategies
	return errors.Wrapf(lastErr, "failed to unmount %s (tried lazy, regular, and forced unmount)", path)
}
