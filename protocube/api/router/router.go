package router

import (
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/auth"
)

// Configure configures the routing infrastructure.
func (r *Router) Configure() *gin.Engine {
	gin.SetMode("release")

	router := gin.New()
	router.Use(gin.Recovery())
	router.Use(middleware.AttachRequestID(), middleware.CaptureErrors(), middleware.SetAccessControlHeaders())
	router.Use(middleware.RateLimiter(), middleware.Timeout())
	router.GET("", postBanner)
	router.GET("/nodes", r.getNodes)

	// All the routes beyond this mount will use an authorization middleware
	// and will not be accessible without the correct Authorization header provided.
	protected := router.Group("/api")
	protected.Use(middleware.RequireAuthorization(r.VerifyToken, auth.Application))
	{
		protected.GET("/system", getSystemInformation)
		protected.GET("/servers", r.getAllServers)
		protected.POST("/servers", r.postCreateServer)
		protected.GET("/blueprints", r.getAllBlueprints)
		protected.POST("/blueprints/reload", r.postReloadBlueprints)
		protected.POST("/software/reload", r.postReloadSoftware)
		protected.GET("/events", r.getEventStream)
		protected.GET("/events/ws", r.getServerWebsocket)
	}

	// Routes for the node api
	// These require that the request be authorized with a node api key
	// The node api is used by nodes to make requests to protocube
	node := router.Group("/api/node")
	node.Use(middleware.RequireAuthorization(r.VerifyToken, auth.Node))
	node.POST("/register", r.postNodeRegister)
	// Ensure the node is connected and exists on the system
	node.Use(middleware.NodeExists(r.NodeManager))
	{
		node.POST("/heartbeat", postNodeHeartbeat)
		node.POST("/disconnect", r.postNodeDisconnect)

		// Node events
		event := node.Group("/event/servers/:server")
		event.Use(middleware.ServerExists(r.ServerManager))
		{
			event.POST("/status", r.postNodeServerStatus)
			event.POST("/crash", r.postEventServerCrash)
			event.POST("/deleted", r.postEventServerDeleted)
		}
	}

	// These are server specific routes, and require that the request be authorized, and
	// that the server exist on the Daemon.
	server := router.Group("/api/servers/:server")
	server.Use(middleware.RequireAuthorization(r.VerifyToken, auth.Application), middleware.ServerExists(r.ServerManager))
	{
		server.GET("", getServer)
		server.DELETE("", deleteServer)

		server.GET("/logs", getServerLogs)
		server.POST("/power", postServerPower)
		server.GET("/status", getServerStatus)
		server.GET("/stats", getServerStats)
		server.POST("/commands", postServerCommands)
		//server.POST("/install", postServerInstall)
		//server.POST("/reinstall", postServerReinstall)
		//server.POST("/sync", postServerSync)
		//server.POST("/ws/deny", postServerDenyWSTokens)
	}

	return router
}
