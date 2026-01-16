package node

import (
	"context"
	"sync"

	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/models"
)

type Node struct {
	id       string
	name     string
	location string
	url      string
	drained  bool
	mutex    sync.RWMutex
	nc       client.NodeClient
	*Health
}

// Returns the underlying node client
func (n *Node) Client() client.NodeClient {
	return n.nc
}

// Drained reports whether the load balancer should skip this node
// for automatic server creation.
func (n *Node) Drained() bool {
	n.mutex.RLock()
	defer n.mutex.RUnlock()
	return n.drained
}

func (n *Node) SetDrained(drained bool) {
	n.mutex.Lock()
	defer n.mutex.Unlock()
	n.drained = drained
}

func (n *Node) Url() string {
	return n.url
}

func (n *Node) Name() string {
	return n.name
}

func (n *Node) Location() string {
	return n.location
}

func (n *Node) Id() string {
	return n.id
}

// CreateServer sends a request to create a new server on the remote node
// and returns its data upon success.
// This only sends the request; it does not configure the server locally.
// Typically, servers should be created through the server manager.
func (n *Node) CreateServer(ctx context.Context, request models.NodeCreateServerRequest) (models.CreateServerResponse, error) {
	return n.nc.CreateServer(ctx, request)
}

func (n *Node) GetSystemInformation(ctx context.Context) (models.Information, error) {
	return n.nc.GetSystemInformation(ctx)
}

// Server returns a server-specific client bound to this node.
func (n *Node) Server(id string) client.ServerClient {
	return n.nc.Server(id)
}
