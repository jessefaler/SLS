package blueprint

import (
	"maps"
	"os"
	"path/filepath"
	"strings"

	"github.com/apex/log"
)

// loadAllYAML walks root recursively for .yaml/.yml files, loads each with load,
// and rejects duplicate IDs. Failed file access or load errors are logged and skipped.
func loadAllYAML[T any](
	root, kind string,
	load func(path string) (*T, error),
	id func(*T) string,
) ([]*T, error) {
	var items []*T
	seenIDs := make(map[string]struct{})
	kindTitle := strings.ToUpper(kind[:1]) + kind[1:]

	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			log.WithField("path", path).Warnf("%s parser: Failed to access file: %v", kind, err)
			return nil
		}

		if info.IsDir() {
			return nil
		}

		ext := strings.ToLower(filepath.Ext(info.Name()))
		if ext != ".yaml" && ext != ".yml" {
			return nil
		}

		item, loadErr := load(path)
		if loadErr != nil {
			log.WithField(kind, path).Warnf("Failed to load %s: %v", kind, loadErr)
			return nil
		}

		itemID := id(item)
		if _, exists := seenIDs[itemID]; exists {
			log.WithField("id", itemID).
				WithField("file", path).
				Errorf("%s validation failed: %s with ID '%s' already exists", kindTitle, kind, itemID)
			return nil
		}

		seenIDs[itemID] = struct{}{}
		items = append(items, item)
		return nil
	})
	if err != nil {
		return nil, err
	}

	return items, nil
}

// MergeState merges the provided environment variables into the base states environment variables
// (env wins on key collision)
func MergeState(base *State, env map[string]string) *State {
	if len(env) == 0 {
		return base
	}
	out := &State{}
	if base != nil {
		out.Volumes = base.Volumes
		out.Mounts = base.Mounts
		out.Copy = base.Copy
		if len(base.Env) > 0 {
			out.Env = maps.Clone(base.Env)
		}
	}
	for k, v := range env {
		if out.Env == nil {
			out.Env = make(map[string]string, len(env))
		}
		out.Env[k] = v
	}
	return out
}
