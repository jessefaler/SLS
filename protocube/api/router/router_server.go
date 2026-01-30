package router

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/models"
)

func postServerPower(c *gin.Context) {
	server := middleware.ExtractServer(c)
	powerAction := models.PowerAction{}
	if err := c.BindJSON(&powerAction); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Failed to bind json"})
		return
	}
	// Make the request
	err := server.Power(c.Request.Context(), powerAction)
	// Handle any errors
	if err != nil {
		client.HandleError(c, err)
		return
	}
	// Respond with 200 OK if success
	c.Status(http.StatusOK)
}

func getServer(c *gin.Context) {
	server := middleware.ExtractServer(c)
	c.JSON(http.StatusOK, server.ServerData())
}

func deleteServer(c *gin.Context) {
	server := middleware.ExtractServer(c)
	err := server.Delete(c.Request.Context())
	if err != nil {
		// If force is enabled, ensure the server is cleaned up on Protocube
		// even if deletion from the daemon fails
		if c.Query("force") == "true" {
			server.CleanupForDestroy()
			c.Status(http.StatusOK)
			return
		}
		client.HandleError(c, err)
		return
	}
	c.Status(http.StatusOK)
}

func getServerStatus(c *gin.Context) {
	s := middleware.ExtractServer(c)
	status, err := s.GetRemoteStatus(c.Request.Context())
	if err != nil {
		client.HandleError(c, err)
		return
	}
	c.JSON(http.StatusOK, gin.H{"status": status})
}

func getServerStats(c *gin.Context) {
	s := middleware.ExtractServer(c)
	var update = false
	if c.Query("update_disk_usage") == "true" {
		update = true
	}
	stats, err := s.Stats(c.Request.Context(), update)
	if err != nil {
		client.HandleError(c, err)
		return
	}
	c.JSON(http.StatusOK, stats)
}

func postServerCommands(c *gin.Context) {
	server := middleware.ExtractServer(c)

	var data struct {
		Commands []string `json:"commands"`
	}
	if err := c.BindJSON(&data); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Failed to bind json"})
		return
	}

	// Make the request
	err := server.SendCommands(c.Request.Context(), data.Commands)
	// Handle any errors
	if err != nil {
		client.HandleError(c, err)
		return
	}
	// Respond with 204 No Content if success
	c.Status(http.StatusNoContent)
}

func postServerReset(c *gin.Context) {
	server := middleware.ExtractServer(c)
	// Make the request
	err := server.Reset(c.Request.Context())
	// Handle any errors
	if err != nil {
		client.HandleError(c, err)
		return
	}
	c.Status(http.StatusAccepted)
}

func getServerLogs(c *gin.Context) {
	server := middleware.ExtractServer(c)

	// Parse the size query parameter, defaulting to 100
	size, _ := strconv.Atoi(c.DefaultQuery("size", "100"))
	if size <= 0 {
		size = 100
	} else if size > 100 {
		size = 100
	}

	// Make the request to the daemon
	logs, err := server.GetLogs(c.Request.Context(), size)
	if err != nil {
		client.HandleError(c, err)
		return
	}

	// Return the logs response
	c.JSON(http.StatusOK, logs)
}

func (r *Router) getInstallInfo(c *gin.Context) {
	server := middleware.ExtractServer(c)

	// Get the servers blueprint
	bp := r.BlueprintRegistry.Get(server.BlueprintId())
	if bp == nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Blueprint not found"})
		return
	}
	// Get the software used by the blueprint
	sw := r.SoftwareRegistry.Get(bp.Server.Software)
	if sw == nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "Software not found"})
		return
	}

	// Return the installation script from the software configuration
	c.JSON(http.StatusOK, sw.InstallScript)
}
