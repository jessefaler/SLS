package cmd

import (
	log2 "log"
	"os"
	"os/signal"
	"syscall"

	"github.com/apex/log"
	"github.com/spf13/cobra"
	"protoxon.com/sls/protocube/api"
	"protoxon.com/sls/protocube/api/router"
	"protoxon.com/sls/protocube/auth"
	"protoxon.com/sls/protocube/balancer"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/internal/database"
	"protoxon.com/sls/protocube/internal/message"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/plugins"
	"protoxon.com/sls/protocube/server"
	"protoxon.com/sls/protocube/software"
)

func Execute() {
	message.Start()
	if err := rootCommand.Execute(); err != nil {
		log2.Fatalf("failed to execute command: %s", err)
	}
}

func run(cmd *cobra.Command, _ []string) {
	log.Debug("running in debug mode")
	log.WithField("config_file", config.Path).Info("loading configuration from file")

	// Create the server manager
	serverManager := server.NewManager()
	// Create the load balancer
	loadBalancer := balancer.NewRoundRobin()
	// Create the remote client
	remoteClient := client.New()
	// Create the node manager
	nodeManager := node.NewManager(remoteClient, loadBalancer)

	// Initialize the internal sqlite database
	err := database.Initialize()
	if err != nil {
		log.WithError(err).Fatal("failed to initialize database")
	}

	// Initialize the token store
	tokenStore, err := auth.LoadAllTokens()
	if err != nil {
		log.WithError(err).Fatal("Failed to load token store.")
	}

	// =========================================================
	// Initialize the software configurations
	// =========================================================
	softwareRegistry := software.NewRegistry()
	sw, err := software.LoadAllSoftware(config.Get().Software.Root)
	if err != nil {
		log.WithError(err).Fatal("failed to load software configurations")
	}
	softwareRegistry.RegisterAll(sw)
	log.WithField("root", config.Get().Software.Root).Infof("Loaded %d software configurations.", len(sw))

	// =========================================================
	// Initialize the blueprint registry
	// =========================================================
	blueprintRegistry := blueprint.NewRegistry()
	blueprints, err := blueprint.LoadAllBlueprints(config.Get().Blueprints.Root, softwareRegistry)
	if err != nil {
		log.WithError(err).Fatal("failed to load blueprints")
	}
	blueprintRegistry.RegisterAll(blueprints)
	log.WithField("root", config.Get().Blueprints.Root).Infof("Initialized blueprint registry. Loaded %d blueprints", len(blueprints))

	// =========================================================
	// Configure the api
	// =========================================================
	apiInstance := api.New(&router.Resources{
		ServerManager:     serverManager,
		LoadBalancer:      loadBalancer,
		BlueprintRegistry: blueprintRegistry,
		SoftwareRegistry:  softwareRegistry,
		NodeManager:       nodeManager,
		Client:            remoteClient,
		VerifyToken:       tokenStore.Verify,
	})

	// =========================================================
	// Load Plugins
	// =========================================================
	err = plugins.LoadPlugins(&plugins.SLS{
		BlueprintRegistry: blueprintRegistry,
		Router:            apiInstance.Router.Handler,
		PluginsDir:        config.Get().PluginsDir,
	})
	if err != nil {
		log.Error("Failed to load plugins")
	}

	apiInstance.Run() // Start the API server
	handleShutdown(apiInstance)
}

func handleShutdown(apiInstance *api.Api) {
	// Create a channel to receive OS signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM, syscall.SIGHUP)
	<-sigChan // Wait for termination

	log.Info("Shutting down.")

	// Disable plugins
	plugins.DisableAll()

	// Stop the api servers
	apiInstance.Stop()
}
