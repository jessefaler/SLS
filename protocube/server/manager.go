package server

import (
	"context"
	"runtime"
	"sync"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gammazero/workerpool"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/events"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/server/repository"
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
func NewManager(nm *node.Manager) (*Manager, error) {
	m := &Manager{
		servers: make(map[string]*Server),
		emitter: events.NewBus(),
	}
	if err := m.init(nm); err != nil {
		return nil, err
	}
	return m, nil
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
	servers := make([]*Server, len(m.servers))
	i := 0
	for _, s := range m.servers {
		servers[i] = s
		i++
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

// ServersByNode returns all servers that belong to the specified node ID.
func (m *Manager) ServersByNode(nodeId string) []*Server {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	result := make([]*Server, 0)
	for _, server := range m.servers {
		if server.nodeId == nodeId {
			result = append(result, server)
		}
	}
	return result
}

// AttachNodeClientToServers attaches a node client to all servers that belong to the specified node.
// This is called when a node connects to ensure all servers for that node have their
// node client properly set.
func (m *Manager) AttachNodeClientToServers(nodeId string, node *node.Node) {
	servers := m.ServersByNode(nodeId)
	for _, server := range servers {
		server.sc.SetNodeClient(node.Client())
		// Update the servers node name in case it changed
		server.nodeName = node.Name()
		if a := server.allocations(); a != nil {
			// Also claim the servers allocation in the allocator
			node.Allocator.Claim(a.DefaultMapping.Ip, a.DefaultMapping.Port)
			// Set the release function in the allocation
			a.Release = func() {
				node.Allocator.Release(a.DefaultMapping.Ip, a.DefaultMapping.Port)
			}
		}
	}
}

// DetachNodeClientFromServers clears the node client on all servers that belong to the
// specified node. This is called when a node disconnects so server operations fail
// fast with ErrNodeUnavailable instead of retrying against a dead endpoint.
func (m *Manager) DetachNodeClientFromServers(nodeId string) {
	servers := m.ServersByNode(nodeId)
	for _, server := range servers {
		server.sc.SetNodeClient(nil)
		if a := server.allocations(); a != nil {
			a.Release = nil
		}
	}
}

// CreateServer creates a server on the specified node and adds it to the manager
func (m *Manager) CreateServer(ctx context.Context, node *node.Node, bp *blueprint.Blueprint, swr *software.Registry, overrides *models.ServerOverrides) (*Server, error) {
	serverId := id.New()

	// Get the server client from the node
	serverClient := node.Server(serverId)

	// Create an allocation for the server
	alloc, err := node.Allocator.NewAllocation()
	if err != nil {
		return nil, errors.Wrap(err, "failed to create allocation")
	}

	// Instantiate the server
	s := &Server{
		id:           serverId,
		nodeName:     node.Name(),
		nodeId:       node.Id(),
		blueprintId:  bp.Meta.ID,
		Overrides:    overrides,
		sc:           serverClient,
		GlobalEvents: m.Events,
		Remove: func() {
			m.Remove(serverId)
		},
	}

	// This will remove the server in the event any of the following steps fail
	created := false
	defer func() {
		if !created {
			s.Remove()
			s.Events().Destroy()
			s.DestroyAllSinks()
			if err := repository.RemoveServer(s.Id()); err != nil {
				log.WithError(err).Errorf("failed to remove server %s from database.", serverId)
			}
		}
	}()

	cfg, installScript, err := BuildServerConfiguration(s, bp, swr, alloc)
	if err != nil {
		return nil, err
	}
	s.Configuration = cfg
	s.InstallScript = installScript

	// Add the server to the manager
	m.Add(s)

	// Write the server to the database
	if err := repository.StoreServer(&models.ServerRecord{
		Id:            serverId,
		NodeName:      node.Name(),
		NodeId:        node.Id(),
		BlueprintId:   bp.Meta.ID,
		Overrides:     overrides,
		Configuration: cfg,
		InstallScript: installScript,
	}); err != nil {
		return nil, err
	}

	// Request server creation on the remote node
	_, err = node.CreateServer(ctx, *cfg)
	if err != nil {
		return nil, err
	}

	// Indicate that the server was successfully created so that it is not removed by the defer function
	created = true
	return s, nil
}

// Loads in all servers stored in the database
func (m *Manager) init(nm *node.Manager) error {
	servers, err := repository.GetAllServers()
	if err != nil {
		return errors.WrapIf(err, "failed to load servers from database")
	}

	start := time.Now()
	log.WithField("total_configs", len(servers)).Info("processing servers configurations from the database")

	pool := workerpool.New(runtime.NumCPU())
	log.Debugf("using %d workerpools to instantiate server instances", runtime.NumCPU())
	for _, data := range servers {
		data := data
		n, _ := nm.Get(data.NodeId)
		pool.Submit(func() {
			s, err := m.InitServer(data, n)
			if err != nil {
				log.WithField("server", data.Id).WithField("node_id", data.NodeId).WithField("error", err).Error("failed to load server, skipping...")
				return
			}
			m.Add(s)
		})
	}

	// Wait until we've processed all the server configurations in the database
	// before continuing.
	pool.StopWait()

	diff := time.Since(start)
	log.WithField("duration", diff.String()).Info("finished processing server configurations")

	return nil
}

func (m *Manager) InitServer(s *models.ServerRecord, n *node.Node) (*Server, error) {
	if err := s.Validate(); err != nil {
		return nil, errors.Wrap(err, "invalid server record")
	}

	// Create the server client.
	// The node client is likely nil at startup because servers are loaded
	// during program boot, before any nodes have connected. The node client will
	// be attached later when a node becomes available.
	serverClient := client.NewServerClient(s.Id, s.NodeId)
	if n != nil {
		// Set the node client if the node has already connected
		serverClient.SetNodeClient(n.Client())
		// Update the servers node name in case it changed
		s.NodeName = n.Name()
		a := &s.Configuration.Allocations
		a.Release = func() {
			n.Allocator.Release(a.DefaultMapping.Ip, a.DefaultMapping.Port)
		}
	}

	// Instantiate the server
	server := &Server{
		id:            s.Id,
		nodeName:      s.NodeName,
		nodeId:        s.NodeId,
		blueprintId:   s.BlueprintId,
		Overrides:     s.Overrides,
		Configuration: s.Configuration,
		InstallScript: s.InstallScript,
		sc:            serverClient,
		GlobalEvents:  m.Events,
		Remove: func() {
			m.Remove(s.Id)
		},
	}

	if n != nil {
		if a := server.allocations(); a != nil {
			// claim the servers allocation
			n.Allocator.Claim(a.DefaultMapping.Ip, a.DefaultMapping.Port)
		}
	}

	return server, nil
}
