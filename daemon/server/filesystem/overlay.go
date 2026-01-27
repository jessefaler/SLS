package filesystem

import (
	"os"
	"path/filepath"
	"sync/atomic"

	"emperror.dev/errors"
	"github.com/apex/log"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/internal/overlay"
	"protoxon.com/sls/daemon/models"
)

type OverlayVolume struct {
	Root          string
	Target        string
	mounted       atomic.Bool
	usage         atomic.Int64
	Server        string
	World         string
	ServerOverlay *overlay.Overlay
	WorldOverlay  *overlay.Overlay
}

// NewOverlayVolume creates a new overlay volume at the path
// @param server the path to the servers root directory
// @param world the path to the world's root directory
// @param target the directory to use as the merged this is typically the servers volume
// @param content bundled content
func NewOverlayVolume(root string, target string, server string, world string, content []models.Content) (*OverlayVolume, error) {
	if err := os.MkdirAll(root, 0o755); err != nil {
		return nil, err
	}
	if err := os.MkdirAll(target, 0o755); err != nil {
		return nil, err
	}

	so, err := BuildServerOverlay(root, target, server)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to build server overlay")
	}

	wo, err := BuildWorldOverlay(root, target, world)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to build world overlay")
	}

	// Add content
	for _, content := range content {
		fullPath := filepath.Join(config.Get().Content.Root, content.Source)
		// Check if the content path exists. If not skip it but log an error
		if _, err := os.Stat(fullPath); os.IsNotExist(err) {
			log.WithField("path", fullPath).Debug("Content path does not exist. Skipping...")
			continue
		} else if err != nil {
			log.WithField("path", fullPath).Debug("Failed to check if content path exists. Skipping.")
			continue
		}
		so.AddLower(fullPath)
	}

	return &OverlayVolume{
		Root:          root,
		Server:        server,
		World:         world,
		ServerOverlay: so,
		WorldOverlay:  wo,
	}, nil
}

func BuildServerOverlay(root string, target string, server string) (*overlay.Overlay, error) {
	o := &overlay.Overlay{
		Merged: target,
		Work:   filepath.Join(root, "server/workdir"),
		Upper:  filepath.Join(root, "server/upperdir"),
	}

	// Add the server
	o.AddLower(server)

	// Create the overlay directories
	err := Mkdirs(o.Merged, o.Work, o.Upper)
	if err != nil {
		return nil, err
	}

	return o, nil
}

func BuildWorldOverlay(root string, target string, world string) (*overlay.Overlay, error) {
	o := &overlay.Overlay{
		Merged: filepath.Join(target, "world"),
		Work:   filepath.Join(root, "world/workdir"),
		Upper:  filepath.Join(root, "world/upperdir"),
	}

	// Add the server
	o.AddLower(world)

	// Create the overlay directories
	err := Mkdirs(o.Merged, o.Work, o.Upper)
	if err != nil {
		return nil, err
	}

	return o, nil
}

func (o *OverlayVolume) IsMounted() bool {
	return o.mounted.Load()
}

// Mount mounts the overlay volume
func (o *OverlayVolume) Mount() error {
	if err := o.ServerOverlay.Mount(); err != nil {
		return err
	}
	// Create the world directory
	if err := os.MkdirAll(filepath.Join(o.WorldOverlay.Merged), 0o755); err != nil {
		return err
	}
	if err := o.WorldOverlay.Mount(); err != nil {
		return err
	}

	// Ensure the overlay directories are owned by the server
	err := o.EnsureOwned()
	if err != nil {
		return errors.Wrap(err, "failed to set volume ownership")
	}

	o.mounted.Store(true)
	return nil
}

// Unmount unmounts the overlay volume
func (o *OverlayVolume) Unmount() error {
	var errs []error

	// Unmount the server overlay
	if err := o.ServerOverlay.Unmount(); err != nil {
		errs = append(errs, errors.Wrap(err, "failed to unmount server overlay"))
	}

	// Then Unmount the world overlay
	if err := o.WorldOverlay.Unmount(); err != nil {
		errs = append(errs, errors.Wrap(err, "failed to unmount world overlay"))
	}

	// If any errors happened, combine them
	if len(errs) > 0 {
		return errors.Combine(errs...)
	}

	o.mounted.Store(false)
	return nil
}

