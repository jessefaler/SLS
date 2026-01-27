package router

import (
	"context"
	"net"
	"net/http"
	"os"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/daemon/api/router/middleware"
	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/server"
	"protoxon.com/sls/daemon/system"
)

func (r *Router) postCreateServer(c *gin.Context) {
	// Parse incoming JSON body
	var req models.ServerConfigurationResponse
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// create the server
	s, err := r.ServerManager.InitServer(req)
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
	Alloc := s.Config().Allocations
	Alloc.DefaultMapping.Ip, err = GetLocalIPv4()
	if err != nil {
		Alloc.DefaultMapping.Ip = "unknown"
	}

	// Respond with a status accepted
	c.JSON(http.StatusAccepted, models.CreateServerResponse{
		Allocation: Alloc,
	})

	// Start the server
	// Pass the actual heavy processing off to a separate thread to handle so that
	// we can immediately return a response from the server. Some of these actions
	// can take quite some time, especially stopping or restarting.
	go func(s *server.Server) {
		action := server.PowerActionStart
		if err := s.HandlePowerAction(server.PowerAction(action), 0); err != nil {
			if errors.Is(err, context.DeadlineExceeded) {
				s.Log().WithField("action", action).WithField("error", err).Warn("could not process server power action")
			} else if errors.Is(err, server.ErrIsRunning) {
				// Do nothing, this isn't something we care about for logging,
			} else {
				s.Log().WithFields(log.Fields{"action": action, "wait_seconds": 0, "error": err}).
					Error("encountered error processing a server power action in the background")
			}
		}
	}(s)
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

// Syncs the daemons servers with Protocube
func (r *Router) postSync(c *gin.Context) {
	err := r.ServerManager.Sync(c.Request.Context())
	if err != nil {
		log.WithError(err).Error("failed to sync server configurations")
		c.JSON(http.StatusInternalServerError, gin.H{
			"error": "failed to sync server configurations",
		})
		return
	}
	c.Status(http.StatusOK)
}
