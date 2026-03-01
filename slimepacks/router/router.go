package router

import (
	"protoxon.com/sls/slimepacks/slimepack"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/middleware"
)

// RegisterRoutes Registers the api routes for SlimePacks
func RegisterRoutes(router *gin.Engine, manager *slimepack.Manager) {
	slimepacks := router.Group("/api/slimepacks")

	// Set middleware
	slimepacks.Use(middleware.AttachRequestID(), middleware.CaptureErrors(), middleware.SetAccessControlHeaders())
	slimepacks.Use(middleware.RateLimiter(), middleware.Timeout())

	// Route for listing all slimepacks
	slimepacks.GET("", func(c *gin.Context) {
		getSlimePacks(c, *manager)
	})

	// Route for downloading a single pack
	pack := slimepacks.Group("/:slimepack")
	pack.Use(PackExists(manager))
	{
		pack.GET("", getSlimePack)
	}
}
