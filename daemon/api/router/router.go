package router

import (
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/daemon/api/router/middleware"
)

// Configure configures the routing infrastructure.
func (r *Router) Configure() *gin.Engine {
	gin.SetMode("release")

	router := gin.New()
	router.Use(gin.Recovery())

	router.Use(middleware.AttachRequestID(), middleware.CaptureErrors(), middleware.SetAccessControlHeaders())

	router.GET("/", postBanner)

	// All the routes beyond this mount will use an authorization middleware
	// and will not be accessible without the correct Authorization header provided.
	protected := router.Use(middleware.RequireAuthorization(r.VerifyToken))

	protected.GET("/api/system") //temp
	//protected.GET("/api/system", getSystemInformation)
	//protected.GET("/api/servers", getAllServers)
	protected.POST("/api/servers", r.postCreateServer)

	// These are server specific routes, and require that the request be authorized, and
	// that the server exist on the Daemon.
	server := router.Group("/api/servers/:server")
	server.Use(middleware.RequireAuthorization(r.VerifyToken), middleware.ServerExists(r.ServerManager))
	{
		//server.GET("", getServer)
		//server.DELETE("", deleteServer)

		//server.GET("/logs", getServerLogs)
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
