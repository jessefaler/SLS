package router

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/httperror"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/models"
)

func postServerPower(c *gin.Context) {
	s := middleware.ExtractServer(c)
	powerAction := models.PowerAction{}
	if err := c.BindJSON(&powerAction); err != nil {
		httperror.JSON(c, http.StatusBadRequest, err.Error(), "Failed to bind json")
		return
	}
	// Make the request
	err := s.Power(c.Request.Context(), powerAction)
	// Handle any errors
	if err != nil {
		client.HandleError(c, err)
		return
	}
	// Respond with 200 OK if success
	c.Status(http.StatusOK)
}

func getServer(c *gin.Context) {
	s := middleware.ExtractServer(c)
	c.JSON(http.StatusOK, s.ServerData())
}

func deleteServer(c *gin.Context) {
	s := middleware.ExtractServer(c)
	err := s.Delete(c.Request.Context())
	if err != nil {
		// If force is enabled, ensure the server is cleaned up on Protocube
		// even if deletion from the daemon fails
		if c.Query("force") == "true" {
			s.CleanupForDestroy()
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
	s := middleware.ExtractServer(c)

	var data struct {
		Commands []string `json:"commands"`
	}
	if err := c.BindJSON(&data); err != nil {
		httperror.JSON(c, http.StatusBadRequest, err.Error(), "Failed to bind json")
		return
	}

	// Make the request
	err := s.SendCommands(c.Request.Context(), data.Commands)
	// Handle any errors
	if err != nil {
		client.HandleError(c, err)
		return
	}
	// Respond with 204 No Content if success
	c.Status(http.StatusNoContent)
}

func postServerReset(c *gin.Context) {
	s := middleware.ExtractServer(c)
	// Reset overlay filesystem
	err := s.Reset(c.Request.Context())
	if err != nil {
		client.HandleError(c, err)
		return
	}
	c.Status(http.StatusAccepted)
}

func getServerLogs(c *gin.Context) {
	s := middleware.ExtractServer(c)

	// Parse the size query parameter
	size, _ := strconv.Atoi(c.DefaultQuery("size", "100"))
	if size <= 0 {
		size = 100
	} else if size > 100 {
		size = 100
	}

	// Make the request to the daemon
	logs, err := s.GetLogs(c.Request.Context(), size)
	if err != nil {
		client.HandleError(c, err)
		return
	}

	// Return the logs in the response
	c.JSON(http.StatusOK, logs)
}

func getServerInstallInfo(c *gin.Context) {
	s := middleware.ExtractServer(c)
	info, err := s.InstallInfo(c.Request.Context())
	if err != nil {
		client.HandleError(c, err)
		return
	}
	c.JSON(http.StatusOK, info)
}

func postServerReinstall(c *gin.Context) {
	s := middleware.ExtractServer(c)
	if err := s.Reinstall(c.Request.Context()); err != nil {
		client.HandleError(c, err)
		return
	}
	c.Status(http.StatusAccepted)
}

func (r *Router) getInstallInfo(c *gin.Context) {
	s := middleware.ExtractServer(c)
	c.JSON(http.StatusOK, s.InstallScript)
}
