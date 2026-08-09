package router

import (
	"net/http"
	"strconv"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/httperror"
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
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "50"))

	if page < 1 {
		page = 1
	}
	if perPage < 1 {
		perPage = 20
	}

	// If only meta requested, paginate meta list
	items := make([]*blueprint.Blueprint, 0)
	if c.Query("meta") == "true" {
		//items = r.BlueprintRegistry.AllMeta()
	} else {
		items = r.BlueprintRegistry.All()
	}

	total := len(items)
	start := (page - 1) * perPage
	end := start + perPage
	if start > total {
		start = total
	}
	if end > total {
		end = total
	}

	paged := items[start:end]

	totalPages := 0
	if perPage > 0 && total > 0 {
		totalPages = (total + perPage - 1) / perPage
	}

	c.JSON(http.StatusOK, gin.H{
		"meta": gin.H{
			"pagination": gin.H{
				"total":        total,
				"per_page":     perPage,
				"current_page": page,
				"total_pages":  totalPages,
			},
		},
		"data": paged,
	})
}

func getBlueprint(c *gin.Context) {
	bp := middleware.ExtractBlueprint(c)
	c.JSON(http.StatusOK, bp)
}

func (r *Router) getAllMixins(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "50"))

	if page < 1 {
		page = 1
	}
	if perPage < 1 {
		perPage = 20
	}

	items := make([]*blueprint.Mixin, 0)
	if c.Query("meta") == "true" {
		// items = r.MixinRegistry.AllMeta()
	} else if r.MixinRegistry != nil {
		items = r.MixinRegistry.All()
	}

	total := len(items)
	start := (page - 1) * perPage
	end := start + perPage
	if start > total {
		start = total
	}
	if end > total {
		end = total
	}

	paged := items[start:end]

	totalPages := 0
	if perPage > 0 && total > 0 {
		totalPages = (total + perPage - 1) / perPage
	}

	c.JSON(http.StatusOK, gin.H{
		"meta": gin.H{
			"pagination": gin.H{
				"total":        total,
				"per_page":     perPage,
				"current_page": page,
				"total_pages":  totalPages,
			},
		},
		"data": paged,
	})
}

func getMixin(c *gin.Context) {
	m := middleware.ExtractMixin(c)
	c.JSON(http.StatusOK, m)
}

func (r *Router) getInstallationScript(c *gin.Context) {
	s := middleware.ExtractServer(c)
	if s.InstallScript == nil {
		httperror.AbortWithJSON(c, http.StatusInternalServerError, "install script is missing", "Server install script is missing.")
		return
	}
	c.JSON(http.StatusOK, s.InstallScript)
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

	out := make([]models.ServerData, len(servers))
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
		httperror.AbortWithJSON(c, http.StatusUnprocessableEntity, err.Error(), "The data provided in the request could not be validated.")
		return
	}

	var n *node.Node
	if req.NodeId != "" {
		// a node id was provided
		// so try to use the provided node
		var ok bool
		n, ok = r.NodeManager.Get(req.NodeId)
		if !ok || n == nil {
			httperror.JSON(c, http.StatusNotFound, "node not found: "+req.NodeId, "The requested node was not found.")
			return
		}
	} else {
		// The node id was not provided
		// so use the load balancer to get a node
		balanced := r.LoadBalancer.Get().PickNode()
		if balanced == nil {
			httperror.JSON(c, http.StatusServiceUnavailable, "no nodes available", "No nodes are available to create a server.")
			return
		}

		var ok bool
		n, ok = balanced.(*node.Node)
		if !ok || n == nil {
			httperror.JSON(c, http.StatusInternalServerError, "load balancer returned unexpected node type", "An unexpected error occurred while assigning a node.")
			return
		}
	}

	bp := r.BlueprintRegistry.Get(req.BlueprintID)
	if bp == nil {
		httperror.AbortWithJSON(c, http.StatusNotFound, "no blueprint with id "+req.BlueprintID, "Blueprint not found.")
		return
	}

	server, err := r.ServerManager.CreateServer(c.Request.Context(), n, bp, r.SoftwareRegistry, req.Overrides)
	if err != nil {
		log.WithError(err).Error("failed to create server")
		client.HandleError(c, err)
		return
	}

	c.JSON(http.StatusAccepted, server.ServerData())
}

func (r *Router) postReloadSoftware(c *gin.Context) {
	sw, err := software.LoadAllSoftware(config.Get().System.Software)
	if err != nil {
		log.WithError(err).Fatal("failed to load software configurations")
	}
	r.SoftwareRegistry.ReplaceAll(sw)
	log.WithField("root", config.Get().System.Software).Infof("Loaded %d software configurations.", len(sw))
	c.Status(http.StatusOK)
}

func (r *Router) postReloadBlueprints(c *gin.Context) {
	loaded, err := blueprint.SyncAndLoadConfigured(true, r.SoftwareRegistry)
	if err != nil {
		log.WithError(err).Error("failed to reload blueprints")
		httperror.AbortWithJSON(c, http.StatusInternalServerError, err.Error(), "Failed to reload blueprints.")
		return
	}
	if r.MixinRegistry != nil {
		r.MixinRegistry.ReplaceAll(loaded.Mixins)
	}
	r.BlueprintRegistry.ReplaceAll(loaded.Blueprints)
	log.WithField("root", config.Get().System.Blueprints).
		Infof("Reloaded registries. Loaded %d blueprints and %d mixins", len(loaded.Blueprints), len(loaded.Mixins))
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
