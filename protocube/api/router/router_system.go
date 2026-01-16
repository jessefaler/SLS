package router

import (
	"net/http"
	"strconv"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/events"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/software"
	"protoxon.com/sls/protocube/system"
)

func (r *Router) getAllBlueprints(c *gin.Context) {
	// Parse page & limit
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "50"))

	if page < 1 {
		page = 1
	}
	if limit < 1 {
		limit = 20
	}

	// If only meta requested, paginate meta list
	var items []*blueprint.Blueprint
	if c.Query("meta") == "true" {
		//items = r.BlueprintRegistry.AllMeta()
	} else {
		items = r.BlueprintRegistry.All()
	}

	total := len(items)
	start := (page - 1) * limit
	end := start + limit
	if start > total {
		start = total
	}
	if end > total {
		end = total
	}

	paged := items[start:end]

	totalPages := 0
	if limit > 0 && total > 0 {
		totalPages = (total + limit - 1) / limit
	}

	c.JSON(http.StatusOK, gin.H{
		"meta": gin.H{
			"pagination": gin.H{
				"total":        total,
				"per_page":     limit,
				"current_page": page,
				"total_pages":  totalPages,
			},
		},
		"data": paged,
	})
}

// Returns all servers
func (r *Router) getAllServers(c *gin.Context) {
	servers := r.ServerManager.All()

	// If ids_only query parameter is set, return only the IDs
	if c.Query("ids_only") == "true" {
		ids := make([]string, len(servers))
		for i, v := range servers {
			ids[i] = v.Id()
		}
		c.JSON(http.StatusOK, ids)
		return
	}

	out := make([]models.ServerData, len(servers), len(servers))
	for i, v := range servers {
		out[i] = v.ServerData()
	}
	c.JSON(http.StatusOK, out)
}

func (r *Router) getAllNodes(c *gin.Context) {
	nodes := r.NodeManager.GetNodes()

	// If ids_only query parameter is set, return only the IDs
	if c.Query("ids_only") == "true" {
		ids := make([]string, len(nodes))
		for i, v := range nodes {
			ids[i] = v.Id()
		}
		c.JSON(http.StatusOK, ids)
		return
	}

	out := make([]models.NodeData, len(nodes))
	for i, n := range nodes {
		out[i] = models.NodeData{
			ID:       n.Id(),
			Name:     n.Name(),
			Location: n.Location(),
			URL:      n.Url(),
			Drained:  n.Drained(),
		}
	}
	c.JSON(http.StatusOK, out)
}

// Returns information about the system that protocube is running on.
func getSystemInformation(c *gin.Context) {
	i, err := system.GetSystemInformation()
	if err != nil {
		middleware.CaptureAndAbort(c, err)
		return
	}
	c.JSON(http.StatusOK, i)
}

func (r *Router) postCreateServer(c *gin.Context) {
	var req models.CreateServerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		log.WithError(err).Error("Failed to create server")
		c.AbortWithStatusJSON(http.StatusUnprocessableEntity, gin.H{
			"status": http.StatusUnprocessableEntity,
			"code":   "ValidationFailed",
			"error":  "The data provided in the request could not be validated.",
		})
		return
	}

	var n *node.Node
	if req.NodeId != "" {
		// a node id was provided
		// so try to use the provided node
		var ok bool
		n, ok = r.NodeManager.Get(req.NodeId)
		if !ok || n == nil {
			c.JSON(http.StatusNotFound, gin.H{"error": "node not found: " + req.NodeId})
			return
		}
	} else {
		// The node id was not provided
		// so use the load balancer to get a node
		balanced := r.LoadBalancer.Get().PickNode()
		if balanced == nil {
			c.JSON(http.StatusServiceUnavailable, gin.H{"error": "no nodes available"})
			return
		}

		var ok bool
		n, ok = balanced.(*node.Node)
		if !ok || n == nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "unexpected node type"})
			return
		}
	}

	bp := r.BlueprintRegistry.Get(req.BlueprintID)
	if bp == nil {
		c.AbortWithStatusJSON(http.StatusNotFound, gin.H{"error": "No such blueprint with id: " + req.BlueprintID})
		return
	}

	server, err := r.ServerManager.CreateServer(c.Request.Context(), n, bp, r.SoftwareRegistry, req.Overrides)
	if err != nil {
		log.WithError(err).Error("Failed to create server")
		client.HandleError(c, err)
		return
	}

	c.JSON(http.StatusAccepted, server.ServerData())
}

func (r *Router) postReloadSoftware(c *gin.Context) {
	sw, err := software.LoadAllSoftware(config.Get().Software.Root)
	if err != nil {
		log.WithError(err).Fatal("failed to load software configurations")
	}
	r.SoftwareRegistry.ReplaceAll(sw)
	log.WithField("root", config.Get().Software.Root).Infof("Loaded %d software configurations.", len(sw))
	c.Status(http.StatusOK)
}

func (r *Router) postReloadBlueprints(c *gin.Context) {
	blueprints, err := blueprint.LoadAllBlueprints(config.Get().Blueprints.Root, r.SoftwareRegistry)
	if err != nil {
		log.WithError(err).Fatal("failed to load blueprints")
	}
	r.BlueprintRegistry.ReplaceAll(blueprints)
	log.WithField("root", config.Get().Blueprints.Root).Infof("Reloaded blueprint registry. Loaded %d blueprints", len(blueprints))
	c.Status(http.StatusOK)
}

// The event stream streams global events for all server events
func (r *Router) getEventStream(c *gin.Context) {
	log.Info("opened event stream")
	// SSE headers
	c.Writer.Header().Set("Content-Type", "text/event-stream")
	c.Writer.Header().Set("Cache-Control", "no-cache")
	c.Writer.Header().Set("Connection", "keep-alive")
	c.Writer.Header().Set("Access-Control-Allow-Origin", "*")

	flusher, ok := c.Writer.(http.Flusher)
	if !ok {
		c.String(http.StatusInternalServerError, "Streaming unsupported")
		return
	}

	// Create a sink channel for this client
	// Allow 32 buffered events
	// If the buffer is full the sink pool
	// will drop the oldest message
	ch := make(chan []byte, 32)

	// Subscribe to the global event bus
	r.ServerManager.Events().On(ch)

	// Unsubscribe when client disconnects
	defer r.ServerManager.Events().Off(ch)

	// Stream events until client closes connection
	for {
		select {
		case <-c.Request.Context().Done():
			log.Info("stream closed")
			return

		case data := <-ch:
			// Decode event
			ev := events.MustDecode(data)

			// Write SSE payload
			_, _ = c.Writer.Write([]byte("event: " + ev.Topic + "\n"))
			_, _ = c.Writer.Write([]byte("data: " + string(data) + "\n\n"))

			flusher.Flush()
		}
	}
}
