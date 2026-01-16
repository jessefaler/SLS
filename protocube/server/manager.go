package server

import (
	"context"
	"fmt"
	"runtime"
	"sync"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gammazero/workerpool"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/enviroment"
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

// ServersByNode returns all servers that belong to the specified node ID.
func (m *Manager) ServersByNode(nodeId string) []*Server {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	var result []*Server
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
	}
}

// CreateServer creates a server on the specified node and adds it to the manager
func (m *Manager) CreateServer(ctx context.Context, node *node.Node, blueprint *blueprint.Blueprint, software *software.Registry, overrides *models.ServerOverrides) (*Server, error) {
	serverId := id.New()

	// Get the server client from the node
	serverClient := node.Server(serverId)

	// Instantiate the server
	server := &Server{
		id:           serverId,
		nodeName:     node.Name(),
		nodeId:       node.Id(),
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
			server.Remove()
			server.Events().Destroy()
			server.DestroyAllSinks()
			err := repository.RemoveServer(server.Id())
			log.WithError(err).Errorf("failed to remove server %s from database.", serverId)
		}
	}()

	// Add the server to the manager
	m.Add(server)

	// Write the server to the database
	if err := repository.StoreServer(&models.ServerStore{
		Id:          serverId,
		NodeName:    node.Name(),
		NodeId:      node.Id(),
		BlueprintId: blueprint.Meta.ID,
		Overrides:   overrides,
	}); err != nil {
		return nil, err
	}

	sw := software.Get(blueprint.Server.Software)

	matcher, err := models.NewOutputLineMatcher(sw.OnlineSignal)
	if err != nil {
		return nil, errors.Wrapf(err, "failed to create output line matcher for the start configuration: %s", sw.OnlineSignal)
	}

	// Convert software and blueprint configuration patches
	// to config file patches
	// if an error occurs the server will still be created but an error
	// will be logged when the server is created
	configFiles, cfgErr := GetConfigFiles(sw, blueprint)

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
		ConfigurationFiles: configFiles,
	}

	// Handle Overrides
	save := blueprint.Save
	limits := blueprint.Server.Limits
	if overrides != nil {
		if overrides.Save != nil {
			save = *overrides.Save
		}
		limits = MergeLimits(limits, overrides.Limits)
	}

	nodeReq := models.NodeCreateServerRequest{
		ID:                   serverId,
		ProcessConfiguration: pc,
		Image:                blueprint.Server.Image,
		Invocation:           sw.Invocation,
		Limits:               limits,
		ServerFolder:         blueprint.Server.Path,
		WorldFolder:          blueprint.World.Path,
		Content:              blueprint.Server.Content,
		Save:                 save,
	}

	// Request server creation on the remote node
	resp, err := node.CreateServer(ctx, nodeReq)
	if err != nil {
		return nil, err
	}

	// Set the servers allocation returned from the node response
	// todo should probably switch to protocube assigning allocations
	server.Allocations = resp.Allocation

	// log any errors that occurred when converting configuration patches
	if cfgErr != nil {
		log.WithError(cfgErr).Warn("an error occurred while converting config patches for server " + server.id)
	}

	// Indicate that the server was successfully created so that it is not removed by the defer function
	created = true
	return server, nil
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
				log.WithField("server", data.Id).WithField("error", err).Error("failed to load server, skipping...")
				return
			}
			m.Add(s)
		})
	}

	// Wait until we've processed all the server configurations in the database
	// before continuing.
	pool.StopWait()

	diff := time.Now().Sub(start)
	log.WithField("duration", fmt.Sprintf("%s", diff)).Info("finished processing server configurations")

	return nil
}

func (m *Manager) InitServer(data *models.ServerStore, n *node.Node) (*Server, error) {
	// Create the server client.
	// The node client is likely nil at startup because servers are loaded
	// during program boot, before any nodes have connected. The node client will
	// be attached later when a node becomes available.
	serverClient := client.NewServerClient(data.Id, data.NodeId)
	if n != nil {
		// Set the node client if the node has already connected
		serverClient.SetNodeClient(n.Client())
		// Update the servers node name in case it changed
		data.NodeName = n.Name()
	}

	// Instantiate the server
	server := &Server{
		id:           data.Id,
		nodeName:     data.NodeName,
		nodeId:       data.NodeId,
		sc:           serverClient,
		GlobalEvents: m.Events,

		Remove: func() {
			m.Remove(data.Id)
		},
	}

	// Add the server to the manager
	m.Add(server)
	return server, nil
}

func MergeLimits(base *enviroment.Limits, override *enviroment.Limits) *enviroment.Limits {
	if override == nil {
		return base
	}

	if override.MemoryLimit != nil {
		base.MemoryLimit = override.MemoryLimit
	}
	if override.Swap != nil {
		base.Swap = override.Swap
	}
	if override.IoWeight != nil {
		base.IoWeight = override.IoWeight
	}
	if override.CpuLimit != nil {
		base.CpuLimit = override.CpuLimit
	}
	if override.DiskSpace != nil {
		base.DiskSpace = override.DiskSpace
	}
	if override.Threads != nil {
		base.Threads = override.Threads
	}
	if override.OOMDisabled != nil {
		base.OOMDisabled = override.OOMDisabled
	}

	return base
}
