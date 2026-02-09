package server

import (
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"emperror.dev/errors"
	"github.com/apex/log"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/server/filesystem"
)

// PerformCopy runs the copy entries from the server config. Each entry has Source
// and Target: Source is resolved relative to the SLS root or must be under
// root/allowed mounts; Target is relative to the server filesystem. Called after
// the overlay is mounted so files land in the visible merged fs.
func (s *Server) PerformCopy() error {
	copyList := s.Config().Copy
	if len(copyList) == 0 {
		return nil
	}

	cfg := config.Get()
	rootDir := cfg.System.RootDirectory
	allowed := cfg.System.AllowedMounts
	destRoot := s.Filesystem().Path()

	for _, entry := range copyList {
		sourceSpec := strings.TrimSpace(entry.Source)
		targetSpec := strings.TrimSpace(entry.Target)
		if sourceSpec == "" || targetSpec == "" {
			s.Log().WithField("source", entry.Source).WithField("target", entry.Target).Warn("invalid copy entry: empty source or target")
			continue
		}

		// Resolve source to absolute path; allow under root or allowed mounts
		var absSource string
		if filepath.IsAbs(sourceSpec) {
			var err error
			absSource, err = filepath.Abs(filepath.Clean(sourceSpec))
			if err != nil {
				s.Log().WithError(err).WithField("source", sourceSpec).Warn("copy: failed to resolve source")
				continue
			}
		} else {
			absSource = filepath.Join(rootDir, filepath.Clean(sourceSpec))
		}

		if !isCopySourceAllowed(absSource, rootDir, allowed) {
			s.Log().WithField("source", absSource).Warn("copy: source path not under SLS root or allowed_mounts, skipping")
			continue
		}

		// Target must be under server filesystem
		targetClean := filepath.Clean(targetSpec)
		if targetClean == ".." || strings.HasPrefix(targetClean, ".."+string(filepath.Separator)) {
			s.Log().WithField("target", targetSpec).Warn("copy: target escapes server filesystem, skipping")
			continue
		}
		absDest := filepath.Join(destRoot, targetClean)
		if !filesystem.WithinPath(absDest, destRoot) {
			s.Log().WithField("target", targetSpec).Warn("copy: target escapes server filesystem, skipping")
			continue
		}

		if err := copyPath(s.Log(), absSource, absDest); err != nil {
			s.Log().WithError(err).WithField("source", absSource).WithField("target", absDest).Warn("copy: failed to copy")
			continue
		}

		// Ensure copied files are owned by the server user
		if err := filesystem.ChownRecursiveUnsafe(absDest); err != nil {
			s.Log().WithError(err).WithField("path", absDest).Warn("copy: failed to chown target")
		}
	}

	return nil
}

func isCopySourceAllowed(absSource, rootDir string, allowed []string) bool {
	rootAbs, err := filepath.Abs(filepath.Clean(rootDir))
	if err != nil {
		return false
	}
	if filesystem.WithinPath(absSource, rootAbs) {
		return true
	}
	return isSourceAllowed(absSource, allowed)
}

func copyPath(log *log.Entry, src, dst string) error {
	info, err := os.Stat(src)
	if err != nil {
		return errors.Wrap(err, "stat source")
	}
	if info.IsDir() {
		return copyDir(log, src, dst)
	}
	return copyFile(src, dst, info.Mode())
}

func copyFile(src, dst string, mode fs.FileMode) error {
	in, err := os.Open(src)
	if err != nil {
		return errors.Wrap(err, "open source")
	}
	defer in.Close()

	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return errors.Wrap(err, "mkdir destination parent")
	}

	out, err := os.OpenFile(dst, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, mode.Perm())
	if err != nil {
		return errors.Wrap(err, "create destination")
	}
	defer out.Close()

	if _, err := io.Copy(out, in); err != nil {
		return errors.Wrap(err, "copy content")
	}
	return nil
}

func copyDir(log *log.Entry, srcRoot, dstRoot string) error {
	return filepath.WalkDir(srcRoot, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(srcRoot, path)
		if err != nil {
			return errors.Wrap(err, "rel path")
		}
		dest := filepath.Join(dstRoot, rel)

		if d.IsDir() {
			return os.MkdirAll(dest, 0o755)
		}

		info, err := d.Info()
		if err != nil {
			return err
		}
		if !info.Mode().IsRegular() {
			log.WithField("path", path).Debug("copy: skipping non-regular file")
			return nil
		}
		return copyFile(path, dest, info.Mode())
	})
}
