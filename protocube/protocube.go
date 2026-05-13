package main

import (
	"github.com/fatih/color"
	"protoxon.com/sls/protocube/cmd"
	"protoxon.com/sls/protocube/system"
)

func main() {
	// Execute the main binary code.
	cmd.Execute()
}

func init() {
	// Bypasses the check for non-tty output streams. While in development
	// noinspection GoBoolExpressions
	if system.Version == "develop" {
		color.NoColor = false
	}
}
