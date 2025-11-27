//go:build linux

package server

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"emperror.dev/errors"
	"github.com/apex/log"
	"golang.org/x/sys/unix"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/models"
)

type OverlayDirs struct {
	Lower  []string
	Merged string
	Work   string
	Upper  string
}

// AddLower is a helper to add a lowerdir to the overlay
func (o *OverlayDirs) AddLower(path string) {
	o.Lower = append(o.Lower, path)
}

// BuildServerVolume constructs a unified, copy-on-write server filesystem for a container.
//
// This function creates separate OverlayFS instances for:
//  1. The server files
//  2. The world folder
//  3. Optional additional content
//
// Each overlay has its own upperdir, workdir, and merged directory. The merged directories
// of the world overlay (and other content overlays) are bind-mounted into the server overlay
// under their respective folders (e.g., /world) to create a single, unified view of the server.
//
// Separate overlays are necessary because OverlayFS does not support nested mounts or
// adding subdirectories with independent copy-on-write layers directly to a lowerdir.
// By using separate overlays and mounting their merged directories into the server overlay,
// we achieve full copy-on-write functionality without copying all files at container startup,
// which saves time and disk space.
//
// The function finally exposes the server overlay through a dedicated container volume
// (bind-mounted), which can be used as the root filesystem for the container.
func BuildServerVolume(id string, serverPath string, worldPath string, content []models.Content) (string, error) {
	cfg := config.Get()
	volumeRoot := filepath.Join(cfg.System.RootDirectory, "volumes")
	overlayRoot := filepath.Join(cfg.System.RootDirectory, "internal", "overlay2", id)
	volume := filepath.Join(volumeRoot, id)

	if err := ensureTreeOwned(serverPath); err != nil {
		return "", errors.Wrap(err, "failed to chown server files")
	}
	if err := ensureTreeOwned(worldPath); err != nil {
		return "", errors.Wrap(err, "failed to chown world files")
	}

	if err := ensureDirectoryOwned(volumeRoot); err != nil {
		return "", errors.Wrapf(err, "failed to create volumes directory %s", filepath.Join(config.Get().System.RootDirectory, "volumes"))
	}
	if err := ensureDirectoryOwned(volume); err != nil {
		return "", errors.Wrap(err, "failed to create container volume directory")
	}
	if err := ensureDirectoryOwned(overlayRoot); err != nil {
		return "", errors.Wrapf(err, "failed to prepare overlay root %s", overlayRoot)
	}

	serverOverlay, err := BuildServerOverlay(overlayRoot, serverPath, content)
	if err != nil {
		return "", errors.WrapIf(err, "failed to build server overlay")
	}

	worldOverlay, err := BuildWorldOverlay(overlayRoot, worldPath)
	if err != nil {
		return "", errors.WrapIf(err, "failed to build world overlay")
	}

	worldTarget := filepath.Join(serverOverlay, "world")
	if err := ensureDirectoryOwned(worldTarget); err != nil {
		return "", errors.Wrap(err, "failed to create world mountpoint inside server overlay")
	}

	// Mount the contents of the server directory into the lowerdir of the overlay filesystem
	if err := unix.Mount(worldOverlay, filepath.Join(serverOverlay, "world"), "", unix.MS_BIND|unix.MS_REC, ""); err != nil {
		return "", errors.Wrap(err, "failed to mount the world overlay to the server overlay")
	}

	// Mount the contents of the server directory into the lowerdir of the overlay filesystem
	if err := unix.Mount(serverOverlay, volume, "", unix.MS_BIND|unix.MS_REC, ""); err != nil {
		return "", errors.Wrap(err, "failed to mount the server overlay to the container volume")
	}

	return volume, nil
}

