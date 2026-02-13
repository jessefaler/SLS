package cmd

import (
	"context"
	log2 "log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/apex/log"
	"github.com/spf13/cobra"
	"protoxon.com/sls/daemon/api"
	"protoxon.com/sls/daemon/api/auth"
	"protoxon.com/sls/daemon/api/router"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/internal/database"
	"protoxon.com/sls/daemon/internal/message"
	"protoxon.com/sls/daemon/remote"
	"protoxon.com/sls/daemon/server"
	"protoxon.com/sls/daemon/system"
)

func Execute() {
	message.Start()
	if err := rootCommand.Execute(); err != nil {
		log2.Fatalf("failed to execute command: %s", err)
	}
}

func run(cmd *cobra.Command, _ []string) {
	// The config and logger are initialized in command.go

	// Ensure the program is running with sufficient capabilities.
	// CAP_SYS_ADMIN is required to perform mount operations.
	admin, err := system.HasCapSysAdmin()
	if err != nil {
		log.Fatalf("Failed to check CAP_SYS_ADMIN capability: %v", err)
	}
	if !admin {
		log.Fatal("Insufficient privileges: this program requires the CAP_SYS_ADMIN capability to run.")
	}

	log.Debug("running in debug mode")
	log.WithField("config_file", config.Path).Info("loading configuration from file")
	cfg := config.Get()

	// =========================================================
	// create a client for making requests to the remote api
	// =========================================================
	client := remote.New(
		cfg.RemoteApi.Url,
		remote.WithCredentials(cfg.RemoteApi.Token),
	)

	// create a server manager instance
	manager := server.NewManager(client)

	// Register the on connected callback
	// This is called when the node successfully connects to Protocube
	// When connected sync server configurations
	client.SetOnConnected(func(ctx context.Context) {
		err := manager.Sync(cmd.Context())
		if err != nil {
			log.WithField("error", err).Fatal("failed to load server configurations")
			return
		}
	})

	// Initialize the sqlite database
	err = database.Initialize()
	if err != nil {
		log.WithError(err).Fatal("failed to initialize database")
	}

	// Ensure the Docker network (e.g. sls_nw) exists before starting containers.
	if err := environment.ConfigureDocker(cmd.Context()); err != nil {
		log.WithError(err).Fatal("failed to configure docker environment")
	}

	// =========================================================
	// Configure and run the api
	// =========================================================
	apiInstance := api.New(&router.Resources{
		ServerManager: manager,
		VerifyToken:   auth.Verify,
	})
	apiInstance.Run()

	ticker := time.NewTicker(time.Minute)
	// Every minute, write the current server states to the disk to allow for a more
	// seamless hard-reboot process in which the daemon will re-sync server states based
	// on its last tracked state.
	// Only servers with saving enabled have their states saved
	go func() {
		for {
			select {
			case <-ticker.C:
				if err := manager.PersistStates(); err != nil {
					log.WithField("error", err).Warn("failed to persist server states to disk")
				}
			case <-cmd.Context().Done():
				ticker.Stop()
				return
			}
		}
	}()

	handleShutdown(apiInstance, client)
}

func handleShutdown(apiInstance *api.Api, client remote.Client) {
	// create a channel to receive OS signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM, syscall.SIGHUP)
	<-sigChan // Wait for termination

	log.Info("Shutting down.")

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	// Send a disconnect request to protocube
	client.Disconnect(ctx)

	// Stop the api servers
	apiInstance.Stop()
}
