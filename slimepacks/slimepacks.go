package main

import (
	"SlimePacks/config"
	"SlimePacks/log"
	"SlimePacks/router"
	"SlimePacks/slimepack"
	"SlimePacks/updater"
	"fmt"
	"path/filepath"

	"emperror.dev/errors"
	"github.com/mitchellh/colorstring"
	"protoxon.com/sls/protocube/plugins"
)

// SlimePacks is a resource pack management plugin for Protocube.
// It reads resource-pack directories defined in blueprint annotations
// and converts them to requested Minecraft versions using:
// https://github.com/agentdid127/ResourcePackConverter
//
// The plugin exposes an HTTP API:
//
//   GET /slimepack
//       Lists all available resource packs.
//
//   GET /slimepack/:resourcepack
//       - Without query parameters: returns the newest version as a direct file download.
//       - With query parameters (e.g., ?version=1.20, ?v=1.20): returns the matching
//         converted resource pack as a direct file download.
//
//    Example url for fetching version 1.20 of a resource pack from the blueprint combat_cube
//        - https://slimelabs.net/api/slimepacks/combat_cube?=version=1.20

// Building:
// go build -buildmode=plugin

type SlimePacks struct{}

var Plugin SlimePacks // Entrypoint

const (
	name        = "SlimePacks"
	version     = "1.0.0"
	authors     = "Protoxon"
	description = "Resource pack management plugin"
)

func (SlimePacks) Name() string        { return name }
func (SlimePacks) Version() string     { return version }
func (SlimePacks) Authors() string     { return authors }
func (SlimePacks) Description() string { return description }

func (SlimePacks) OnEnable(sls *plugins.SLS) error {
	log.SetLogger(sls.Logger)
	banner("startup")
	// Load the slimepack configuration file
	config.InitConfig(sls.PluginsDir, name)
	// Check for updates and update the converter
	updater.Update(filepath.Join(sls.PluginsDir, name))
	// Create the slimepack manager
	spm, err := slimepack.NewManager()
	if err != nil {
		return errors.Wrap(err, "failed to initialize slimepack manager")
	}
	// Load in all the packs
	spm.LoadAllPacks(sls.BlueprintRegistry)
	// Register the slimepack routes
	router.RegisterRoutes(sls.Router, spm)
	return nil
}

func (SlimePacks) OnDisable() error {
	banner("shutdown")
	return nil
}

func banner(messageType string) {
	if messageType == "shutdown" {
		// Shutdown message
		logo := fmt.Sprintf(`[green]%s [yellow]v%s[blue] by [magenta]%s[blue] dissabled`, name, version, authors)
		log.Info(colorstring.Color(logo))
	} else if messageType == "startup" {
		// Start message
		logo := fmt.Sprintf(`[green]%s [yellow]v%s[blue] by [magenta]%s[blue] enabled`, name, version, authors)
		log.Info(colorstring.Color(logo))
	}
}
