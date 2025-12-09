package client

import (
	"context"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/enviroment"
	"protoxon.com/sls/protocube/models"
)

type ServerClient interface {
	Power(ctx context.Context, action models.PowerAction) error
	//Logs(ctx context.Context) ([]LogEntry, error)
	Stats(ctx context.Context) (enviroment.Stats, error)
	Status(ctx context.Context) (gin.H, error)
	GetServer(ctx context.Context) (models.ServerData, error)
	Commands(ctx context.Context, commands []string) error
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
func (sc *serverClient) Stats(ctx context.Context) (enviroment.Stats, error) {
	return Get[enviroment.Stats](sc, ctx, "/stats", nil)
}

// Status fetches the servers status from the remote node.
func (sc *serverClient) Status(ctx context.Context) (gin.H, error) {
	return Get[gin.H](sc, ctx, "/status", nil)
}

// GetServer fetches the servers data from the remote node.
func (sc *serverClient) GetServer(ctx context.Context) (models.ServerData, error) {
	return Get[models.ServerData](sc, ctx, "/", nil)
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
