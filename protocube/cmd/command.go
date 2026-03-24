package cmd

import (
	"fmt"
	"time"

	"github.com/spf13/cobra"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/internal/message"
	"protoxon.com/sls/protocube/log"
	"protoxon.com/sls/protocube/system"
)

var (
	debug = false
)

func init() {
	// Configure command-line flags
	rootCommand.PersistentFlags().StringVar(&config.Path, "config", config.Path, "set the location for the configuration file")
	rootCommand.PersistentFlags().BoolVar(&debug, "debug", false, "pass in order to run sls in debug mode")

	rootCommand.AddCommand(versionCommand)
}

// initEnvironment sets up the config and logging
func initEnvironment(cmd *cobra.Command) {
	config.InitConfig()

	// Override the config Debug if the debug flag was set
	if cmd.Flags().Changed("debug") {
		config.Get().Debug = debug
	}

	log.InitLogging()
}

var rootCommand = &cobra.Command{
	Use:   "Protocube",
	Short: "Runs Protocube master control program.",
	PreRun: func(cmd *cobra.Command, args []string) {
		message.Start()
		initEnvironment(cmd)
	},
	Run: run,
}

var versionCommand = &cobra.Command{
	Use:   "version",
	Short: "Prints the current executable version and exits.",
	Run: func(cmd *cobra.Command, _ []string) {
		fmt.Printf("SLS v%s\nCopyright © 2025 - %d %s\n", system.Version, time.Now().Year(), system.Authors)
	},
}
