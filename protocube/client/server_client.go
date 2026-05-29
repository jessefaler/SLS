package client

import (
	"context"
	"strconv"

	"emperror.dev/errors"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/models"
)

// ServerClient is a dedicated REST client for interacting with the API endpoints of a specific server.
type ServerClient interface {
	SetNodeClient(client NodeClient) ServerClient
	Node() (NodeClient, error)

	// Request types
	Get(ctx context.Context, path string, query q) (*Response, error)
	Post(ctx context.Context, path string, data interface{}) (*Response, error)
	Delete(ctx context.Context, path string) (*Response, error)

	// Requests
	Power(ctx context.Context, action models.PowerAction) error
	Reset(ctx context.Context) error
	Reinstall(ctx context.Context) error
	Logs(ctx context.Context, size int) (gin.H, error)
	InstallInfo(ctx context.Context) (models.InstallInfo, error)
	Stats(ctx context.Context, update bool) (models.ResourceUsage, error)
	Status(ctx context.Context) (string, error)
	GetServer(ctx context.Context) (models.ServerData, error)
	Commands(ctx context.Context, commands []string) error
	DeleteServer(ctx context.Context) error
}

type serverClient struct {
	node     NodeClient
	nodeId   string
	serverId string
}

func NewServerClient(serverId string, nodeId string) ServerClient {
	return &serverClient{
		nodeId:   nodeId,
		serverId: serverId,
	}
}

// SetNodeClient sets the underlying node client to use when making requests
func (sc *serverClient) SetNodeClient(node NodeClient) ServerClient {
	sc.node = node
	return sc
}

// Node returns the node client this server client is bound to.
//
// Callers must use this method instead of accessing sc.node directly, as the
// node client may be nil during startup. In that case,
// Node returns ErrNodeUnavailable rather than panicking.
func (sc *serverClient) Node() (NodeClient, error) {
	if sc.node == nil {
		return nil, errors.Wrapf(ErrNodeUnavailable, "node %s for server %s is not connected", sc.nodeId[:8], sc.serverId)
	}
	return sc.node, nil
}

func (sc *serverClient) Power(ctx context.Context, action models.PowerAction) error {
	_, err := sc.Post(ctx, "/power", action)
	return err
}

func (sc *serverClient) Reset(ctx context.Context) error {
	_, err := sc.Post(ctx, "/reset", nil)
	return err
}

func (sc *serverClient) Reinstall(ctx context.Context) error {
	_, err := sc.Post(ctx, "/reinstall", nil)
	return err
}

// Stats fetches resource stats from the remote node.
func (sc *serverClient) Stats(ctx context.Context, update bool) (models.ResourceUsage, error) {
	if update {
		return Get[models.ResourceUsage](sc, ctx, "/stats", q{"update_disk_usage": "true"})
	}
	return Get[models.ResourceUsage](sc, ctx, "/stats", nil)
}

// Status fetches the servers status from the remote node.
func (sc *serverClient) Status(ctx context.Context) (string, error) {
	resp, err := Get[models.StatusResponse](sc, ctx, "/status", nil)
	if err != nil {
		return "", err
	}
	return resp.Status, nil
}

// GetServer fetches the servers data from the remote node.
func (sc *serverClient) GetServer(ctx context.Context) (models.ServerData, error) {
	return Get[models.ServerData](sc, ctx, "/", nil)
}

// DeleteServer sends a request to the node to permanently delete the server attached to this client
func (sc *serverClient) DeleteServer(ctx context.Context) error {
	_, err := sc.Delete(ctx, "/")
	return err
}

// Commands sends an array of commands to the remote node.
func (sc *serverClient) Commands(ctx context.Context, commands []string) error {
	var data struct {
		Commands []string `json:"commands"`
	}
	data.Commands = commands
	_, err := sc.Post(ctx, "/commands", data)
	return err
}

// Logs fetches server logs from the remote node.
func (sc *serverClient) Logs(ctx context.Context, size int) (gin.H, error) {
	query := q{"size": strconv.Itoa(size)}
	return Get[gin.H](sc, ctx, "/logs", query)
}

func (sc *serverClient) InstallInfo(ctx context.Context) (models.InstallInfo, error) {
	return Get[models.InstallInfo](sc, ctx, "/install", nil)
}
