package router

import (
	"net/http"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/auth"
	"protoxon.com/sls/protocube/server"
	"protoxon.com/sls/protocube/system"
)

func (r *Router) postNodeRegister(c *gin.Context) {
	var req NodeRegistration
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Validate the request fields
	if err := req.Validate(); err != nil {
		c.JSON(http.StatusBadRequest, errors.Wrap(err, "invalid field"))
	}

	// Check if the node is already registered
	node, exists := r.NodeManager.Get(req.Id)
	if exists {
		// Node is already registered return the current token
		c.JSON(http.StatusOK, gin.H{
			"token": node.Client().GetToken(),
		})
		return
	}

	// Generate a token the node will use to authenticate future requests from Protocube
	token, err := auth.GenerateToken()
	if err != nil {
		c.JSON(http.StatusInternalServerError, errors.Wrap(err, "failed to generate auth token"))
		return
	}

	// Connect the node in the node manager
	node = r.NodeManager.Register(req.Id, req.Name, req.Url, req.Location, token.String())
	c.JSON(http.StatusOK, gin.H{
		"token": token.String(),
	})
}

func postNodeHeartbeat(c *gin.Context) {
	node := middleware.ExtractNode(c)
	// Call the nodes heartbeat handler
	node.OnHeartBeat()
	c.Status(http.StatusOK)
}

func (r *Router) postNodeDisconnect(c *gin.Context) {
	node := middleware.ExtractNode(c)
	r.NodeManager.Disconnect(node)
	log.WithField("node", node.Id()).Infof("Node disconnected. Reason: %s", "Node closed the connection.")
	c.Status(http.StatusOK)
}

func (r *Router) postNodeServerStatus(c *gin.Context) {
	// Declare a variable to hold the JSON data
	var status server.Status
	// Bind the JSON to the status variable
	if err := c.ShouldBindJSON(&status); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Process the status change
	s := middleware.ExtractServer(c)
	log.WithFields(log.Fields{
		"server": system.Red(s.Id()),
		"status": system.Red(status),
	}).Debug("Server Status Update.")
	s.SetStatus(status)
	c.Status(http.StatusOK)
}

func (r *Router) postEventServerCrash(c *gin.Context) {
	var crash server.CrashData
	if err := c.ShouldBindJSON(&crash); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	// Process the crash
	s := middleware.ExtractServer(c)
	s.HandleServerCrash(crash)
	c.Status(http.StatusOK)
}

func (r *Router) postEventServerDeleted(c *gin.Context) {
	s := middleware.ExtractServer(c)
	s.CleanupForDestroy()
	c.Status(http.StatusOK)
}
