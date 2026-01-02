package router

import (
	"net"
	"net/http"
	"os"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/daemon/api/router/middleware"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/server"
	"protoxon.com/sls/daemon/server/installer"
	"protoxon.com/sls/daemon/system"
)

func (r *Router) postCreateServer(c *gin.Context) {
	// Parse incoming JSON body
	var req models.CreateServerRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// todo verify that the incoming request fields are correct

	// Check if the base server folder has been installed
	if !installer.IsInstalled(req.ServerFolder) {
		c.Status(http.StatusAccepted)
		// If the base server folder doesn't exist create it
		go func() {
			installer.Install()
		}()
	}

	// Create the server
	server, err := r.ServerManager.Create(req)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			// A not exists error usually means the blueprints server or world paths don't exist
			c.JSON(http.StatusConflict, gin.H{
				"error": "The specified path to the server or world directory does not exist on this daemon instance.",
			})
			log.WithError(err).Errorf("Failed to create server %s", req.ID)
			return
		}
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// todo handle allocations differently
	// this just returns the computers ipv4 address which will only work for local network connections
	Alloc := server.Config().Allocations
	Alloc.DefaultMapping.Ip, err = GetLocalIPv4()
	if err != nil {
		Alloc.DefaultMapping.Ip = "unknown"
	}

	// At this point respond with a status accepted
	c.JSON(http.StatusAccepted, models.CreateServerResponse{
		Allocation: Alloc,
	})

	// Start the server
	go func() {
		if err := server.Environment.Start(server.Context()); err != nil {
			log.WithError(err).Error("failed to start server container")
		}
	}()
}

// Returns all the servers that are registered and configured correctly on
// this daemon instance.
func (r *Router) getAllServers(c *gin.Context) {
	servers := r.ServerManager.All()
	out := make([]server.APIResponse, len(servers), len(servers))
	for i, v := range servers {
		out[i] = v.ToAPIResponse()
	}
	c.JSON(http.StatusOK, out)
}

// GetLocalIPv4 returns the first non-loopback IPv4 address of the computer
func GetLocalIPv4() (string, error) {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "", err
	}

	for _, addr := range addrs {
		var ip net.IP

		switch v := addr.(type) {
		case *net.IPNet:
			ip = v.IP
		case *net.IPAddr:
			ip = v.IP
		}

		if ip == nil || ip.IsLoopback() {
			continue
		}

		if ip = ip.To4(); ip != nil {
			return ip.String(), nil
		}
	}

	return "", errors.New("no non-loopback IPv4 address found")
}

// Returns information about the system that wings is running on.
func getSystemInformation(c *gin.Context) {
	i, err := system.GetSystemInformation()
	if err != nil {
		middleware.CaptureAndAbort(c, err)
		return
	}
	c.JSON(http.StatusOK, i)
}
