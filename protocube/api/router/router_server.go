package router

import (
	"net/http"

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

func getServerStatus(c *gin.Context) {
	s := middleware.ExtractServer(c)
	status, err := s.GetRemoteStatus(c.Request.Context())
	if err != nil {
		client.HandleError(c, err)
		return
	}
	c.JSON(http.StatusOK, status)
}
