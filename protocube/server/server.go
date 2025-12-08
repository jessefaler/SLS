package server

import (
	"context"
	"sync"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/enviroment"
	"protoxon.com/sls/protocube/events"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/system"
)

// Server represents a managed server instance controlled by SLS.
type Server struct {
	id          string
	Limits      enviroment.Limits
	status      Status
	emitterLock sync.Mutex

	// Events emitted by the server instance.
	emitter      *events.Bus
	GlobalEvents func() *events.Bus
	sinks        map[system.SinkName]*system.SinkPool

	// The crash handler for this server instance.
	crasher CrashHandler

	// sc is the dedicated client used to interact with server-specific API endpoints.
	// A server client can only access its own endpoints and control itself.
	sc client.ServerClient

	Allocations enviroment.Allocations
}

func (s *Server) Id() string {
	return s.id
}

// SetStatus updates the server's status and publishes the change.
func (s *Server) SetStatus(status Status) {
	s.status = status
	s.PublishEvent(StatusEvent, status.String())
}

// Client returns the underlying server client used to interact with remote endpoints.
func (s *Server) Client() client.ServerClient {
	return s.sc
}

// Stats fetches resource stats from the servers remote node.
func (s *Server) Stats(ctx context.Context) (enviroment.Stats, error) {
	return s.Client().Stats(ctx)
}

// Power sends a power action request to the remote node.
func (s *Server) Power(ctx context.Context, request models.PowerAction) error {
	return s.Client().Power(ctx, request)
}

// Stop gracefully stops the server by sending a "stop" power action.
func (s *Server) Stop(ctx context.Context) error {
	return s.Power(ctx, models.PowerAction{Action: "stop"})
}

// Kill forcefully terminates the server by sending a "kill" power action.
func (s *Server) Kill(ctx context.Context) error {
	return s.Power(ctx, models.PowerAction{Action: "kill"})
}

// GetStatus returns the server's most recently reported power status.
func (s *Server) GetStatus() Status {
	return s.status
}

// GetRemoteStatus fetches the servers status from the remote node
func (s *Server) GetRemoteStatus(ctx context.Context) (gin.H, error) {
	return s.Client().Status(ctx)
}

// ServerData returns the ServerData model for this server.
func (s *Server) ServerData() models.ServerData {
	return models.ServerData{
		Id:   s.id,
		Ip:   s.Allocations.DefaultMapping.Ip,
		Port: s.Allocations.DefaultMapping.Port,
	}
}

func (s *Server) Log() *log.Entry {
	return log.WithField("server", s.Id())
}
