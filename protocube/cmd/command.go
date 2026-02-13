package cmd

import (
	"fmt"
	log2 "log"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"protoxon.com/sls/protocube/auth"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/internal/database"
	"protoxon.com/sls/protocube/internal/message"
	"protoxon.com/sls/protocube/log"
	"protoxon.com/sls/protocube/system"
)

var (
	debug          = false
	keyType        string
	outputFile     string
	printToConsole bool
)

func init() {
	// Configure command-line flags
	rootCommand.PersistentFlags().StringVar(&config.Path, "config", config.Path, "set the location for the configuration file")
	rootCommand.PersistentFlags().BoolVar(&debug, "debug", false, "pass in order to run sls in debug mode")

	rootCommand.AddCommand(versionCommand)
	rootCommand.AddCommand(generateKeyCommand)

	// Configure generate-key command flags
	generateKeyCommand.Flags().StringVarP(&keyType, "type", "t", "", "Type of key to generate: 'node' or 'application' (required)")
	generateKeyCommand.Flags().StringVarP(&outputFile, "output", "o", "", "File path to write the key to (default: saves to system directory)")
	generateKeyCommand.Flags().BoolVar(&printToConsole, "print", false, "Print key to console (not recommended for security)")
	_ = generateKeyCommand.MarkFlagRequired("type")
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

var generateKeyCommand = &cobra.Command{
	Use:   "generate-key",
	Short: "Generate a new API key (node or application)",
	Long: "Generate a new API key for either a node or application. Use --type flag to specify 'node' or 'application'. " +
		"By default, the key will be written to a file. Use --output to specify the file path, or --print to output to console (not recommended).",
	Run: func(cmd *cobra.Command, args []string) {
		config.InitConfig()

		// Validate key type
		keyTypeLower := strings.ToLower(keyType)
		var tokenType auth.KeyType
		switch keyTypeLower {
		case "node":
			tokenType = auth.Node
		case "application":
			tokenType = auth.Application
		default:
			log2.Fatalf("Key type must be either 'node' or 'application', got: %s", keyType)
		}

		// Initialize the database
		err := database.Initialize()
		if err != nil {
			log2.Fatalf("Failed to initialize database: %v", err)
		}

		// Initialize the token store
		tokenStore, err := auth.LoadAllTokens()
		if err != nil {
			log2.Fatalf("Failed to load token store: %v", err)
		}

		// Generate the new key
		token, err := tokenStore.NewKey(tokenType)
		if err != nil {
			log2.Fatalf("Failed to generate API key: %v", err)
		}

		keyString := token.String()

		// Determine output method
		if outputFile != "" {
			// Write to specified file with restricted permissions (0600 = rw-------)
			absPath, err := filepath.Abs(outputFile)
			if err != nil {
				log2.Fatalf("Failed to resolve output file path: %v", err)
			}

			err = os.WriteFile(absPath, []byte(keyString+"\n"), 0600)
			if err != nil {
				log2.Fatalf("Failed to write key to file: %v", err)
			}

			fmt.Printf("Generated new %s API key and saved to: %s\n", tokenType.String(), absPath)
		} else if printToConsole {
			// Print to console
			fmt.Printf("Generated new %s API key:\n", tokenType.String())
			fmt.Printf("%s\n", keyString)
		} else {
			// Default: write to a file in the system root directory
			keyFileName := fmt.Sprintf("%s_key_%s.txt", strings.ToLower(tokenType.String()), token.Id)
			keyFilePath := filepath.Join(config.Get().System.RootDirectory, keyFileName)

			// Ensure directory exists
			if err := os.MkdirAll(config.Get().System.RootDirectory, 0755); err != nil {
				log2.Fatalf("Failed to create system directory: %v", err)
			}

			err = os.WriteFile(keyFilePath, []byte(keyString+"\n"), 0600)
			if err != nil {
				log2.Fatalf("Failed to write key to file: %v", err)
			}

			fmt.Printf("Generated new %s API key and saved to: %s\n", tokenType.String(), keyFilePath)
		}
	},
}
