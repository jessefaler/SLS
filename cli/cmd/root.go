package cmd

import (
	log2 "log"

	"github.com/spf13/cobra"
)

var rootCmd = &cobra.Command{
	Use:   "sls",
	Short: "SLS CLI",
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		log2.Fatalf("failed to execute command: %s", err)
	}
}
