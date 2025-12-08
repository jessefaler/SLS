package router

import (
	"net/http"
	"strconv"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/events"
	"protoxon.com/sls/protocube/models"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/software"
)

func (r *Router) getAllBlueprints(c *gin.Context) {
	// Parse page & limit
	page, _ := strconv.Atoi(c.DefaultQuery("page", "20"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "100"))

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

func (r *Router) postCreateServer(c *gin.Context) {
	balanced := r.LoadBalancer.PickNode()
	if balanced == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "no nodes available"})
		return
	}

	node, ok := balanced.(*node.Node)
	if !ok {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "unexpected node type"})
		return
	}

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

	bp := r.BlueprintRegistry.Get(req.BlueprintID)
	if bp == nil {
		c.AbortWithStatusJSON(http.StatusNotFound, gin.H{"error": "No such blueprint with id: " + req.BlueprintID})
		return
	}

	server, err := r.ServerManager.CreateServer(c.Request.Context(), node, bp, r.SoftwareRegistry)
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
