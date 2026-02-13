package server

import (
	"context"
	"os"
	"path/filepath"
	"sync"
	"time"

	"emperror.dev/errors"
	"github.com/docker/docker/client"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/remote"
)

// Installer is responsible for setting up the base server files for a specific software version.
// Once installed, any blueprint that requires this software version can skip the installer.
// Typically, the installer runs only once per software version, unless a reinstallation is explicitly requested.

type Installer struct {
	mu        sync.Mutex
	Processes map[string]*InstallationProcess // Server path -> Install process
}

type InstallationProcess struct {
	client *client.Client
	done   chan struct{} // closed when install finishes
	ctx    context.Context
	cancel context.CancelFunc
}

// IsInstalled checks if the server is installed
// Returns false if the server is in the process of installing
// or if the server path doesn't exist
func (i *Installer) IsInstalled(serverPath string) bool {
	if i.IsInstalling(serverPath) {
		return false
	}
	root := config.Get().System.Servers
	fullPath := filepath.Join(root, serverPath)

	// Check if the path exists
	info, err := os.Stat(fullPath)
	if err != nil {
		return false // does not exist or cannot be accessed
	}

	return info.IsDir() // make sure it's a directory
}

// IsInstalling Checks if a server is being installed at the provided path
func (i *Installer) IsInstalling(serverPath string) bool {
	i.mu.Lock()
	defer i.mu.Unlock()
	_, exists := i.Processes[serverPath]
	return exists
}

// Install This will install the base server folder
// If an installation is already in progress this will just add the provided server to the progress to be notified when it is complete
func (i *Installer) Install(s *Server, serverPath string, client remote.Client) error {
	const timeout = 10 * time.Minute

	i.mu.Lock()
	if proc, exists := i.Processes[serverPath]; exists {
		// Already installing, use the existing context
		procCtx := proc.ctx
		i.mu.Unlock()

		select {
		case <-proc.done:
			return nil
		case <-procCtx.Done():
			if errors.Is(procCtx.Err(), context.DeadlineExceeded) {
				return errors.WithDetails(
					errors.Wrap(procCtx.Err(), "installation timed out"),
					"server-path", serverPath,
					"timeout", timeout,
				)
			}
			return errors.Wrap(procCtx.Err(), "install cancelled")
		}
	}

	// No install running, create new process with context
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	proc := &InstallationProcess{
		done:   make(chan struct{}),
		ctx:    ctx,
		cancel: cancel,
	}
	i.Processes[serverPath] = proc
	i.mu.Unlock()

	// Cleanup + notify all waiters
	defer func() {
		i.mu.Lock()
		delete(i.Processes, serverPath)
		close(proc.done)
		cancel()
		i.mu.Unlock()
	}()

	// install the base server
	// get the installation script this server uses
	//info, err := client.GetServerInstallInfo(ctx, s.ID())
	//if err != nil {
	//	return err
	//}

	// run install script

	return nil
}
