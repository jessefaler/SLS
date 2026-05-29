package server

import (
	"context"
	"sync"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/environment"
	"protoxon.com/sls/protocube/events"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/server/repository"
	"protoxon.com/sls/protocube/software"
	"protoxon.com/sls/protocube/system"
)

// Server represents a managed server instance controlled by SLS.
type Server struct {
	id          string
	nodeName    string
	nodeId      string
	blueprintId string
	Overrides   *models.ServerOverrides
	status      Status
	emitterLock sync.Mutex

	// Events emitted by the server instance.
	emitter      *events.Bus
	GlobalEvents func() *events.Bus
	sinks        map[system.SinkName]*system.SinkPool

	// Removes the server from the manager
	Remove func()

	// The crash handler for this server instance.
	crasher CrashHandler

	// Configuration is the servers runtime configuration information
	Configuration *models.ServerConfiguration

	// InstallScript is copied from the selected software at creation time.
	InstallScript *software.InstallationScript

	// sc is the dedicated client used to interact with server-specific API endpoints.
	// A server client can only access its own endpoints and control itself.
	sc client.ServerClient
}

// GetServerConfiguration returns the runtime configuration info for the server.
func (s *Server) GetServerConfiguration() *models.ServerConfiguration {
	cfg := *s.Configuration
	cfg.Id = s.Id()
	return &cfg
}

func (s *Server) allocations() *environment.Allocations {
	return &s.Configuration.Allocations
}

func (s *Server) Id() string {
	return s.id
}

func (s *Server) NodeName() string {
	return s.nodeName
}
func (s *Server) NodeId() string {
	return s.nodeId
}
func (s *Server) BlueprintId() string {
	return s.blueprintId
}

// SetStatus updates the server's status and publishes the change.
func (s *Server) SetStatus(status Status) {
	s.status = status
	s.PublishEvent(StatusEvent, status)
}

// Client returns the underlying server client used to interact with remote endpoints.
func (s *Server) Client() client.ServerClient {
	return s.sc
}

// Stats fetches resource stats from the servers remote node.
func (s *Server) Stats(ctx context.Context, update bool) (models.ResourceUsage, error) {
	return s.Client().Stats(ctx, update)
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

// Pause pauses the server container (Docker pause). Server must be running.
func (s *Server) Pause(ctx context.Context) error {
	return s.Power(ctx, models.PowerAction{Action: "pause"})
}

// Unpause resumes a paused server container.
func (s *Server) Unpause(ctx context.Context) error {
	return s.Power(ctx, models.PowerAction{Action: "unpause"})
}

// Reset sends a request to reset the server on the node
// This will delete the servers overlay and restart the server
// If it is running
func (s *Server) Reset(ctx context.Context) error {
	return s.Client().Reset(ctx)
}

func (s *Server) Reinstall(ctx context.Context) error {
	return s.Client().Reinstall(ctx)
}

// Delete deletes the server from the daemon
func (s *Server) Delete(ctx context.Context) error {
	return s.Client().DeleteServer(ctx)
}

// GetStatus returns the server's most recently reported power status.
func (s *Server) GetStatus() Status {
	return s.status
}

// GetRemoteStatus fetches the servers status from the remote node
func (s *Server) GetRemoteStatus(ctx context.Context) (Status, error) {
	return s.Client().Status(ctx)
}

// SendCommands sends an array of commands to the remote node.
func (s *Server) SendCommands(ctx context.Context, commands []string) error {
	return s.Client().Commands(ctx, commands)
}

// GetLogs fetches server logs from the remote node.
func (s *Server) GetLogs(ctx context.Context, size int, logType string) (gin.H, error) {
	return s.Client().Logs(ctx, size, logType)
}

func (s *Server) InstallInfo(ctx context.Context, size int) (models.InstallInfo, error) {
	return s.Client().InstallInfo(ctx, size)
}

// ServerData returns the ServerData model for this server.
// ServerData is the public API representation of a managed server.
func (s *Server) ServerData() models.ServerData {
	data := models.ServerData{
		Id:              s.id,
		BlueprintId:     s.BlueprintId(),
		NodeId:          s.NodeId(),
		NodeName:        s.NodeName(),
		Overrides:       s.Overrides,
		SoftwareId:      s.Configuration.SoftwareId,
		SoftwareVersion: s.Configuration.SoftwareVersion,
		Image:           s.Configuration.Image,
		Limits:          s.Configuration.Limits,
		Allocations:     s.allocations(),
	}
	return data
}

func (s *Server) Log() *log.Entry {
	return log.WithField("server", s.Id())
}

func (s *Server) CleanupForDestroy() {
	// Notify event listeners
	s.PublishEvent(DeletedEvent, nil)
	s.Events().Destroy()
	s.DestroyAllSinks()
	// Release allocations if they exist
	if a := s.allocations(); a != nil && a.Release != nil {
		a.Release()
	}
	// Remove the server from the manager
	s.Remove()
	// Remove the server from the database
	err := repository.RemoveServer(s.Id())
	if err != nil {
		log.WithError(err).Errorf("failed to remove server %s from database.", s.Id())
	}
}
