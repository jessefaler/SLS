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
	"protoxon.com/sls/protocube/api"
	"protoxon.com/sls/protocube/api/router"
	"protoxon.com/sls/protocube/auth"
	"protoxon.com/sls/protocube/balancer"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/client"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/internal/database"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/plugins"
	"protoxon.com/sls/protocube/server"
	"protoxon.com/sls/protocube/software"
	"protoxon.com/sls/protocube/telemetry"
)

func Execute() {
	if err := rootCommand.Execute(); err != nil {
		log2.Fatalf("failed to execute command: %s", err)
	}
}

func run(cmd *cobra.Command, _ []string) {
	log.Debug("running in debug mode")
	log.WithField("config_file", config.Path).Info("loading configuration from file")

	// Initialize the internal sqlite database
	err := database.Initialize()
	if err != nil {
		log.WithError(err).Fatal("failed to initialize database")
	}

	// Create the load balancer provider with the default load balancer
	loadBalancer := balancer.NewProvider(balancer.NewRoundRobin())
	// Create the remote client
	remoteClient := client.New()
	// Create the node manager
	nodeManager := node.NewManager(remoteClient, loadBalancer)
	// Create the server manager
	serverManager, err := server.NewManager(nodeManager)
	if err != nil {
		log.Fatal(err.Error())
	}

	// Wire up node connection callbacks to attach/detach node clients on servers
	nodeManager.SetOnNodeRegistered(func(nodeId string, node *node.Node) {
		serverManager.AttachNodeClientToServers(nodeId, node)
	})
	nodeManager.SetOnNodeDisconnected(func(nodeId string) {
		serverManager.DetachNodeClientFromServers(nodeId)
	})

	// Initialize the api key service
	keyService := auth.NewKeyService()

	// =========================================================
	// Initialize the software configurations
	// =========================================================
	softwareRegistry := software.NewRegistry()
	sw, err := software.LoadAllSoftware(config.Get().System.Software)
	if err != nil {
		log.WithError(err).Fatal("failed to load software configurations")
	}
	softwareRegistry.RegisterAll(sw)
	log.WithField("root", config.Get().System.Software).Infof("Loaded %d software configurations.", len(sw))

	// =========================================================
	// Initialize blueprint and mixin registries
	// =========================================================
	mixinRegistry := blueprint.NewMixinRegistry()
	blueprintRegistry := blueprint.NewBlueprintRegistry()
	loaded, err := blueprint.SyncAndLoadConfigured(false, softwareRegistry)
	if err != nil {
		log.WithError(err).Fatal("failed to load blueprints")
	}
	mixinRegistry.RegisterAll(loaded.Mixins)
	blueprintRegistry.RegisterAll(loaded.Blueprints)
	log.WithField("root", config.Get().System.Blueprints).
		Infof("Initialized registries. Loaded %d blueprints and %d mixins", len(loaded.Blueprints), len(loaded.Mixins))

	// =========================================================
	// Configure the api
	// =========================================================
	apiInstance := api.New(&router.Resources{
		ServerManager:     serverManager,
		LoadBalancer:      loadBalancer,
		BlueprintRegistry: blueprintRegistry,
		MixinRegistry:     mixinRegistry,
		SoftwareRegistry:  softwareRegistry,
		NodeManager:       nodeManager,
		Client:            remoteClient,
		KeyService:        keyService,
	})

	// =========================================================
	// Load Plugins
	// =========================================================
	err = plugins.LoadPlugins(&plugins.SLS{
		BlueprintRegistry: blueprintRegistry,
		Router:            apiInstance.Router.Handler,
		PluginsDir:        config.Get().System.Plugins,
	})
	if err != nil {
		log.Error("Failed to load plugins")
	}

	var telemetryStop context.CancelFunc
	if ResolveTelemetryEnabled(cmd) {
		var telemetryCtx context.Context
		telemetryCtx, telemetryStop = context.WithCancel(context.Background())
		telemetry.Start(telemetryCtx, serverManager, nodeManager, 5*time.Minute)
	} else {
		log.Info("Outbound telemetry is disabled.")
	}

	apiInstance.Run() // Start the API server
	handleShutdown(apiInstance, telemetryStop)
}

func handleShutdown(apiInstance *api.Api, telemetryStop context.CancelFunc) {
	// Create a channel to receive OS signals
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM, syscall.SIGHUP)
	<-sigChan // Wait for termination

	log.Info("Shutting down.")

	if telemetryStop != nil {
		telemetryStop()
	}

	// Disable plugins
	plugins.DisableAll()

	// Stop the api servers
	apiInstance.Stop()
}
