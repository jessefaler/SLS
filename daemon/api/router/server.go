package router

import (
	"context"
	"net"
	"net/http"
	"time"

	"emperror.dev/errors"
)

type Router struct {
	*Resources
	HTTPServer *http.Server
}

// New will create and configure a new HTTP router
func New(resources *Resources) *Router {
	router := &Router{Resources: resources}
	router.HTTPServer = &http.Server{
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       5 * time.Minute,
		Handler:           router.Configure(),
	}
	return router
}

// Run starts the HTTP server and blocks until it stops or encounters an error.
func (r *Router) Run(lis net.Listener) error {
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
		// If requests don’t finish within 10 seconds, the context will time out,
		// and the api server will forcefully shut down
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(ctx); err != nil {
			_ = httpServer.Close()
		}
	}
}
