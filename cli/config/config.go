package config

import (
	"os"
	"path/filepath"
)

func ConfigPath() (string, error) {
	dir, err := os.UserConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "sls", "config.yaml"), nil
}
