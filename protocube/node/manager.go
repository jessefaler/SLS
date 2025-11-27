package node

import (
	"sync"
	"time"

	"github.com/apex/log"
	"protoxon.com/sls/protocube/balancer"
	"protoxon.com/sls/protocube/client"
)

type Manager struct {
	mutex  sync.RWMutex
	lb     balancer.Balancer
	client *client.Client
	nodes  map[string]*Node
}

// NewManager returns a new server manager instance.
func NewManager(client *client.Client, lb balancer.Balancer) *Manager {
	m := &Manager{
		lb:     lb,
		client: client,
		nodes:  make(map[string]*Node),
	}
	m.StartHealthMonitor()
	return m
}

// Add a server to the collection
func (m *Manager) add(node *Node) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	m.nodes[node.Id()] = node
}

// Remove Add a server to the collection
func (m *Manager) remove(id string) {
	m.mutex.Lock()
	defer m.mutex.Unlock()
	delete(m.nodes, id)
}

// Register creates a new Node, initializes its NodeClient, adds it to the Manager's
// internal collection, and registers it with the Manager's load balancer.
// Returns the newly created Node instance.
func (m *Manager) Register(id string, name string, url string, location string, token string) *Node {
	nc := m.client.Node(url, token)
	n := &Node{
		id:       id,
		name:     name,
		location: location,
		url:      url,
		nc:       nc,
		Health: &Health{
			LastSeen: time.Now(),
			Online:   true,
		},
	}
	m.lb.AddNode(n)
	m.add(n)
	log.WithFields(log.Fields{
		"node_id":  n.id,
		"name":     n.name,
		"location": n.location,
	}).Info("Node connected")
	return n
}

func (m *Manager) Disconnect(node *Node) {
	node.Online = false
	m.lb.RemoveNode(node)
	m.remove(node.Id())
}

// Get returns a single node instance and a boolean value indicating if it was
// found in the global collection or not.
func (m *Manager) Get(id string) (*Node, bool) {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	n, exists := m.nodes[id]
	return n, exists
}

// IsRegistered returns true if a node with the given ID exists in the manager.
func (m *Manager) IsRegistered(id string) bool {
	_, exists := m.Get(id)
	return exists
}

// Find returns a single element from the collection matching the filter. If
// nothing is found, a nil result is returned.
func (m *Manager) Find(filter func(match *Node) bool) *Node {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	for _, node := range m.nodes {
		if filter(node) {
			return node
		}
	}
	return nil
}

// GetNodes Returns all nodes
func (m *Manager) GetNodes() []*Node {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	nodes := make([]*Node, 0, len(m.nodes))
	for _, n := range m.nodes {
		nodes = append(nodes, n)
	}
	return nodes
}
