package cmd

import (
	"github.com/spf13/cobra"
)

var pullCmd = &cobra.Command{
	Use:   "pull VOLUME[:TAG] [PATH]",
	Short: "Pull a volume artifact from a registry",
	RunE: func(cmd *cobra.Command, args []string) error {
		return nil
	},
}

func init() {
	rootCmd.AddCommand(pullCmd)
}
