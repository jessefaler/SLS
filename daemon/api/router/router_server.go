package router

import (
	"context"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"unicode"

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
	if (data.Action == server.PowerActionStart || data.Action == server.PowerActionRestart) && s.IsSuspended() {
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
	if c.Query("update_disk_usage") == "true" {
		s.Filesystem().UpdateCachedDiskUsage()
	}
	c.JSON(http.StatusOK, s.Proc())
}

// Sends an array of commands to a running server instance.
func postServerCommands(c *gin.Context) {
	s := middleware.ExtractServer(c)

	if running, err := s.Environment.IsRunning(c.Request.Context()); err != nil {
		middleware.CaptureAndAbort(c, err)
		return
	} else if !running {
		c.AbortWithStatusJSON(http.StatusBadGateway, gin.H{
			"error": "Cannot send commands to a stopped server instance.",
		})
		return
	}

	var data struct {
		Commands []string `json:"commands"`
	}
	// BindJSON sends 400 if the request fails, all we need to do is return
	if err := c.BindJSON(&data); err != nil {
		return
	}

	for _, command := range data.Commands {
		if err := s.Environment.SendCommand(command); err != nil {
			s.Log().WithFields(log.Fields{"command": command, "error": err}).Warn("failed to send command to server instance")
		}
	}

	c.Status(http.StatusNoContent)
}

// Deletes a server from the daemon and dissociate its objects.
func (r *Router) deleteServer(c *gin.Context) {
	s := middleware.ExtractServer(c)
	if err := s.Delete(); err != nil {
		middleware.CaptureAndAbort(c, err)
		return
	}
	c.Status(http.StatusOK)
}

// ANSI escape code regex pattern to strip formatting codes from log lines
var stripAnsiRegex = regexp.MustCompile("[\u001B\u009B][[\\]()#;?]*(?:(?:(?:[a-zA-Z\\d]*(?:;[a-zA-Z\\d]*)*)?\u0007)|(?:(?:\\d{1,4}(?:;\\d{0,4})*)?[\\dA-PRZcf-ntqry=><~]))")

// Returns the logs for a given server instance.
func getServerLogs(c *gin.Context) {
	s := middleware.ExtractServer(c)

	l, _ := strconv.Atoi(c.DefaultQuery("size", "100"))
	if l <= 0 {
		l = 100
	} else if l > 100 {
		l = 100
	}

	out, err := s.ReadLogfile(l)
	if err != nil {
		middleware.CaptureAndAbort(c, err)
		return
	}

	// Strip ANSI formatting codes and other formatting from each log line
	stripped := make([]string, len(out))
	for i, line := range out {
		// Strip ANSI escape codes
		cleaned := stripAnsiRegex.ReplaceAllString(line, "")

		// Remove "> " prefix if present
		cleaned = strings.TrimPrefix(cleaned, "> ")

		// Remove carriage return characters
		cleaned = strings.ReplaceAll(cleaned, "\r", "")

		// Remove box-drawing and box characters (common Unicode box characters)
		cleaned = strings.Map(func(r rune) rune {
			// Remove box-drawing characters (U+2500 to U+257F)
			if r >= 0x2500 && r <= 0x257F {
				return -1
			}
			// Remove block elements (U+2580 to U+259F)
			if r >= 0x2580 && r <= 0x259F {
				return -1
			}
			// Remove other control characters except newline and tab
			if unicode.IsControl(r) && r != '\n' && r != '\t' {
				return -1
			}
			return r
		}, cleaned)

		// Trim leading/trailing whitespace
		cleaned = strings.TrimSpace(cleaned)

		stripped[i] = cleaned
	}

	c.JSON(http.StatusOK, gin.H{"data": stripped})
}
