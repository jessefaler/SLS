package config

import (
	"crypto/tls"
	"fmt"
	log2 "log"
	"os"
	"path/filepath"
	"sync"

	"emperror.dev/errors"
	"github.com/mitchellh/colorstring"
	"gopkg.in/yaml.v3"
)

var Path = "/sls/protocube/config.yml"

var (
	mutex  sync.RWMutex   // protects concurrent access to the config
	config *Configuration // global singleton configuration
)

type Configuration struct {
	// A unique identifier for this node_old.
	Uuid string `yaml:"uuid"`

	// Determines if sls should be running in debug mode. This value is ignored
	// if the debug flag is passed through the command line arguments.
	Debug bool `yaml:"debug"`

	Api ApiConfiguration `json:"api" yaml:"api"`

	AppName string `default:"SLS" yaml:"app_name"`

	PluginsDir string `yaml:"plugins-folder"`

	System SystemConfiguration `yaml:"system"`

	Blueprints ResourceConfig `yaml:"blueprints"`

	Software ResourceConfig `yaml:"software"`

	// AllowedOrigins is a list of allowed request origins.
	AllowedOrigins []string `json:"allowed_origins" yaml:"allowed_origins"`

	// AllowCORSPrivateNetwork sets the `Access-Control-Request-Private-Network` header which
	// allows client browsers to make requests to internal IP addresses over HTTP.
	AllowCORSPrivateNetwork bool `json:"allow_cors_private_network" yaml:"allow_cors_private_network"`
}

// ApiConfiguration defines the configuration for the API server
type ApiConfiguration struct {

	// The interface that the internal proto should bind to.
	Host string `default:"0.0.0.0" yaml:"host"`

	// The port that the internal proto should bind to.
	Port int `default:"8080" yaml:"port"`

	// TSL configuration for the daemon.
	Tls struct {
		Enabled         bool   `default:"true" yaml:"enabled"`
		CertificateFile string `json:"cert" yaml:"cert"`
		KeyFile         string `json:"key" yaml:"key"`
	}
}

// ResourceConfig represents a generic resource directory
// that SLS manages (blueprint, worlds, servers, plugins).
type ResourceConfig struct {
	// Root is the mounts path where the resources are stored.
	Root string `yaml:"root"`
}

type SystemConfiguration struct {
	RootDirectory string `default:"/var/lib/protocube" yaml:"root_directory"`
	LogDirectory  string `default:"/var/log/protocube" yaml:"log_directory"`
}

// InitConfig Reads the configuration from the disk and then sets up the global singleton
// with all the configuration values.
func InitConfig() {
	var configPath = Path
	if !filepath.IsAbs(configPath) {
		absolutePath, err := filepath.Abs(configPath)
		if err != nil {
			log2.Fatalf("config/config: failed to get path to config file: %s", err)
		}
		configPath = absolutePath
	}

	err := loadConfigFromFile(configPath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			exitWithConfigurationNotice()
		}
		log2.Fatalf("config/config: error while reading configuration file: %s", err)
	}
}

// LoadConfigFromFile reads the configuration from the provided file and stores it in the
// global singleton for this instance.
func loadConfigFromFile(path string) error {
	bytes, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	var config Configuration

	// Decode the contents of the yml into the config struct
	if err := yaml.Unmarshal(bytes, &config); err != nil {
		return err
	}

	// Store this configuration in the global state.
	set(&config)
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

func exitWithConfigurationNotice() {
	fmt.Printf(colorstring.Color(`
[_red_][white][bold]Error: Configuration File Not Found[reset]

Protocube was not able to locate the configuration file, and therefore is not
able to complete its boot process. Please ensure the configuration file 
exists at the location below.

Location: %s[reset]

`), Path)
	os.Exit(1)
}

// GetTLSConfig Configures the TSL config and certificates for
// the api server
func GetTLSConfig() *tls.Config {
	return &tls.Config{
		MinVersion: tls.VersionTLS13,
	}
}
