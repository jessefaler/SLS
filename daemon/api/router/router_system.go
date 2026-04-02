package router

import (
	"context"
	"net/http"
	"os"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/daemon/api/router/httperror"
	"protoxon.com/sls/daemon/api/router/middleware"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/server"
	"protoxon.com/sls/daemon/system"
)

func (r *Router) postCreateServer(c *gin.Context) {
	// Parse incoming JSON body
	var req models.ServerConfigurationResponse
	if err := c.ShouldBindJSON(&req); err != nil {
		httperror.JSON(c, http.StatusInternalServerError, err.Error(), "Invalid request body.")
		return
	}

	// create the server
	s, err := r.ServerManager.InitServer(req)
	if err != nil {
		log.WithError(err).WithField("server_id", req.Id).Error("failed to create server")
		if errors.Is(err, os.ErrNotExist) {
			// A not exists error usually means the blueprints server or world paths don't exist
			httperror.JSON(c, http.StatusConflict, err.Error(), "The specified path to the server or world directory does not exist on this daemon instance.")
			return
		}
		if errors.Is(err, server.ErrInvalidServerConfig) {
			httperror.JSON(c, http.StatusBadRequest, err.Error(), "Invalid server configuration.")
			return
		}
		httperror.JSON(c, http.StatusInternalServerError, err.Error(), "")
		return
	}

	c.Status(http.StatusOK)

	// Start the server
	// Pass the actual heavy processing off to a separate thread to handle so that
	// we can immediately return a response from the server. Some of these actions
	// can take quite some time, especially stopping or restarting.
	go func(s *server.Server) {
		action := server.PowerActionStart
		if err := s.HandlePowerAction(server.PowerAction(action), 0); err != nil {
			if errors.Is(err, context.DeadlineExceeded) {
				s.Log().WithField("action", action).WithField("error", err).Warn("could not process server power action")
			} else if errors.Is(err, server.ErrIsRunning) {
				// Do nothing, this isn't something we care about for logging,
			} else {
				s.Log().WithFields(log.Fields{"action": action, "wait_seconds": 0, "error": err}).
					Error("encountered error processing a server power action in the background")
			}
		}
	}(s)
}

// Returns all the servers that are registered and configured correctly on
// this daemon instance.
func (r *Router) getAllServers(c *gin.Context) {
	servers := r.ServerManager.All()
	out := make([]server.APIResponse, len(servers), len(servers))
	for i, v := range servers {
		out[i] = v.ToAPIResponse()
	}
	c.JSON(http.StatusOK, out)
}

// Returns information about the system that wings is running on.
func getSystemInformation(c *gin.Context) {
	i, err := system.GetSystemInformation()
	if err != nil {
		middleware.CaptureAndAbort(c, err)
		return
	}
	c.JSON(http.StatusOK, i)
}

// Syncs the daemons servers with Protocube
func (r *Router) postSync(c *gin.Context) {
	err := r.ServerManager.Sync(c.Request.Context())
	if err != nil {
		log.WithError(err).Error("failed to sync server configurations")
		httperror.JSON(c, http.StatusInternalServerError, err.Error(), "failed to sync server configurations")
		return
	}
	c.Status(http.StatusOK)
}
