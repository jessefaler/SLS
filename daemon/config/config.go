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

var Path = "/mnt/c/slime/config.yml"

var (
	mutex  sync.RWMutex   // protects concurrent access to the config
	config *Configuration // global singleton configuration
)

type Configuration struct {

	// A unique identifier for this node.
	Uuid string `yaml:"uuid"`

	// Determines if sls should be running in debug mode. This value is ignored
	// if the debug flag is passed through the command line arguments.
	Debug bool `yaml:"debug"`

	Location string `yaml:"location"`

	Name string `default:"SLS" yaml:"name"`

	System SystemConfiguration `yaml:"system"`

	Docker DockerConfiguration `json:"docker" yaml:"docker"`

	Api ApiConfiguration `json:"api" yaml:"api"`

	Allocations Allocations `json:"allocations" yaml:"allocations"`

	Blueprints ResourceConfig `yaml:"blueprints"`
	Worlds     ResourceConfig `yaml:"worlds"`
	Servers    ResourceConfig `yaml:"servers"`
	Content    ResourceConfig `yaml:"content"`

	// Defines messages throttling configurations for server processes.
	Throttles ConsoleThrottles

	// The remote api where the master is running that this daemon should connect too
	// to collect data and send events.
	RemoteApi RemoteApi `json:"-" yaml:"remote"`

	// AllowedOrigins is a list of allowed request origins.
	// protocube URL is automatically allowed, this is only needed for adding
	// additional origins.
	AllowedOrigins []string `json:"allowed_origins" yaml:"allowed_origins"`

	// AllowCORSPrivateNetwork sets the `Access-Control-Request-Private-Network` header which
	// allows client browsers to make requests to messages IP addresses over HTTP.  This setting
	// is only required by users running the daemon without SSL certificates and using messages IP
	// addresses in order to connect. Most users should NOT enable this setting.
	AllowCORSPrivateNetwork bool `json:"allow_cors_private_network" yaml:"allow_cors_private_network"`
}

type RemoteApi struct {
	Url   string `json:"-" yaml:"url"`
	Token string `json:"-" yaml:"token"`
}

type ConsoleThrottles struct {
	// Whether or not the throttler is enabled for this instance.
	Enabled bool `json:"enabled" yaml:"enabled" default:"true"`

	// The total number of lines that can be output in a given period before
	// a warning is triggered and counted against the server.
	Lines uint64 `json:"lines" yaml:"lines" default:"2000"`

	// The amount of time after which the number of lines processed is reset to 0. This runs in
	// a constant loop and is not affected by the current console output volumes. By default, this
	// will reset the processed line count back to 0 every 100ms.
	Period uint64 `json:"line_reset_interval" yaml:"line_reset_interval" default:"100"`
}

type Allocations struct {
	Address string `json:"address"`
	Ports   string `yaml:"ports"`
}

// ResourceConfig represents a generic resource directory
// that SLS manages (blueprint, worlds, servers, plugins).
type ResourceConfig struct {
	// Root is the mounts path where the resources are stored.
	Root string `yaml:"root"`

	// Sync determines whether the resources in this directory
	// should be kept in sync
	Sync bool `yaml:"sync"`
}

// ApiConfiguration defines the configuration for the API server
type ApiConfiguration struct {
	Url string `json:"-" yaml:"url"`

	// The interface that the messages proto should bind to.
	Host string `default:"0.0.0.0" yaml:"host"`

	// The port that the messages proto should bind to.
	Port int `default:"8080" yaml:"port"`

	// TSL configuration for the daemon.
	Tls struct {
		Enabled         bool   `default:"true" yaml:"enabled"`
		CertificateFile string `json:"cert" yaml:"cert"`
		KeyFile         string `json:"key" yaml:"key"`
	}
}

type SystemConfiguration struct {
	RootDirectory string `default:"/var/lib/sls" yaml:"root_directory"`

	LogDirectory string `default:"/var/log/sls" yaml:"log_directory"`

	// AllowedMounts enumerates host paths that can be exposed to containers as additional
	// bind mounts. Custom mounts supplied by servers must live within one of these paths.
	AllowedMounts []string `yaml:"allowed_mounts"`

	Timezone string `yaml:"timezone"`

	// Definitions for the user that gets created to ensure that we can quickly access
	// this information without constantly having to do a system lookup.
	User struct {
		// Rootless controls settings related to rootless container daemons.
		Rootless struct {
			// Enabled controls whether rootless containers are enabled.
			Enabled bool `yaml:"enabled" default:"false"`
			// ContainerUID controls the UID of the user inside the container.
			// This should likely be set to 0 so the container runs as the user
			// running this daemon.
			ContainerUID int `yaml:"container_uid" default:"0"`
			// ContainerGID controls the GID of the user inside the container.
			// This should likely be set to 0 so the container runs as the user
			// running this daemon.
			ContainerGID int `yaml:"container_gid" default:"0"`
		} `yaml:"rootless"`

		Uid int `yaml:"uid"`
		Gid int `yaml:"gid"`
	} `yaml:"user"`
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

	// Override token values with environment variables if present
	if envToken := os.Getenv("SLS_TOKEN"); envToken != "" {
		//config.Api.Token = envToken
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

SLS was not able to locate the configuration file, and therefore is not
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

// Update performs an in-situ update of the global configuration object using
// a thread-safe mutex lock. This is the correct way to make modifications to
// the global configuration.
func Update(callback func(c *Configuration)) {
	mutex.Lock()
	defer mutex.Unlock()
	callback(config)
}
