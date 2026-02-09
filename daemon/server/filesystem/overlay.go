package filesystem

import (
	"os"
	"path/filepath"
	"sync"
	"sync/atomic"

	"emperror.dev/errors"
	"protoxon.com/sls/daemon/internal/overlay"
)

// OverlayVolume represents a single logical volume composed of multiple OverlayFS
type OverlayVolume struct {
	mu         sync.Mutex
	Root       string
	mounted    atomic.Bool
	usage      atomic.Int64
	Overlays   []*overlay.Overlay
	ServerPath string
}

// NewOverlayVolume creates a new overlay volume at the path
// root is the directory where the overlay's will store their work and upper directories
// root = internal/overlay2/<server_id>
func NewOverlayVolume(root string, serverPath string) (*OverlayVolume, error) {
	return &OverlayVolume{
		Root:       root,
		ServerPath: serverPath,
	}, nil
}

func (ov *OverlayVolume) addOverlay(overlay *overlay.Overlay) {
	ov.mu.Lock()
	defer ov.mu.Unlock()
	ov.Overlays = append(ov.Overlays, overlay)
}

// NewOverlay creates and adds a new overlay to the volume.
// The given name is used to create a folder under the volume's root,
// with work and upper directories created inside it.
func (ov *OverlayVolume) NewOverlay(name string, lowerDirs []string, merged string) *overlay.Overlay {
	o := overlay.New(filepath.Join(ov.Root, name), lowerDirs, merged)
	ov.addOverlay(o)
	return o
}

func (ov *OverlayVolume) IsMounted() bool {
	return ov.mounted.Load()
}

// Mount mounts the overlay volume
func (ov *OverlayVolume) Mount() error {
	ov.mu.Lock()
	defer ov.mu.Unlock()

	// Mount the overlays
	for _, o := range ov.Overlays {

		// Ensure the work upper and merged directories exist
		err := Mkdirs(o.Work, o.Upper, o.Merged)
		if err != nil {
			return errors.Wrapf(err, "failed to create directories for overlay %s", o.Merged)
		}

		// Mount the overlay
		if err := o.Mount(); err != nil {
			return err
		}

	}

	// Ensure the overlay directories are owned by the server
	err := ov.EnsureOwned()
	if err != nil {
		return errors.Wrap(err, "failed to set overlay volume ownership")
	}

	ov.mounted.Store(true)
	return nil
}

// Unmount unmounts the overlay volume
func (ov *OverlayVolume) Unmount() error {
	ov.mu.Lock()
	defer ov.mu.Unlock()

	var errs []error

	for _, o := range ov.Overlays {
		if err := o.Unmount(); err != nil {
			errs = append(errs, err)
		}
	}

	// If any errors happened, combine them
	if len(errs) > 0 {
		return errors.Combine(errs...)
	}

	ov.mounted.Store(false)
	return nil
}

// Destroy unmounts the overlay volume and deletes the root directory
func (ov *OverlayVolume) Destroy() error {
	var errs []error

	if err := ov.Unmount(); err != nil {
		errs = append(errs, errors.Wrap(err, "failed to unmount overlay volume"))
	}

	// Delete the overlay root directory
	if ov.Root != "" {
		if err := os.RemoveAll(ov.Root); err != nil {
			errs = append(errs, errors.Wrapf(err, "failed to delete overlay volume %s", ov.Root))
		}
	}

	if len(errs) > 0 {
		return errors.Combine(errs...)
	}

	return nil
}

// Reset destroys the overlay filesystem and recreates the work and upper directories
func (ov *OverlayVolume) Reset() error {
	if err := ov.Destroy(); err != nil {
		return err
	}

	for _, o := range ov.Overlays {
		// Create the overlay directories
		err := Mkdirs(o.Work, o.Upper)
		if err != nil {
			return errors.Wrap(err, "failed to create overlay directories")
		}
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
func (ov *OverlayVolume) DiskUsage(allowStaleValue bool) (int64, error) {
	if allowStaleValue {
		return ov.CachedUsage(), nil
	}
	size, err := DirectorySizePhysical(ov.Root)
	if err != nil {
		return 0, err
	}
	ov.SetUsage(size)
	return size, nil
}

// Returns the cached value for the amount of physical disk space used by the overlay filesystem. Do not rely on this
// function for critical logical checks. It should only be used in areas where the actual disk usage
// does not need to be perfect, e.g. API responses for server resource usage.
func (ov *OverlayVolume) CachedUsage() int64 {
	return ov.usage.Load()
}

// SetUsage updates the total usage of the filesystem.
func (ov *OverlayVolume) SetUsage(newUsage int64) int64 {
	return ov.usage.Swap(newUsage)
}

// EnsureOwned ensures that all overlay directories are owned by the container user
// Ownership of the lower directories is required because OverlayFS preserves
// the original permissions and ownership when copying up files or directories.
// If the server does not own these directories, it will not be able to write to them.
// todo this can likely be fixed with user groups or idmapped mounts
func (ov *OverlayVolume) EnsureOwned() error {
	// Chown and chmod the overlay directories
	for _, o := range ov.Overlays {
		if err := ChownRecursiveUnsafe(o.Upper, o.Work); err != nil {
			return errors.Wrap(err, "failed to recursively chown overlay directory")
		}
		if err := ChownRecursiveUnsafe(o.Lower...); err != nil {
			return errors.Wrap(err, "failed to recursively chown overlay lower directory")
		}
		if err := ChmodUnsafe(0o755, o.Upper, o.Work); err != nil {
			return errors.Wrap(err, "failed to chmod overlay directory")
		}
		if err := ChmodUnsafe(0o755, o.Lower...); err != nil {
			return errors.Wrap(err, "failed to chmod overlay lower directory")
		}
		// Perform a non-recursive chown on the merged directory so the server can access its files.
		// A recursive chown would propagate ownership to all files, causing them to be copied up unnecessarily.
		if err := ChownUnsafe(o.Merged); err != nil {
			return errors.Wrap(err, "failed to chown world overlay directories")
		}
	}
	return nil
}
