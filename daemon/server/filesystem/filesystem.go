package filesystem

import (
	"os"

	"protoxon.com/sls/daemon/config"
)

type Filesystem struct {
	path string
}

// New creates a new Filesystem instance for a given server.
func New(root string) (*Filesystem, error) {
	if err := os.MkdirAll(root, 0o755); err != nil {
		return nil, err
	}
	if err := chownPath(root); err != nil {
		return nil, err
	}

	return &Filesystem{
		path: root,
	}, nil
}

func (s *Filesystem) Path() string {
	return s.path
}

func chownPath(path string) error {
	cfg := config.Get()
	if cfg == nil {
		return nil
	}

	return os.Chown(path, cfg.System.User.Uid, cfg.System.User.Gid)
}
