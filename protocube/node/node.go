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
	mutex    sync.RWMutex
	nc       client.NodeClient
	*Health
}

func (n *Node) Client() client.NodeClient {
	return n.nc
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

// Server returns a server-specific client bound to this node.
func (n *Node) Server(id string) client.ServerClient {
	return n.nc.Server(id)
}
