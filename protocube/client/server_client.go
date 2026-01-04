package client

import (
	"context"
	"strconv"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/models"
)

type ServerClient interface {
	Power(ctx context.Context, action models.PowerAction) error
	Logs(ctx context.Context, size int) (gin.H, error)
	Stats(ctx context.Context, update bool) (models.ResourceUsage, error)
	Status(ctx context.Context) (gin.H, error)
	GetServer(ctx context.Context) (models.ServerData, error)
	Commands(ctx context.Context, commands []string) error
	DeleteServer(ctx context.Context) error
}

type serverClient struct {
	node *nodeClient
	id   string // Servers id
}

func (sc *serverClient) Power(ctx context.Context, action models.PowerAction) error {
	_, err := sc.Post(ctx, "/power", action)
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
func (sc *serverClient) Status(ctx context.Context) (gin.H, error) {
	return Get[gin.H](sc, ctx, "/status", nil)
}

// GetServer fetches the servers data from the remote node.
func (sc *serverClient) GetServer(ctx context.Context) (models.ServerData, error) {
	return Get[models.ServerData](sc, ctx, "/", nil)
}

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
	query := q{
		"size": strconv.Itoa(size),
	}
	return Get[gin.H](sc, ctx, "/logs", query)
}