func BuildWorldOverlay(root string, worldPath string) (string, error) {

	worldOverlay := filepath.Join(root, "world")

	overlayfs := OverlayDirs{
		Merged: filepath.Join(worldOverlay, "merged"),
		Work:   filepath.Join(worldOverlay, "workdir"),
		Upper:  filepath.Join(worldOverlay, "upperdir"),
	}

	// Create the overlay directories
	for _, path := range []string{overlayfs.Merged, overlayfs.Work, overlayfs.Upper} {
		if err := ensureDirectoryOwned(path); err != nil {
			return "", errors.Wrapf(err, "failed to create overlay directory %s", path)
		}
	}

	// Create the lowerdir directory, the world will be mounted here and this will serve as the lower directory
	lowerdir := filepath.Join(worldOverlay, "lowerdir")
	if err := ensureDirectoryOwned(lowerdir); err != nil {
		return "", errors.Wrapf(err, "failed to create lowerdir folder %s", lowerdir)
	}

	// Mount the contents of the world directory into the lowerdir directory
	if err := unix.Mount(worldPath, lowerdir, "", unix.MS_BIND|unix.MS_REC|unix.MS_RDONLY, ""); err != nil {
		return "", errors.Wrap(err, "failed to mount the server folder to the lowerdir of the overlayfs")
	}

	// Add the lowerdir directory as the lowerdir
	overlayfs.AddLower(lowerdir)

	// Mount the overlay file system
	err := MountOverlay(overlayfs)
	if err != nil {
		return "", errors.Wrapf(err, "failed to mount overlay filesystem at %s", overlayfs.Lower)
	}

	return overlayfs.Merged, nil
}

func BuildServerOverlay(root string, serverPath string, content []models.Content) (string, error) {

	// Set the server directly as a lower dir
	// mount the world to a world folder then set that directly as the lower dir also
	serverOverlay := filepath.Join(root, "server")

	overlayfs := OverlayDirs{
		Merged: filepath.Join(serverOverlay, "merged"),
		Work:   filepath.Join(serverOverlay, "workdir"),
		Upper:  filepath.Join(serverOverlay, "upperdir"),
	}

	// Create the overlay directories
	for _, path := range []string{overlayfs.Merged, overlayfs.Work, overlayfs.Upper} {
		if err := ensureDirectoryOwned(path); err != nil {
			return "", errors.Wrapf(err, "failed to create overlay directory %s", path)
		}
	}

	// Create the lowerdir directory, the server will be mounted here and this will serve as the lower directory
	lowerdir := filepath.Join(serverOverlay, "lowerdir")
	if err := ensureDirectoryOwned(lowerdir); err != nil {
		return "", errors.Wrapf(err, "failed to create lowerdir folder %s", lowerdir)
	}

	// Mount the contents of the server directory into the lowerdir directory
	if err := unix.Mount(serverPath, lowerdir, "", unix.MS_BIND|unix.MS_REC|unix.MS_RDONLY, ""); err != nil {
		return "", errors.Wrap(err, "failed to mount the server folder to the lowerdir of the overlayfs")
	}

	// Add the lowerdir directory as the lowerdir
	overlayfs.AddLower(lowerdir)
	// Add content as lower directories
	for _, content := range content {
		fullPath := filepath.Join(config.Get().Content.Root, content.Source)
		// Check if the content path exists. If not skip it but log an error
		if _, err := os.Stat(fullPath); os.IsNotExist(err) {
			log.WithField("path", fullPath).Debug("Content path does not exist. Skipping.")
			continue
		} else if err != nil {
			log.WithField("path", fullPath).Debug("Failed to check if content path exists. Skipping.")
			continue
		}
		overlayfs.AddLower(fullPath)
	}

	// Mount the overlay file system
	err := MountOverlay(overlayfs)
	if err != nil {
		return "", errors.Wrapf(err, "failed to mount overlay filesystem at %s", overlayfs.Lower)
	}

	return overlayfs.Merged, nil
}

// MountOverlay mounts an overlay filesystem at 'merged' using the given lower, upper, and work directories.
func MountOverlay(overlayfs OverlayDirs) error {
	lower := strings.Join(overlayfs.Lower, ":")
	opts := fmt.Sprintf("lowerdir=%s,upperdir=%s,workdir=%s",
		lower, overlayfs.Upper, overlayfs.Work)

	if err := unix.Mount("overlay", overlayfs.Merged, "overlay", 0, opts); err != nil {
		return fmt.Errorf("failed to mount overlay: %w", err)
	}
	return nil
}

func ensureDirectoryOwned(path string) error {
	if err := os.MkdirAll(path, 0o755); err != nil {
		return err
	}
	return chownToDaemon(path)
}

func chownToDaemon(path string) error {
	cfg := config.Get()
	if cfg == nil {
		return nil
	}

	return os.Lchown(path, cfg.System.User.Uid, cfg.System.User.Gid)
}

func ensureTreeOwned(root string) error {
	if root == "" {
		return nil
	}

	if _, err := os.Stat(root); err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}

	return filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		return chownToDaemon(path)
	})
}
