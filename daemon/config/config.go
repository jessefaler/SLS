package config

import (
	"crypto/tls"
	"fmt"
	log2 "log"
	"os"
	"path"
	"path/filepath"
	"sync"
	"sync/atomic"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/mitchellh/colorstring"
	"golang.org/x/sys/unix"
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
	RemoteApi   RemoteApi                `json:"-" yaml:"remote"`
	RemoteQuery RemoteQueryConfiguration `json:"remote_query" yaml:"remote_query"`

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

type Backups struct {
	// WriteLimit imposes a Disk I/O write limit on backups to the disk, this affects all
	// backup drivers as the archiver must first write the file to the disk in order to
	// upload it to any external storage provider.
	//
	// If the value is less than 1, the write speed is unlimited,
	// if the value is greater than 0, the write speed is the value in MiB/s.
	//
	// Defaults to 0 (unlimited)
	WriteLimit int `default:"0" yaml:"write_limit"`

	// CompressionLevel determines how much backups created by wings should be compressed.
	//
	// "none" -> no compression will be applied
	// "best_speed" -> uses gzip level 1 for fast speed
	// "best_compression" -> uses gzip level 9 for minimal disk space useage
	//
	// Defaults to "best_speed" (level 1)
	CompressionLevel string `default:"best_speed" yaml:"compression_level"`
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

// GetStatesPath returns the location of the JSON file that tracks server states.
func (sc *SystemConfiguration) GetStatesPath() string {
	return path.Join(sc.RootDirectory, "/states.json")
}

type SystemConfiguration struct {
	RootDirectory string `default:"/var/lib/sls" yaml:"root_directory"`

	LogDirectory string `default:"/var/log/sls" yaml:"log_directory"`

	// AllowedMounts enumerates host paths that can be exposed to containers as additional
	// bind mounts. Custom mounts supplied by servers must live within one of these paths.
	AllowedMounts []string `yaml:"allowed_mounts"`

	Timezone string `yaml:"timezone"`

	// The amount of time in seconds that can elapse before a server's disk space calculation is
	// considered stale and a re-check should occur. DANGER: setting this value too low can seriously
	// impact system performance and cause massive I/O bottlenecks and high CPU usage for the Wings
	// process.
	//
	// Set to 0 to disable disk checking entirely. This will always return 0 for the disk space used
	// by a server and should only be set in extreme scenarios where performance is critical and
	// disk usage is not a concern.
	DiskCheckInterval int64 `default:"150" yaml:"disk_check_interval"`

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

		Uid int `default:"988" yaml:"uid"`
		Gid int `default:"988" yaml:"gid"`
	} `yaml:"user"`

	Backups Backups `yaml:"backups"`

	OpenatMode string `default:"auto" yaml:"openat_mode"`

	// Directory where the server data is stored at.
	Data string `default:"/var/lib/sls/volumes" json:"-" yaml:"data"`
}

var (
	openat2    atomic.Bool
	openat2Set atomic.Bool
)

func UseOpenat2() bool {
	if openat2Set.Load() {
		return openat2.Load()
	}
	defer openat2Set.Store(true)

	c := Get()
	openatMode := c.System.OpenatMode
	switch openatMode {
	case "openat2":
		openat2.Store(true)
		return true
	case "openat":
		openat2.Store(false)
		return false
	default:
		fd, err := unix.Openat2(unix.AT_FDCWD, "/", &unix.OpenHow{})
		if err != nil {
			log.WithError(err).Warn("error occurred while checking for openat2 support, falling back to openat")
			openat2.Store(false)
			return false
		}
		_ = unix.Close(fd)
		openat2.Store(true)
		return true
	}
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

// RemoteQueryConfiguration defines the configuration settings for remote requests
// from the daemon1 to the Protocube.
type RemoteQueryConfiguration struct {
	// The amount of time in seconds that the daemon should allow for a request to the Protocube API
	// to complete. If this time passes the request will be marked as failed. If your requests
	// are taking longer than 30 seconds to complete it is likely a performance issue that
	// should be resolved on Protocube, and not something that should be resolved by upping this
	// number.
	Timeout int `default:"30" yaml:"timeout"`

	// The number of servers to load in a single request to protocube API when booting the
	// Daemon instance. A single request is initially made to Protocube to get this number
	// of servers, and then the pagination status is checked and additional requests are
	// fired off in parallel to request the remaining pages.
	//
	// It is not recommended to change this from the default as you will likely encounter
	// memory limits on your Protocube instance. In the grand scheme of things 4 requests for
	// 50 servers is likely just as quick as two for 100 or one for 400, and will certainly
	// be less likely to cause performance issues on Protocube.
	BootServersPerPage int `default:"50" yaml:"boot_servers_per_page"`
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
	Set(&config)
	return nil
}

// Set the global configuration instance. This is a blocking operation such that
// anything trying to set a different configuration value, or read the configuration
// will be paused until it is complete.
func Set(c *Configuration) {
	mutex.Lock()
	defer mutex.Unlock()
	config = c
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
