package middleware

import (
	"io"
	"net/http"
	"strings"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"protoxon.com/sls/daemon/api/router/httperror"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/server"
)

// AttachRequestID attaches a unique ID to the incoming HTTP request so that any
// errors that are generated or returned to the client will include this reference
// allowing for an easier time identifying the specific request that failed for
// the user.
//
// If you are using a tool such as Sentry or Bugsnag for error reporting this is
// a great location to also attach this request ID to your error handling logic
// so that you can easily cross-reference the errors.
func AttachRequestID() gin.HandlerFunc {
	return func(c *gin.Context) {
		id := uuid.New().String()
		c.Set("request_id", id)
		c.Set("logger", log.WithField("request_id", id))
		c.Header("X-Request-Id", id)
		c.Next()
	}
}

// CaptureAndAbort aborts the request and attaches the provided error to the gin
// context, so it can be reported properly. If the error is missing a stacktrace
// at the time it is called the stack will be attached.
func CaptureAndAbort(c *gin.Context, err error) {
	c.Abort()
	c.Error(errors.WithStackDepthIf(err, 1))
}

// CaptureErrors is custom handler function allowing for errors bubbled up by
// c.Error() to be returned in a standardized format with tracking UUIDs on them
// for easier log searching.
func CaptureErrors() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Next()
		err := c.Errors.Last()
		if err == nil || err.Err == nil {
			return
		}

		status := http.StatusInternalServerError
		if c.Writer.Status() != 200 {
			status = c.Writer.Status()
		}
		if err.Error() == io.EOF.Error() {
			httperror.AbortWithJSON(c, http.StatusBadRequest, "empty or unreadable request body", "The data passed in the request was not in a parsable format. Please try again.")
			return
		}
		captured := NewError(err.Err)
		captured.Abort(c, status)
	}
}

// SetAccessControlHeaders sets the access request control headers on all
// the requests.
func SetAccessControlHeaders() gin.HandlerFunc {
	cfg := config.Get()
	origins := cfg.AllowedOrigins
	location := cfg.RemoteApi.Url
	allowPrivateNetwork := cfg.AllowCORSPrivateNetwork

	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", location)
		c.Header("Access-Control-Allow-Credentials", "true")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PATCH, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Accept, Accept-Encoding, Authorization, Cache-Control, Content-Type, Content-Length, Origin, X-Real-IP, X-CSRF-NodeToken")

		// CORS for Private Networks (RFC1918)
		// @see https://developer.chrome.com/blog/private-network-access-update/?utm_source=devtools
		if allowPrivateNetwork {
			c.Header("Access-Control-Request-Private-Network", "true")
		}

		// Maximum age allowable under Chromium v76 is 2 hours, so just use that since
		// anything higher will be ignored (even if other browsers do allow higher values).
		//
		// @see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Max-Age#Directives
		c.Header("Access-Control-Max-Age", "7200")

		// Validate that the request origin is coming from an allowed origin. Because you
		// cannot set multiple values here we need to see if the origin is one of the ones
		// that we allow, and if so return it explicitly. Otherwise, just return the default
		// origin which is the same URL that protocube is located at.
		origin := c.GetHeader("Origin")
		if origin != location {
			for _, o := range origins {
				if o != "*" && o != origin {
					continue
				}
				c.Header("Access-Control-Allow-Origin", o)
				break
			}
		}
		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}

// ServerExists will ensure that the requested server exists in this setup.
// Returns a 404 if we cannot locate it. If the server is found it is set into
// the request context, and the logger for the context is also updated to include
// the server ID in the fields list.
func ServerExists(manager *server.Manager) gin.HandlerFunc {
	return func(c *gin.Context) {
		var s *server.Server
		if c.Param("server") != "" {
			s = manager.Find(func(s *server.Server) bool {
				return c.Param("server") == s.ID()
			})
		}
		if s == nil {
			httperror.AbortWithJSON(c, http.StatusNotFound, "resource not found", "The requested resource does not exist on this instance.")
			return
		}
		c.Set("logger", ExtractLogger(c).WithField("server_id", s.ID()))
		c.Set("server", s)
		c.Next()
	}
}

// RequireAuthorization authenticates the request using the verification function.
func RequireAuthorization(verify func(token string) bool) gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			c.Header("WWW-Authenticate", "Bearer")
			httperror.AbortWithJSON(c, http.StatusUnauthorized, "", "The required authorization header was not present in the request.")
			logUnauthorisedAccess("The required authorization header was not present in the request.", c, "")
			return
		}

		if !strings.HasPrefix(authHeader, "Bearer ") {
			httperror.AbortWithJSON(c, http.StatusUnauthorized, "", "Invalid authorization header format.")
			logUnauthorisedAccess("Invalid authorization header format", c, "")
			return
		}

		tokenStr := strings.TrimPrefix(authHeader, "Bearer ")

		valid := verify(tokenStr)
		if !valid {
			httperror.AbortWithJSON(c, http.StatusUnauthorized, "invalid token", "You are not authorized to access this endpoint.")
			logUnauthorisedAccess("Invalid token", c, tokenStr)
			return
		}

		c.Next()
	}
}

func logUnauthorisedAccess(reason string, c *gin.Context, tokenStr string) {
	log.WithFields(log.Fields{
		"reason":      reason,
		"client_ip":   c.ClientIP(),
		"remote_addr": c.Request.RemoteAddr,
		"method":      c.Request.Method,
		"path":        c.Request.URL.Path,
		"token_len":   len(tokenStr),
		"token_head":  tokenHead(tokenStr),
	}).Warn("unauthorized access attempt rejected")
}

// tokenHead returns the leading token segment (e.g., "SLS_aB3dE1").
func tokenHead(token string) string {
	parts := strings.Split(token, "_")
	if len(parts) >= 2 {
		return parts[0] + "_" + parts[1]
	}
	return ""
}

// ExtractLogger pulls the logger out of the request context and returns it. By
// default, this will include the request ID, but may also include the server ID
// if that middleware has been used in the chain by the time it is called.
func ExtractLogger(c *gin.Context) *log.Entry {
	v, ok := c.Get("logger")
	if !ok {
		panic("middleware/middleware: cannot extract logger: not present in request context")
	}
	return v.(*log.Entry)
}

// ExtractServer will return the server from the gin.Context or panic if it is
// not present.
func ExtractServer(c *gin.Context) *server.Server {
	v, ok := c.Get("server")
	if !ok {
		panic("middleware/middleware: cannot extract server: not present in request context")
	}
	return v.(*server.Server)
}
