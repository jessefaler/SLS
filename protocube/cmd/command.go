package cmd

import (
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/spf13/cobra"
	apexlog "github.com/apex/log"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/internal/message"
	protolog "protoxon.com/sls/protocube/log"
	"protoxon.com/sls/protocube/system"
)

var (
	debug            = false
	disableTelemetry = false
)

func init() {
	// Configure command-line flags
	rootCommand.PersistentFlags().StringVar(&config.Path, "config", config.Path, "set the location for the configuration file")
	rootCommand.PersistentFlags().BoolVar(&debug, "debug", false, "pass in order to run sls in debug mode")
	rootCommand.PersistentFlags().BoolVar(&disableTelemetry, "disable-telemetry", false, "disable outbound telemetry (overrides config and SLS_PROTOCUBE_TELEMETRY_ENABLED)")

	rootCommand.AddCommand(versionCommand)
}

// initEnvironment sets up the config and logging
func initEnvironment(cmd *cobra.Command) {
	config.InitConfig()

	// Override the config Debug if the debug flag was set
	if cmd.Flags().Changed("debug") {
		config.Get().Debug = debug
	}

	protolog.InitLogging()
}

// ResolveTelemetryEnabled returns whether telemetry should run.
// Precedence: --disable-telemetry > SLS_PROTOCUBE_TELEMETRY_ENABLED (true or false) > config telemetry_enabled (default true).
func ResolveTelemetryEnabled(cmd *cobra.Command) bool {
	if cmd.Flags().Changed("disable-telemetry") {
		return !disableTelemetry
	}
	if v, ok := os.LookupEnv("SLS_PROTOCUBE_TELEMETRY_ENABLED"); ok {
		if b, ok := parseTelemetryEnvBool(v); ok {
			return b
		}
		apexlog.WithField("value", v).Warn("invalid SLS_PROTOCUBE_TELEMETRY_ENABLED; falling back to config")
	}
	return config.Get().IsTelemetryEnabled()
}

func parseTelemetryEnvBool(s string) (bool, bool) {
	s = strings.TrimSpace(s)
	switch {
	case strings.EqualFold(s, "true"):
		return true, true
	case strings.EqualFold(s, "false"):
		return false, true
	default:
		return false, false
	}
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
