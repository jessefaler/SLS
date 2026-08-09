package router

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/httperror"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/auth/scope"
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

	// All the routes beyond this point will use an authorization middleware
	// and will not be accessible without the correct Authorization header provided.
	protected := router.Group("/api")
	protected.Use(middleware.RequireAuthorization(r.KeyService, scope.AppAdmin))
	{
		protected.GET("/nodes", r.getAllNodes)
		protected.GET("/system", getSystemInformation)
		protected.GET("/servers", r.getAllServers)
		protected.POST("/servers", r.postCreateServer)
		protected.GET("/blueprints", r.getAllBlueprints)
		protected.POST("/blueprints/reload", r.postReloadBlueprints)
		protected.GET("/mixins", r.getAllMixins)
		protected.POST("/software/reload", r.postReloadSoftware)
		protected.GET("/events", r.getEventStream)
		protected.GET("/events/ws", r.getServerWebsocket)
	}

	// These are blueprint specific routes, and require that the request be authorized, and
	// that the blueprint exist.
	blueprintGroup := router.Group("/api/blueprints/:blueprint")
	blueprintGroup.Use(middleware.RequireAuthorization(r.KeyService, scope.Node), middleware.BlueprintExists(r.BlueprintRegistry))
	{
		blueprintGroup.GET("", getBlueprint)
	}

	// These are mixin specific routes, and require that the request be authorized, and
	// that the mixin exist.
	mixinGroup := router.Group("/api/mixins/:mixin")
	mixinGroup.Use(middleware.RequireAuthorization(r.KeyService, scope.Node), middleware.MixinExists(r.MixinRegistry))
	{
		mixinGroup.GET("", getMixin)
	}

	// These are server specific routes, and require that the request be authorized, and
	// that the server exist.
	server := router.Group("/api/servers/:server")
	server.Use(middleware.RequireAuthorization(r.KeyService, scope.AppAdmin), middleware.ServerExists(r.ServerManager))
	{
		server.GET("", getServer)
		server.DELETE("", deleteServer)

		server.GET("/logs", getServerLogs)
		server.POST("/power", postServerPower)
		server.GET("/status", getServerStatus)
		server.GET("/stats", getServerStats)
		server.POST("/commands", postServerCommands)
		server.POST("/reset", postServerReset)
		server.GET("/install/logs", getServerInstallLogs)
		server.GET("/install", getServerInstallInfo)
		server.POST("/reinstall", postServerReinstall)
		//server.POST("/sync", postServerSync)
		//server.POST("/ws/deny", postServerDenyWSTokens)
	}

	// Node Registration
	registration := router.Group("/api/nodes/:node")
	registration.Use(middleware.RequireAuthorization(r.KeyService, scope.Node))
	registration.POST("/register", r.postNodeRegister)

	// These are node specific routes, and require that the request be authorized, and
	// that the node exists.
	// TODO: Current node routes are becoming messy and deeply nested.
	// TODO: Refactor to a flat structure like /api/remote/... where the node is
	//       identified via its API key instead of path parameters.
	node := router.Group("/api/nodes/:node")
	node.Use(middleware.RequireAuthorization(r.KeyService, scope.AppAdmin), middleware.NodeExists(r.NodeManager))
	{
		node.GET("", getNode)
		node.GET("/system", getNodeSystemInfo)
		node.PATCH("/drained", toggleNodeDrained)

		// These are routes for internal communication
		internal := router.Group("/api/nodes/:node/internal")
		internal.Use(middleware.RequireAuthorization(r.KeyService, scope.Node), middleware.NodeExists(r.NodeManager))
		internal.POST("/heartbeat", postNodeHeartbeat)
		internal.POST("/disconnect", r.postNodeDisconnect)

		// Routes for the node to retrieve server configurations
		internal.GET("/servers", r.getAllServerConfigurations)
		nodeServer := internal.Group("/servers/:server")
		nodeServer.Use(middleware.ServerExists(r.ServerManager))
		nodeServer.GET("", r.getServerConfiguration)
		nodeServer.GET("/install", r.getInstallInfo)

		// Node events
		event := internal.Group("/event/servers/:server")
		event.Use(middleware.ServerExists(r.ServerManager))
		{
			event.POST("/status", postNodeServerStatus)
			event.POST("/install-status", postNodeServerInstallStatus)
			event.POST("/crash", postEventServerCrash)
			event.POST("/deleted", postEventServerDeleted)
		}
	}

	// Return JSON error bodies for unmatched routes and methods so API clients
	// (e.g. daemon remote client) get a parseable response instead of _MissingResponseCode.
	router.NoRoute(func(c *gin.Context) {
		httperror.JSON(c, http.StatusNotFound, "no matching route", "The requested resource does not exist.")
	})
	router.NoMethod(func(c *gin.Context) {
		httperror.JSON(c, http.StatusMethodNotAllowed, "method not allowed for route", "Method not allowed.")
	})

	return router
}
