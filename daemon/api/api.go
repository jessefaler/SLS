package api

import (
	"crypto/tls"
	"errors"
	"net"
	"strconv"

	"github.com/apex/log"
	"github.com/fatih/color"
	"protoxon.com/sls/daemon/api/router"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/system"
)

type Server interface {
	Run(listener net.Listener) error
	Stop() error
}

type Api struct {
	Listener net.Listener
	Router   *router.Router
}

func New(resources *router.Resources) *Api {
	return &Api{
		Router: router.New(resources),
	}
}

// Run starts the API server
// The server run asynchronously and any errors are logged directly.
// If any server fails to listen the program will exit
func (api *Api) Run() {

	cfg := config.Get().Api
	address := cfg.Host + ":" + strconv.Itoa(cfg.Port)

	tlsOn := true
	if cfg.Tls.Enabled != nil {
		tlsOn = *cfg.Tls.Enabled
	}

	// create a single listener for HTTP
	lis, err := net.Listen("tcp", address)
	if err != nil {
		log.WithField("error", err).Fatal("Failed to create net listener")
	}

	// Wrap listener in TLS if enabled
	if tlsOn {
		tlsCfg := config.GetTLSConfig()
		lis = tls.NewListener(lis, tlsCfg)
	}
	// Set the api listener
	api.Listener = lis

	// Run the HTTP Server
	go func() {
		if err := api.Router.Run(lis); err != nil && !errors.Is(err, net.ErrClosed) {
			log.WithField("error", err).Warn("HTTP server failed")
		}
	}()

	log.Info("Congestion control algorithm: " + system.DefaultTCPCC())
	red := color.New(color.FgHiRed).SprintFunc()
	if !tlsOn {
		log.Warn("TLS disabled")
	}
	log.Info("Listening on " + red(address))
}

// Stop stops the api servers and closes the net listener
func (api *Api) Stop() {
	// Stop the HTTP server
	if api.Router != nil {
		api.Router.Stop()
	}
	// Close the listener
	if api.Listener != nil {
		_ = api.Listener.Close()
	}
}
