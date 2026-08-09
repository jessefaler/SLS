package plugins

import (
	"os"
	"path/filepath"
	"plugin"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/balancer"
	"protoxon.com/sls/protocube/blueprint"
)

// PROTOCUBE PLUGIN API

var loadedPlugins []Plugin

// SLS is the plugin context that gets passes to plugins for accessing data from protocube
type SLS struct {
	Logger            *log.Entry
	BlueprintRegistry *blueprint.BlueprintRegistry
	Router            *gin.Engine
	PluginsDir        string
	LoadBalancer      *balancer.Provider
}

type Plugin interface {
	Name() string
	Authors() string
	Description() string
	Version() string

	OnEnable(sls *SLS) error
	OnDisable() error
}

func LoadPlugins(sls *SLS) error {
	pluginDir := sls.PluginsDir
	err := filepath.Walk(pluginDir, func(path string, info os.FileInfo, walkErr error) error {
		if filepath.Ext(path) != ".so" {
			return nil
		}

		p, err := plugin.Open(path)
		if err != nil {
			log.WithField("error", err.Error()).Errorf("Error loading plugin %s", filepath.Base(path))
			return nil
		}

		sym, err := p.Lookup("Plugin")
		if err != nil {
			log.Errorf("Failed to load plugin %s missing entrypoint: %s", filepath.Base(path), err)
			return nil
		}

		plg, ok := sym.(Plugin)
		if !ok {
			log.Errorf("Failed to load plugin %s plugin does not implement plugin interface", filepath.Base(path))
			return nil
		}

		// Save plugin instance
		loadedPlugins = append(loadedPlugins, plg)

		log.Infof("Loaded plugin %s v%s by %s", plg.Name(), plg.Version(), plg.Authors())

		// Set the log field so plugin logs will include the plugin name [{name}]
		sls.Logger = log.WithField("plugin", plg.Name())
		err = plg.OnEnable(sls)
		if err != nil {
			log.WithField("error", err).Errorf("Error loading plugin %s", plg.Name())
		}

		return nil
	})
	return err
}

func DisableAll() {
	for _, plg := range loadedPlugins {
		log.Infof("Disabling plugin %s", plg.Name())
		err := plg.OnDisable()
		if err != nil {
			log.WithError(err).Errorf("Error disabling plugin %s", plg.Name())
		}
	}
}
