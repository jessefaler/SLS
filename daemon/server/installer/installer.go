package installer

import (
	"os"
	"path/filepath"

	"protoxon.com/sls/daemon/config"
)

// IsInstalled checks if the provided server folder path exists at the root path.
func IsInstalled(serverPath string) bool {
	root := config.Get().Servers.Root
	fullPath := filepath.Join(root, serverPath)

	// Check if the path exists
	info, err := os.Stat(fullPath)
	if err != nil {
		return false // does not exist or cannot be accessed
	}

	return info.IsDir() // make sure it's a directory
}

// todo implement installation
func Install() {
	return
}
