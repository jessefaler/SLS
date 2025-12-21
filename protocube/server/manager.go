package server

import (
	"context"
	"sync"

	"emperror.dev/errors"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/events"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/software"
	"protoxon.com/sls/protocube/system"
	"protoxon.com/sls/protocube/system/id"
)

type Manager struct {
	mutex   sync.RWMutex
	servers map[string]*Server // key = server ID

	// Global event emitter that all servers will emit events to
	emitter     *events.Bus
	emitterLock sync.Mutex
	sinks       map[system.SinkName]*system.SinkPool
}

// NewManager returns a new server manager instance.
func NewManager() *Manager {
	return &Manager{
		servers: make(map[string]*Server),
		emitter: events.NewBus(),
	}
}

// Add a server to the collection
func (m *Manager) Add(server *Server) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	m.servers[server.id] = server
}

// All returns a snapshot of all servers
func (m *Manager) All() []*Server {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	servers := make([]*Server, 0, len(m.servers))
	for _, s := range m.servers {
		servers = append(servers, s)
	}
	return servers
}

// Remove removes a server from the collection by its ID.
func (m *Manager) Remove(id string) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	delete(m.servers, id)
}

// Find returns a single element from the collection matching the filter. If
// nothing is found, a nil result is returned.
func (m *Manager) Find(filter func(match *Server) bool) *Server {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	for _, server := range m.servers {
		if filter(server) {
			return server
		}
	}
	return nil
}

// GetServer returns the Server instance with the given id.
func (m *Manager) GetServer(id string) *Server {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	return m.servers[id]
}

// CreateServer creates a server on the specified node and adds it to the manager
func (m *Manager) CreateServer(ctx context.Context, node *node.Node, blueprint *blueprint.Blueprint, software *software.Registry) (*Server, error) {

	sw := software.Get(blueprint.Server.Software)

	matcher, err := models.NewOutputLineMatcher(sw.OnlineSignal)
	if err != nil {
		return nil, errors.Wrap(err, "failed to create output line matcher for the start configuration: "+sw.OnlineSignal)
	}

	pc := &models.ProcessConfiguration{
		Startup: struct {
			Done      []*models.OutputLineMatcher `json:"done"`
			StripAnsi bool                        `json:"strip_ansi"`
		}{
			Done:      []*models.OutputLineMatcher{matcher},
			StripAnsi: false,
		},
		Stop: models.ProcessStopConfiguration{
			Type:  "command",
			Value: "stop",
		},
		ConfigurationFiles: nil,
	}

	nodeReq := models.NodeCreateServerRequest{
		ID:                   id.New(),
		ProcessConfiguration: pc,
		Image:                blueprint.Server.Image,
		Invocation:           sw.Invocation,
		Limits:               blueprint.Server.Limits,
		ServerFolder:         blueprint.Server.Path,
		WorldFolder:          blueprint.World.Path,
		Content:              blueprint.Server.Content,
		Save:                 blueprint.Save,
	}

	// Request server creation on the remote node
	resp, err := node.CreateServer(ctx, nodeReq)
	if err != nil {
		return nil, err
	}

	// Get the server client from the node
	serverClient := node.Server(nodeReq.ID)

	// Instantiate the server
	server := &Server{
		id:           nodeReq.ID,
		sc:           serverClient,
		GlobalEvents: m.Events,
		Allocations:  resp.Allocation,
		Remove: func() {
			m.Remove(nodeReq.ID)
		},
	}

	// Add the server to the manager
	m.Add(server)

	// Write the server to the database

	return server, nil
}
