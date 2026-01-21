package node

import (
	"context"
	"sync"

	"emperror.dev/errors"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/internal/database"
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

// Sets the drained state of the node
func (n *Node) SetDrained(drained bool) error {
	// store the drained state to the database
	if err := n.storeDrainedState(drained); err != nil {
		return err
	}
	// Update the in memory state
	// If the database succeeded
	n.mutex.Lock()
	n.drained = drained
	n.mutex.Unlock()
	return nil
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
func (n *Node) CreateServer(ctx context.Context, request models.ServerConfigurationResponse) (models.CreateServerResponse, error) {
	return n.nc.CreateServer(ctx, request)
}

func (n *Node) GetSystemInformation(ctx context.Context) (models.Information, error) {
	return n.nc.GetSystemInformation(ctx)
}

// Server returns a server-specific client bound to this node.
func (n *Node) Server(id string) client.ServerClient {
	return n.nc.Server(id)
}

// Persists the node's drained state to the database.
// When drained is true, a record is upserted.
// When drained is false, any existing record is removed.
func (n *Node) storeDrainedState(drained bool) error {
	db := database.Instance()

	if drained {
		// Insert if not exists, update if it does
		return db.
			Clauses(clause.OnConflict{
				Columns: []clause.Column{{Name: "id"}},
				DoUpdates: clause.Assignments(map[string]interface{}{
					"drained": true,
				}),
			}).
			Create(&models.NodeState{
				Id:      n.id,
				Drained: true,
			}).Error
	}

	// Undrained remove row entirely
	return db.Delete(&models.NodeState{}, "id = ?", n.id).Error
}

// Loads the persisted drained state for this node, if present.
// Absence of a record implies the node is not drained.
// Called when a node connects to restore drained state across restarts.
func (n *Node) loadDrainedState() error {
	var state models.NodeState
	err := database.Instance().First(&state, "id = ?", n.id).Error
	if err == nil {
		n.drained = state.Drained
		return nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		n.drained = false
		return nil
	}
	return err
}
