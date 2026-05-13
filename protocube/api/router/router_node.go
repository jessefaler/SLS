package router

import (
	"net/http"
	"strconv"
	"sync"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/httperror"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/node/allocator"
	"protoxon.com/sls/protocube/server"
	"protoxon.com/sls/protocube/system"
)

var registerMutex sync.Mutex

func (r *Router) postNodeRegister(c *gin.Context) {
	var req models.NodeRegistration
	if err := c.ShouldBindJSON(&req); err != nil {
		httperror.JSON(c, http.StatusBadRequest, err.Error(), "Invalid request body.")
		return
	}

	// Validate the request fields
	if err := req.Validate(); err != nil {
		httperror.JSON(c, http.StatusBadRequest, errors.Wrap(err, "invalid field").Error(), "Invalid field in request.")
		return
	}

	registerMutex.Lock()
	defer registerMutex.Unlock()

	// Check if the node is already registered
	node, exists := r.NodeManager.Get(req.Id)
	if exists {
		// Update the allocator for the existing node (in case it was nil or changed)
		var err error
		node.Allocator, err = allocator.NewAllocator(req.Allocations)
		if err != nil {
			log.WithError(err).Errorf("failed to update allocator for existing node %s", node.Id())
			wrapped := errors.Wrap(err, "failed to update allocator")
			httperror.JSON(c, http.StatusInternalServerError, wrapped.Error(), "")
			return
		}
		// Invoke the callback to attach node clients to existing servers
		// This ensures existing allocations are claimed in the new allocator
		r.NodeManager.TriggerNodeRegistered(req.Id, node)
		// Node is already registered return the current token
		c.JSON(http.StatusOK, gin.H{
			"token": node.Client().GetToken(),
		})
		return
	}

	// Generate a key the node will use to authenticate future requests from Protocube
	token, err := system.GenerateKey()
	if err != nil {
		wrapped := errors.Wrap(err, "failed to generate auth token")
		httperror.JSON(c, http.StatusInternalServerError, wrapped.Error(), "")
		return
	}

	// Create the allocator before registering the node (so it's available when the callback is invoked)
	alloc, err := allocator.NewAllocator(req.Allocations)
	if err != nil {
		log.WithError(err).Errorf("failed to create allocator for node %s", req.Id)
		wrapped := errors.Wrap(err, "failed to create allocator")
		httperror.JSON(c, http.StatusInternalServerError, wrapped.Error(), "")
		return
	}

	// Connect the node in the node manager
	r.NodeManager.Register(c.Request.Context(), req.Id, req.Name, req.Url, req.Location, token, alloc)

	c.JSON(http.StatusOK, gin.H{
		"token": token,
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

func postNodeServerStatus(c *gin.Context) {
	// Declare a variable to hold the JSON data
	var status server.Status
	// Bind the JSON to the status variable
	if err := c.ShouldBindJSON(&status); err != nil {
		httperror.JSON(c, http.StatusBadRequest, err.Error(), "Invalid request body.")
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

// installStatusBody is the JSON body for daemon install completion notifications.
type installStatusBody struct {
	Successful bool `json:"successful"`
	Reinstall  bool `json:"reinstall"`
}

func postNodeServerInstallStatus(c *gin.Context) {
	var body installStatusBody
	if err := c.ShouldBindJSON(&body); err != nil {
		httperror.JSON(c, http.StatusBadRequest, err.Error(), "Invalid request body.")
		return
	}
	s := middleware.ExtractServer(c)
	log.WithFields(log.Fields{
		"server":     system.Red(s.Id()),
		"successful": body.Successful,
		"reinstall":  body.Reinstall,
	}).Debug("Server install status update.")
	// After install the server process is offline.
	s.SetStatus(server.ProcessOfflineState)
	c.Status(http.StatusOK)
}

func postEventServerCrash(c *gin.Context) {
	var crash server.CrashData
	if err := c.ShouldBindJSON(&crash); err != nil {
		httperror.JSON(c, http.StatusBadRequest, err.Error(), "Invalid request body.")
		return
	}

	// Process the crash
	s := middleware.ExtractServer(c)
	s.HandleServerCrash(crash)
	c.Status(http.StatusOK)
}

func postEventServerDeleted(c *gin.Context) {
	s := middleware.ExtractServer(c)
	s.CleanupForDestroy()
	c.Status(http.StatusOK)
}

func getNode(c *gin.Context) {
	node := middleware.ExtractNode(c)
	c.JSON(http.StatusOK, models.NodeData{
		ID:       node.Id(),
		Name:     node.Name(),
		Location: node.Location(),
		URL:      node.Url(),
		Drained:  node.Drained(),
	})
}

func getNodeSystemInfo(c *gin.Context) {
	node := middleware.ExtractNode(c)
	info, err := node.GetSystemInformation(c.Request.Context())
	if err != nil {
		client.HandleError(c, err)
		return
	}
	c.JSON(http.StatusOK, info)
}

func toggleNodeDrained(c *gin.Context) {
	node := middleware.ExtractNode(c)

	var req struct {
		Drained bool `json:"drained"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		httperror.JSON(c, http.StatusBadRequest, err.Error(), "Invalid request body.")
		return
	}

	err := node.SetDrained(req.Drained)
	if err != nil {
		httperror.JSON(c, http.StatusInternalServerError, err.Error(), "Failed to update node drained state.")
		log.Errorf("failed to update drained state for node %s: %v", node.Id(), err)
		return
	}
	c.Status(http.StatusOK)
}

func (r *Router) getServerConfiguration(c *gin.Context) {
	s := middleware.ExtractServer(c)
	configuration, err := server.GetServerConfiguration(s)
	if err != nil {
		bp := r.BlueprintRegistry.Get(s.BlueprintId())
		if bp == nil {
			httperror.JSON(c, http.StatusNotFound, "blueprint_id="+s.BlueprintId(), "Server configuration snapshot is missing and the referenced blueprint does not exist.")
			return
		}
		configuration, err = server.EnsureServerSnapshot(s, bp, r.SoftwareRegistry)
		if err != nil {
			httperror.JSON(c, http.StatusNotFound, err.Error(), "Could not build server configuration.")
			return
		}
	}
	c.JSON(http.StatusOK, configuration)
}

func (r *Router) getAllServerConfigurations(c *gin.Context) {
	// Extract the node from the context
	n := middleware.ExtractNode(c)
	nodeId := n.Id()

	// Parse pagination query parameters
	// The daemon uses "page" and "per_page" query parameters
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "50"))

	// Validate and set defaults
	if page < 1 {
		page = 1
	}
	if perPage < 1 {
		perPage = 50
	}

	// Get all servers for this node
	servers := r.ServerManager.ServersByNode(nodeId)

	// Build configurations for all servers
	configurations := make([]*models.ServerConfigurationResponse, 0, len(servers))
	for _, s := range servers {
		configuration, err := server.GetServerConfiguration(s)
		if err != nil {
			bp := r.BlueprintRegistry.Get(s.BlueprintId())
			if bp == nil {
				// Skip legacy servers with no snapshot and missing blueprints.
				log.WithField("server", s.Id()).WithField("blueprint_id", s.BlueprintId()).
					Warn("skipping server configuration: snapshot is missing and referenced blueprint does not exist")
				continue
			}

			configuration, err = server.EnsureServerSnapshot(s, bp, r.SoftwareRegistry)
			if err != nil {
				// Skip servers with configuration errors, log but don't fail the request
				log.WithField("server", s.Id()).WithError(err).
					Warn("skipping server configuration: failed to generate configuration")
				continue
			}
		}

		configurations = append(configurations, configuration)
	}

	// Calculate pagination
	total := len(configurations)
	start := (page - 1) * perPage
	end := start + perPage

	if start > total {
		start = total
	}
	if end > total {
		end = total
	}

	// Get the paged slice
	var paged []*models.ServerConfigurationResponse
	if start < total {
		paged = configurations[start:end]
	} else {
		paged = []*models.ServerConfigurationResponse{}
	}

	// Calculate pagination metadata
	totalPages := 0
	if perPage > 0 && total > 0 {
		totalPages = (total + perPage - 1) / perPage
	}

	from := uint(0)
	to := uint(0)
	if total > 0 {
		from = uint(start + 1)
		to = uint(end)
	}

	// Return paginated response
	c.JSON(http.StatusOK, gin.H{
		"data": paged,
		"meta": gin.H{
			"current_page": uint(page),
			"from":         from,
			"last_page":    uint(totalPages),
			"per_page":     uint(perPage),
			"to":           to,
			"total":        uint(total),
		},
	})
}
