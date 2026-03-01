package config

import (
	"os"
	"path/filepath"
	"protoxon.com/sls/slimepacks/log"
	"sync"

	"emperror.dev/errors"
	"github.com/creasty/defaults"
	"gopkg.in/yaml.v3"
)

var (
	mutex  sync.RWMutex   // protects concurrent access to the config
	config *Configuration // global singleton configuration
)

type Configuration struct {
	ResourcePacksRoot string `yaml:"root-folder"`        // The path to the root of the folder that holds all resource packs
	ConversionsFolder string `yaml:"conversions-folder"` // the path to the folder that will hold converted resource packs
	AutoUpdate        bool   `yaml:"auto-update"`        // Weather to auto update the converter jar or not
	ConverterJar      string `yaml:"converter-jar"`      // The path to the converter jar
}

// InitConfig Reads the configuration from the disk and then sets up the global singleton
// with all the configuration values.
func InitConfig(pluginsDir string, pluginName string) {
	configFolder := filepath.Join(pluginsDir, pluginName)
	configFile := filepath.Join(configFolder, "config.yaml")
	if err := os.MkdirAll(filepath.Join(pluginsDir, pluginName), 0755); err != nil {
		log.Errorf("Failed to create config directory: %v", err)
	}
	if !filepath.IsAbs(configFile) {
		absolutePath, err := filepath.Abs(configFile)
		if err != nil {
			log.Errorf("config/config: failed to get path to config file: %s", err)
		}
		configFile = absolutePath
	}
	err := loadConfigFromFile(configFolder)
	if err != nil {
		log.Errorf("config/config: error while reading configuration file: %s", err)
	}
}

// LoadConfigFromFile reads the configuration from the provided file and stores it in the
// global singleton for this instance.
func loadConfigFromFile(configFolder string) error {
	// Create the config at the path if it doesn't exist
	configFile := filepath.Join(configFolder, "config.yaml")
	if _, err := os.Stat(configFile); os.IsNotExist(err) {
		if err := CreateDefaultConfig(configFolder); err != nil {
			return err
		}
	}
	bytes, err := os.ReadFile(configFile)
	if err != nil {
		return err
	}
	var config Configuration
	if err := defaults.Set(&config); err != nil {
		return errors.Wrap(err, "failed to set config default values")
	}

	// Decode the contents of the yml into the config struct
	if err := yaml.Unmarshal(bytes, &config); err != nil {
		return err
	}

	// Store this configuration in the global state.
	set(&config)
	return nil
}

func CreateDefaultConfig(configFolder string) error {
	// Ensure directory exists
	if err := os.MkdirAll(filepath.Dir(configFolder), 0755); err != nil {
		return errors.Wrapf(err, "failed creating config directory for path %s", configFolder)
	}

	cfg := &Configuration{
		// Defaults
		ResourcePacksRoot: filepath.Join(configFolder, "resource_packs"),
		ConversionsFolder: filepath.Join(configFolder, "conversions"),
		ConverterJar:      filepath.Join(configFolder, "ResourcePackConverter.jar"),
		AutoUpdate:        true,
	}

	// Apply default struct tags
	if err := defaults.Set(cfg); err != nil {
		return errors.Wrap(err, "failed applying defaults to config struct")
	}

	// Convert to YAML
	out, err := yaml.Marshal(cfg)
	if err != nil {
		return errors.Wrap(err, "failed marshalling config struct to YAML")
	}

	// Write file
	configFile := filepath.Join(configFolder, "config.yaml")
	if err := os.WriteFile(configFile, out, 0644); err != nil {
		return errors.Wrapf(err, "failed writing default config file to %s", configFile)
	}

	return nil
}

// Set the global configuration instance. This is a blocking operation such that
// anything trying to set a different configuration value, or read the configuration
// will be paused until it is complete.
func set(configuration *Configuration) {
	mutex.Lock()
	defer mutex.Unlock()
	config = configuration
}

// Get returns the global configuration instance.
func Get() *Configuration {
	return config
}
