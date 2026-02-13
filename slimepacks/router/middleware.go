package router

import (
	"SlimePacks/slimepack"
	"net/http"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/middleware"
)

// PackExists will ensure that the requested slimepack exists.
// Returns a 404 if we cannot locate it. If the pack is found it is set into
// the request context, and the logger for the context is also updated to include
// the pack id in the fields list.
func PackExists(manager *slimepack.Manager) gin.HandlerFunc {
	return func(c *gin.Context) {
		var pack *slimepack.Pack
		if c.Param("slimepack") != "" {
			pack = manager.GetPack(c.Param("slimepack"))
		}
		if pack == nil {
			c.AbortWithStatusJSON(http.StatusNotFound, gin.H{"error": "The requested resource does not exist on this instance."})
			return
		}
		c.Set("logger", middleware.ExtractLogger(c).WithField("pack", pack.Id()))
		c.Set("slimepack", pack)
		c.Next()
	}
}

// ExtractPack will return the slimepack from the gin.Context or panic if it is
// not present.
func ExtractPack(c *gin.Context) *slimepack.Pack {
	v, ok := c.Get("slimepack")
	if !ok {
		panic("router/middleware: cannot extract slimepack: not present in request context")
	}
	return v.(*slimepack.Pack)
}
