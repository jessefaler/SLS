package config

import (
	"crypto/tls"
	_ "embed"
	"fmt"
	"os"
	"path/filepath"
	"sync"

	"github.com/apex/log"
	"github.com/mitchellh/colorstring"
	"gopkg.in/yaml.v3"
)

var Path = "/etc/sls/protocube/config.yml"

//go:embed config.yml
var defaultConfig []byte

var (
	mutex  sync.RWMutex   // protects concurrent access to the config
	config *Configuration // global singleton configuration
)

type Configuration struct {

	// Determines if sls should be running in debug mode. This value is ignored
	// if the debug flag is passed through the command line arguments.
	Debug bool `yaml:"debug"`

	Api ApiConfiguration `json:"api" yaml:"api"`

	AppName string `default:"SLS" yaml:"app_name"`

	System SystemConfiguration `yaml:"system"`

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

type SystemConfiguration struct {
	RootDirectory string `default:"/var/lib/protocube" yaml:"root_directory"`
	LogDirectory  string `default:"/var/log/protocube" yaml:"log_directory"`

	// Directory where state volumes are stored
	Volumes string `default:"/var/lib/sls/volumes" json:"-" yaml:"volumes"`
	// Directory where software config files are stored
	Software string `default:"/var/lib/sls/software" json:"-" yaml:"software"`
	// Directory where blueprints are stored
	Blueprints string `default:"/var/lib/sls/blueprints" json:"-" yaml:"blueprints"`
	// Directory where Protocube plugins are stored
	Plugins string `default:"/var/lib/sls/plugins" json:"-" yaml:"plugins"`
}

// InitConfig Reads the configuration from the disk and then sets up the global singleton
// with all the configuration values.
func InitConfig() {
	var configPath = Path
	if !filepath.IsAbs(configPath) {
		absolutePath, err := filepath.Abs(configPath)
		if err != nil {
			log.Fatalf("config/config: failed to get path to config file: %s", err)
		}
		configPath = absolutePath
	}

	// If config file doesn't exist, create it from embedded default
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		err := writeDefaultConfig(configPath)
		if err != nil {
			exitWithConfigurationNotice()
		}
		fmt.Printf(colorstring.Color(" [blue][bold]Created default config at: [reset]%s[reset]\n\n"), configPath)
	}

	// Load the config from disk
	err := loadConfigFromFile(configPath)
	if err != nil {
		log.Fatalf("config/config: error while reading configuration file: %s", err)
	}

	if err = ConfigureDirectories(); err != nil {
		log.Errorf("config/config: failed to configure directories: %s", err)
	}
}

// ConfigureDirectories ensures that all the system directories exist on the
// system. These directories are created so that only the owner can read the data,
// and no other users.
//
// This function IS NOT thread-safe.
func ConfigureDirectories() error {
	root := config.System.RootDirectory
	log.WithField("path", root).Debug("ensuring root data directory exists")
	if err := os.MkdirAll(root, 0o700); err != nil {
		return err
	}

	log.WithField("path", config.System.Plugins).Debug("ensuring plugins directory exists")
	if err := os.MkdirAll(config.System.Plugins, 0o700); err != nil {
		return err
	}

	log.WithField("path", config.System.Blueprints).Debug("ensuring blueprints directory exists")
	if err := os.MkdirAll(config.System.Blueprints, 0o700); err != nil {
		return err
	}

	log.WithField("path", config.System.Software).Debug("ensuring software directory exists")
	if err := os.MkdirAll(config.System.Software, 0o700); err != nil {
		return err
	}

	log.WithField("path", config.System.Volumes).Debug("ensuring volumes directory exists")
	if err := os.MkdirAll(config.System.Volumes, 0o700); err != nil {
		return err
	}

	return nil
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

func writeDefaultConfig(path string) error {
	// Ensure parent directory exists
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}

	if err := os.WriteFile(path, defaultConfig, 0o644); err != nil {
		return err
	}

	var embedded Configuration
	if err := yaml.Unmarshal(defaultConfig, &embedded); err != nil {
		return err
	}
	softwareDir := filepath.Clean(embedded.System.Software)
	if softwareDir == "" || softwareDir == "." {
		return nil
	}
	if err := os.MkdirAll(softwareDir, 0o700); err != nil {
		return err
	}
	// Only runs when the main config was just created (see InitConfig).
	syncDefaultSoftwareYAMLs(softwareDir)
	return nil
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