// Destroy deletes the overlay volume
func (o *OverlayVolume) Destroy() error {
	var errs []error

	if err := o.Unmount(); err != nil {
		errs = append(errs, errors.Wrap(err, "failed to unmount overlay volume"))
	}

	// Delete the overlay root directory
	if o.Root != "" {
		if err := os.RemoveAll(o.Root); err != nil {
			errs = append(errs, errors.Wrapf(err, "failed to delete overlay volume %s", o.Root))
		}
	}

	if len(errs) > 0 {
		return errors.Combine(errs...)
	}

	return nil
}

// Reset destroys the overlay filesystem and recreates the work and upper directories
func (o *OverlayVolume) Reset() error {
	if err := o.Destroy(); err != nil {
		return err
	}

	// Create the overlay directories
	err := Mkdirs(o.ServerOverlay.Work, o.ServerOverlay.Upper, o.WorldOverlay.Work, o.WorldOverlay.Upper)
	if err != nil {
		return errors.Wrap(err, "failed to create overlay directories")
	}

	return nil
}

// Mkdirs creates the provided directories if they dont exist
func Mkdirs(dirs ...string) error {
	for _, dir := range dirs {
		if dir == "" {
			// skip empty strings
			continue
		}
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return errors.Wrapf(err, "failed to create directory %s", dir)
		}
	}
	return nil
}

// DiskUsage returns the physical disk space usage of the overlay not including the lowerdirs
func (o *OverlayVolume) DiskUsage(allowStaleValue bool) (int64, error) {
	if allowStaleValue {
		return o.CachedUsage(), nil
	}
	size, err := DirectorySizePhysical(o.Root)
	if err != nil {
		return 0, err
	}
	o.SetUsage(size)
	return size, nil
}

// Returns the cached value for the amount of physical disk space used by the overlay filesystem. Do not rely on this
// function for critical logical checks. It should only be used in areas where the actual disk usage
// does not need to be perfect, e.g. API responses for server resource usage.
func (o *OverlayVolume) CachedUsage() int64 {
	return o.usage.Load()
}

// SetUsage updates the total usage of the filesystem.
func (o *OverlayVolume) SetUsage(newUsage int64) int64 {
	return o.usage.Swap(newUsage)
}

// EnsureOwned ensures that all directories the server uses are owned by the server's UID.
// This includes the overlay upper, work, and lower directories for both server and world overlays.
// Ownership of the lower directories is required because OverlayFS preserves
// the original permissions and ownership when copying up files or directories.
// If the server does not own these directories, it will not be able to write to them.
// todo this can likely be fixed with idmapped mounts
func (o *OverlayVolume) EnsureOwned() error {
	// Chown and chmod the server overlay directories
	// Recursively chown the lower directory
	if err := ChownRecursiveUnsafe(o.ServerOverlay.Upper); err != nil {
		return errors.Wrap(err, "failed to recursively chown server overlay upper directory")
	}
	if err := ChownUnsafe(o.ServerOverlay.Work, o.ServerOverlay.Merged); err != nil {
		return errors.Wrap(err, "failed to chown server overlay directories")
	}
	if err := ChownRecursiveUnsafe(o.ServerOverlay.Lower...); err != nil {
		return errors.Wrap(err, "failed to chown server overlay lowerdir")
	}
	if err := ChmodUnsafe(0o755, o.ServerOverlay.Upper, o.ServerOverlay.Work); err != nil {
		return errors.Wrap(err, "failed to chmod server overlay directories")
	}
	// Chmod the lower directories
	if err := ChmodUnsafe(0o755, o.ServerOverlay.Lower...); err != nil {
		return errors.Wrap(err, "failed to chmod server lower directories")
	}
	// Chown and chmod the world overlay directories
	// Recursively chown the lower directory
	if err := ChownRecursiveUnsafe(o.WorldOverlay.Lower...); err != nil {
		return errors.Wrap(err, "failed to recursively chown world overlay upper directory")
	}
	if err := ChownUnsafe(o.WorldOverlay.Work, o.WorldOverlay.Merged); err != nil {
		return errors.Wrap(err, "failed to chown world overlay directories")
	}
	if err := ChmodUnsafe(0o755, o.WorldOverlay.Upper, o.WorldOverlay.Work); err != nil {
		return errors.Wrap(err, "failed to chmod world overlay directories")
	}
	// Chmod the lower directories
	if err := ChmodUnsafe(0o755, o.WorldOverlay.Lower...); err != nil {
		return errors.Wrap(err, "failed to chmod world lower directories")
	}
	return nil
}
