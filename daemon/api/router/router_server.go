package router

import (
	"context"
	"net/http"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/daemon/api/router/middleware"
	"protoxon.com/sls/daemon/server"
)

// Handles a request to control the power state of a server. If the action being passed
// through is invalid a 404 is returned. Otherwise, a HTTP/202 Accepted response is returned
// and the actual power action is run asynchronously so that we don't have to block the
// request until a potentially slow operation completes.
//
// This is done because for the most part protocube is using websockets to determine when
// things are happening, so theres no reason to sit and wait for a request to finish. We'll
// just see over the socket if something isn't working correctly.
func postServerPower(c *gin.Context) {
	s := middleware.ExtractServer(c)

	var data struct {
		Action      server.PowerAction `json:"action"`
		WaitSeconds int                `json:"wait_seconds"`
	}

	if err := c.BindJSON(&data); err != nil {
		return
	}

	if !data.Action.IsValid() {
		c.AbortWithStatusJSON(http.StatusUnprocessableEntity, gin.H{
			"error": "The power action provided was not valid, should be one of \"stop\", \"start\", \"restart\", \"kill\"",
		})
		return
	}

	// Because we route all of the actual bootup process to a separate thread we need to
	// check the suspension status here, otherwise the user will hit the endpoint and then
	// just sit there wondering why it returns a success but nothing actually happens.
	//
	// We don't really care about any of the other actions at this point, they'll all result
	// in the process being stopped, which should have happened anyways if the server is suspended.
	if data.Action == server.PowerActionStart || data.Action == server.PowerActionRestart {
		c.AbortWithStatusJSON(http.StatusBadRequest, gin.H{
			"error": "Cannot start or restart a server that is suspended.",
		})
		return
	}

	// Pass the actual heavy processing off to a separate thread to handle so that
	// we can immediately return a response from the server. Some of these actions
	// can take quite some time, especially stopping or restarting.
	go func(s *server.Server) {
		if data.WaitSeconds < 0 || data.WaitSeconds > 300 {
			data.WaitSeconds = 30
		}
		if err := s.HandlePowerAction(data.Action, data.WaitSeconds); err != nil {
			if errors.Is(err, context.DeadlineExceeded) {
				s.Log().WithField("action", data.Action).WithField("error", err).Warn("could not process server power action")
			} else if errors.Is(err, server.ErrIsRunning) {
				// Do nothing, this isn't something we care about for logging,
			} else {
				s.Log().WithFields(log.Fields{"action": data.Action, "wait_seconds": data.WaitSeconds, "error": err}).
					Error("encountered error processing a server power action in the background")
			}
		}
	}(s)

	c.Status(http.StatusAccepted)
}

func getServerStatus(c *gin.Context) {
	s := middleware.ExtractServer(c)
	c.JSON(http.StatusOK, gin.H{
		"status": s.Environment.State(),
	})
}

func getServerStats(c *gin.Context) {
	s := middleware.ExtractServer(c)
	c.JSON(http.StatusOK, s.Proc().Stats)
}
