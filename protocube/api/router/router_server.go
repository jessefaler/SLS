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

	page, perPage := logPaginationParams(c)

	logs, err := s.GetLogs(c.Request.Context(), page, perPage)
	if err != nil {
		client.HandleError(c, err)
		return
	}

	c.JSON(http.StatusOK, logs)
}

func logPaginationParams(c *gin.Context) (page, perPage int) {
	page, _ = strconv.Atoi(c.DefaultQuery("page", "1"))
	if page < 1 {
		page = 1
	}

	perPageRaw := c.Query("per_page")
	if perPageRaw == "" {
		perPageRaw = c.DefaultQuery("size", c.DefaultQuery("lines", "100"))
	}
	perPage, _ = strconv.Atoi(perPageRaw)
	if perPage <= 0 {
		perPage = 100
	}
	if perPage > 100 {
		perPage = 100
	}
	return page, perPage
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

func getServerInstallLogs(c *gin.Context) {
	s := middleware.ExtractServer(c)

	page, perPage := logPaginationParams(c)

	logs, err := s.InstallLogs(c.Request.Context(), page, perPage)
	if err != nil {
		client.HandleError(c, err)
		return
	}

	c.JSON(http.StatusOK, logs)
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
