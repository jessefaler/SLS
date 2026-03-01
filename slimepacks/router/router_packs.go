package router

import (
	"path/filepath"

	"protoxon.com/sls/slimepacks/slimepack"

	"github.com/gin-gonic/gin"
)

func getSlimePacks(c *gin.Context, manager slimepack.Manager) {
	packs := manager.GetPacks()
	// collect all IDs
	ids := make([]string, len(packs))
	for i, pack := range packs {
		ids[i] = pack.Id()
	}

	// return as JSON
	c.JSON(200, gin.H{"slimepacks": ids})
}

func getSlimePack(c *gin.Context) {
	pack := ExtractPack(c)

	version := c.DefaultQuery("version", c.DefaultQuery("v", ""))

	// Path to the generated resource pack zip
	filePath, err := pack.GetVersion(version)
	if err != nil {
		c.JSON(500, gin.H{"error": err.Error()})
		return
	}

	// Send the file as a download
	c.FileAttachment(filePath, filepath.Base(filePath))
}
