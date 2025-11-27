package router

import (
	"net/http"
	"os"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/server/installer"
)

func (r *Router) postCreateServer(c *gin.Context) {
	log.Info("postCreateServer")

	// Parse incoming JSON body
	var req models.CreateServerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// todo verify that the incoming request fields are correct

	// Check if the base server folder has been installed
	if !installer.IsInstalled(req.ServerFolder) {
		c.Status(http.StatusAccepted)
		// If the base server folder doesn't exist create it
		go func() {
			installer.Install()
		}()
	}

	// Create the server
	server, err := r.ServerManager.Create(req)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			// A not exists error usually means the blueprints server or world paths don't exist
			c.JSON(http.StatusConflict, gin.H{
				"error": "The specified path to the server or world directory does not exist on this daemon instance.",
			})
			log.WithError(err).Errorf("Failed to create server %s", req.ID)
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// At this point respond with a status accepted
	c.Status(http.StatusAccepted)

	// Start the server
	go func() {
		if err := server.Environment.Start(server.Context()); err != nil {
			log.WithError(err).Error("failed to start server container")
		}
	}()
}
