package server

import (
	"path/filepath"
	"strings"

	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
)

// Mount To avoid confusion when working with mounts, assume that a server.Mount has not been properly
// cleaned up and had the paths set. An environment.Mount should only be returned with valid paths
// that have been checked.
type Mount environment.Mount

// Mounts Returns the default container mounts for the server instance. This includes the data directory
// for the server. Previously this would also mount in host timezone files, however we've moved from
// that approach to just setting `TZ=Timezone` environment values in containers which should work
// in most scenarios.
func (s *Server) Mounts() []environment.Mount {
	m := []environment.Mount{
		{
			Default:  true,
			Target:   "/home/container",
			Source:   s.Filesystem().Path(),
			ReadOnly: false,
		},
	}

	// Also include any of this server's custom mounts when returning them.
	return append(m, s.customMounts()...)
}

// Returns the custom mounts for a given server after verifying that they are within a list of
// allowed mount points for the node.
func (s *Server) customMounts() []environment.Mount {
	var mounts []environment.Mount
	allowed := config.Get().System.AllowedMounts

	for _, mount := range s.Config().Mounts {
		source, err := filepath.Abs(filepath.Clean(mount.Source))
		if err != nil {
			s.Log().WithError(err).WithField("mount_source", mount.Source).Warn("rejecting custom mount: cannot resolve absolute path")
			continue
		}

		target := filepath.Clean(mount.Target)
		if target == "." {
			target = "/"
		}

		if !isSourceAllowed(source, allowed) {
			s.Log().WithField("mount_source", source).Warn("rejecting custom mount: source outside allowed paths")
			continue
		}

		mounts = append(mounts, environment.Mount{
			Source:   source,
			Target:   target,
			ReadOnly: mount.ReadOnly,
		})
	}

	return mounts
}

func isSourceAllowed(source string, allowed []string) bool {
	if len(allowed) == 0 {
		return false
	}

	for _, root := range allowed {
		if root == "" {
			continue
		}

		absoluteRoot, err := filepath.Abs(filepath.Clean(root))
		if err != nil {
			continue
		}

		if withinPath(source, absoluteRoot) {
			return true
		}
	}

	return false
}

func withinPath(path string, root string) bool {
	path = filepath.Clean(path)
	root = filepath.Clean(root)

	if path == root {
		return true
	}

	if !strings.HasSuffix(root, string(filepath.Separator)) {
		root = root + string(filepath.Separator)
	}

	return strings.HasPrefix(path, root)
}
