package router

import (
	"context"
	stdlog "log"
	"net"
	"net/http"
	"strings"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/api/router/middleware"
)

type Router struct {
	Handler    *gin.Engine
	HTTPServer *http.Server
	*Resources
}

// apexLogWriter is an io.Writer that redirects standard log output to the Apex logger
type apexLogWriter struct{}

func (w apexLogWriter) Write(p []byte) (n int, err error) {
	msg := strings.TrimSpace(string(p))
	// Remove the timestamp prefix from standard log messages if present
	// Standard format: "2006/01/02 15:04:05 message"
	if len(msg) > 20 && msg[4] == '/' && msg[7] == '/' && msg[10] == ' ' {
		msg = msg[20:] // Skip the "YYYY/MM/DD HH:MM:SS " prefix
	}
	log.Warn(msg)
	return len(p), nil
}

// New will create and configure a new HTTP router
// Write timeout is handled by the timeout middleware
// So it can be set to 0 here to keep SSE open
func New(resources *Resources) *Router {
	router := &Router{Resources: resources}
	router.Handler = router.Configure()
	router.HTTPServer = &http.Server{
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       20 * time.Second,
		WriteTimeout:      0 * time.Second,
		IdleTimeout:       5 * time.Minute,
		Handler:           router.Handler,
		ErrorLog:          stdlog.New(apexLogWriter{}, "", 0),
	}
	return router
}

// Run starts the HTTP server and blocks until it stops or encounters an error.
func (r *Router) Run(lis net.Listener) error {
	middleware.ClientRateLimiterCleanUp() // Start the client rate limiter clean up routine
	if err := r.HTTPServer.Serve(lis); err != nil && !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

// Stop attempts to gracefully shut down the HTTP server.
// If the server does not stop within 10 seconds, it will be forcefully closed.
func (r *Router) Stop() {
	httpServer := r.HTTPServer
	if httpServer != nil {
		// If requests don’t finish within 1 seconds, the context will time out,
		// and the api server will forcefully shut down
		ctx, cancel := context.WithTimeout(context.Background(), 1*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(ctx); err != nil {
			_ = httpServer.Close()
		}
	}
}
