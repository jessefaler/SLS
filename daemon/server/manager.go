package server

import (
	"fmt"
	"path/filepath"
	"strings"
	"sync"

	"emperror.dev/errors"
	"github.com/apex/log"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/environment/docker"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/remote"
	"protoxon.com/sls/daemon/server/filesystem"
)

type Manager struct {
	mutex   sync.RWMutex
	client  remote.Client
	servers map[string]*Server // key = server ID
}

// NewManager returns a new server manager instance.
func NewManager(client remote.Client) *Manager {
	return &Manager{
		client:  client,
		servers: make(map[string]*Server),
	}
}

// Add a server to the collection
func (manager *Manager) Add(server *Server) {
	manager.mutex.Lock()
	defer manager.mutex.Unlock()
	manager.servers[server.id] = server
}

// Get returns a single server instance and a boolean value indicating if it was
// found in the global collection or not.
func (manager *Manager) Get(id string) (*Server, bool) {
	match := manager.Find(func(server *Server) bool {
		return server.id == id
	})
	return match, match != nil
}

// Find returns a single element from the collection matching the filter. If
// nothing is found, a nil result is returned.
func (manager *Manager) Find(filter func(match *Server) bool) *Server {
	manager.mutex.RLock()
	defer manager.mutex.RUnlock()
	for _, server := range manager.servers {
		if filter(server) {
			return server
		}
	}
	return nil
}

// Remove removes a server from the collection by its ID.
func (manager *Manager) Remove(id string) {
	manager.mutex.Lock()
	defer manager.mutex.Unlock()
	delete(manager.servers, id)
}

// All returns a snapshot of all servers managed by this instance.
func (manager *Manager) All() []*Server {
	manager.mutex.RLock()
	defer manager.mutex.RUnlock()
	servers := make([]*Server, 0, len(manager.servers))
	for _, s := range manager.servers {
		servers = append(servers, s)
	}
	return servers
}

// Create creates a new server
func (manager *Manager) Create(req models.CreateServerRequest) (*Server, error) {
	s, err := New(manager.client)
	if err != nil {
		return nil, err
	}

	s.save = req.Save
	s.Remove = func() {
		manager.Remove(s.id)
	}
	s.Config().Limits = req.Limits
	// Create an allocation for the server
	alloc := environment.NewAllocation()
	s.Config().Allocations = alloc

	s.id = req.ID

	// Replace the server.build.default.port variable with the servers actual port
	// todo add support for other variables in the invocation
	invocation := req.Invocation
	invocation = strings.ReplaceAll(invocation, "{{server.build.default.port}}", fmt.Sprintf("%d", alloc.DefaultMapping.Port))

	s.Config().Invocation = invocation
	s.SetProcessConfiguration(req.ProcessConfiguration)
	s.Config().Container.Image = req.Image

	// Create the servers volume
	serverFolder := filepath.Join(config.Get().Servers.Root, req.ServerFolder)
	worldFolder := filepath.Join(config.Get().Worlds.Root, req.WorldFolder)
	volume, overlay, err := BuildServerVolume(s.id, serverFolder, worldFolder, req.Content)
	if err != nil {
		// Clean up the volume and overlay that were created
		if cleanupErr := CleanupServerVolume(volume, overlay); cleanupErr != nil {
			log.WithError(cleanupErr).Warnf("Failed to cleanup volume and overlay after volume creation failure for server %s", s.id)
		}
		return nil, errors.Wrap(err, "failed to build server volume")
	}

	// Create the servers filesystem abstraction
	// denylist is not used for now is its set to nil
	s.filesystem, err = filesystem.New(volume, overlay, s.DiskSpace(), nil)
	if err != nil {
		// Clean up the volume and overlay that were created
		if cleanupErr := CleanupServerVolume(volume, overlay); cleanupErr != nil {
			log.WithError(cleanupErr).Warnf("Failed to cleanup volume and overlay after filesystem creation failure for server %s", s.id)
		}
		return nil, errors.Wrap(err, "failed to create filesystem")
	}

	// set the servers environment settings
	settings := environment.Settings{
		Mounts:      s.Mounts(),
		Allocations: alloc,
		Limits:      s.cfg.Limits,
		Labels:      s.cfg.Labels,
	}

	envCfg := environment.NewConfiguration(settings, s.GetEnvironmentVariables())
	meta := docker.Metadata{
		Image: s.Config().Container.Image,
	}

	if env, err := docker.New(s.id, &meta, envCfg); err != nil {
		// Clean up the volume and overlay that were created
		if cleanupErr := CleanupServerVolume(volume, overlay); cleanupErr != nil {
			log.WithError(cleanupErr).Warnf("Failed to cleanup volume and overlay for server %s", s.id)
		}
		return nil, err
	} else {
		s.Environment = env
		s.StartEventListeners()
	}

	// Add the server to this manager instance
	manager.Add(s)
	return s, nil
}
