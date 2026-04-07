package config

import (
	"crypto/tls"
	"fmt"
	log2 "log"
	"os"
	"os/exec"
	"os/user"
	"path"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"text/template"

	"emperror.dev/errors"
	"github.com/acobaugh/osrelease"
	"github.com/apex/log"
	"github.com/creasty/defaults"
	"github.com/google/uuid"
	"github.com/mitchellh/colorstring"
	"golang.org/x/sys/unix"
	"gopkg.in/yaml.v3"
	"protoxon.com/sls/daemon/system"
)

var Path = "/etc/sls/daemon/config.yml"

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

	Location string `yaml:"location" default:"main"`

	Name string `yaml:"name" default:"SLS"`

	System SystemConfiguration `yaml:"system"`

	Docker DockerConfiguration `json:"docker" yaml:"docker"`

	Api ApiConfiguration `json:"api" yaml:"api"`

	Allocations []Allocation `json:"allocations" yaml:"allocations"`

	// Defines messages throttling configurations for server processes.
	Throttles ConsoleThrottles `json:"throttles" yaml:"throttles"`

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

type RemoteApi struct {
	Url   string `json:"-" yaml:"url" default:"https://protocube.sls.net:5620"`
	Token string `json:"-" yaml:"token" default:"API_KEY"`
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

type Allocation struct {
	Address         string `yaml:"address" default:"0.0.0.0"`
	Alias           string `yaml:"alias" default:"172.18.0.1"`
	ForceOutgoingIP bool   `yaml:"force_outgoing_ip"`
	Ports           string `yaml:"ports" default:"40000-40100"`
}

// ApiConfiguration defines the configuration for the API server
type ApiConfiguration struct {
	Url string `json:"-" yaml:"url" default:"https://daemon.sls.net:5585"`

	// The interface that the daemon should bind to.
	Host string `yaml:"host" default:"0.0.0.0"`

	// The port that the daemon should bind to.
	Port int `yaml:"port" default:"5585"`

	// TSL configuration for the daemon.
	Tls struct {
		Enabled         bool   `yaml:"enabled" default:"true"`
		CertificateFile string `json:"cert" yaml:"cert" default:"/etc/ssl/certs/sls.crt"`
		KeyFile         string `json:"key" yaml:"key" default:"/etc/ssl/private/sls.key"`
	}
}

// GetStatesPath returns the location of the JSON file that tracks server states.
func (sc *SystemConfiguration) GetStatesPath() string {
	return path.Join(sc.RootDirectory, "/states.json")
}

type SystemConfiguration struct {
	RootDirectory string `yaml:"root_directory" default:"/var/lib/sls"`

	LogDirectory string `yaml:"log_directory" default:"/var/log/sls"`

	// AllowedMounts enumerates host paths that can be exposed to containers as additional
	// bind mounts. Custom mounts supplied by servers must live within one of these paths.
	AllowedMounts []string `yaml:"allowed_mounts" default:"[]"`

	// TmpDirectory specifies where temporary files for daemons installation processes
	// should be created. This supports environments running docker-in-docker.
	TmpDirectory string `json:"-" yaml:"tmp_directory" default:"/tmp/sls/daemon"`

	Timezone string `yaml:"timezone"`

	// If set to false the daemon will not attempt to write a log rotate configuration to the disk
	// when it boots and one is not detected.
	EnableLogRotate bool `yaml:"enable_log_rotate" default:"true"`

	// The amount of time in seconds that can elapse before a server's disk space calculation is
	// considered stale and a re-check should occur. DANGER: setting this value too low can seriously
	// impact system performance and cause massive I/O bottlenecks and high CPU usage for the daemon
	// process.
	//
	// Set to 0 to disable disk checking entirely. This will always return 0 for the disk space used
	// by a server and should only be set in extreme scenarios where performance is critical and
	// disk usage is not a concern.
	DiskCheckInterval int64 `yaml:"disk_check_interval" default:"150"`

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

		Uid int `yaml:"uid" default:"988"`
		Gid int `yaml:"gid" default:"988"`
	} `yaml:"user"`

	OpenatMode string `yaml:"openat_mode" default:"auto"`

	// The user that should own all of the server files, and be used for containers.
	Username string `yaml:"username" default:"sls"`

	// Directory where the server data is stored at.
	Data string `json:"-" yaml:"data" default:"/var/lib/sls/data"`
	// Directory where state volumes are stored
	Volumes string `json:"-" yaml:"volumes" default:"/var/lib/sls/volumes"`
	// Directory where installed servers are stored
	Servers string `json:"-" yaml:"servers" default:"/var/lib/sls/servers"`
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

	// There are a non-trivial number of users out there whose data directories are actually a
	// symlink to another location on the disk. If we do not resolve that final destination at this
	// point things will appear to work, but endless errors will be encountered when we try to
	// verify accessed paths since they will all end up resolving outside the expected data directory.
	//
	// For the sake of automating away as much of this as possible, see if the data directory is a
	// symlink, and if so resolve to its final real path, and then update the configuration to use
	// that.
	if d, err := filepath.EvalSymlinks(config.System.Data); err != nil {
		if !os.IsNotExist(err) {
			return err
		}
	} else if d != config.System.Data {
		config.System.Data = d
	}

	log.WithField("path", config.System.Data).Debug("ensuring data directory exists")
	if err := os.MkdirAll(config.System.Data, 0o700); err != nil {
		return err
	}

	log.WithField("path", config.System.Servers).Debug("ensuring servers directory exists")
	if err := os.MkdirAll(config.System.Servers, 0o700); err != nil {
		return err
	}

	log.WithField("path", config.System.Volumes).Debug("ensuring volumes directory exists")
	if err := os.MkdirAll(config.System.Volumes, 0o700); err != nil {
		return err
	}

	return nil
}

// EnsureSLSUser ensures that the SLS core user exists on the
// system. This user will be the owner of all data in the root data directory
// and is used as the user within containers. If files are not owned by this
// user there will be issues with permissions on Docker mount points.
//
// This function IS NOT thread safe and should only be called in the main thread
// when the application is booting.
func EnsureSLSUser() error {
	sysName, err := getSystemName()
	if err != nil {
		return err
	}

	// Our way of detecting if sls is running inside of Docker.
	if sysName == "distroless" {
		config.System.Username = system.FirstNotEmpty(os.Getenv("SLS_USERNAME"), "sls")
		config.System.User.Uid = system.MustInt(system.FirstNotEmpty(os.Getenv("SLS_UID"), "988"))
		config.System.User.Gid = system.MustInt(system.FirstNotEmpty(os.Getenv("SLS_GID"), "988"))
		return nil
	}

	if config.System.User.Rootless.Enabled {
		log.Info("rootless mode is enabled, skipping user creation...")
		u, err := user.Current()
		if err != nil {
			return err
		}
		config.System.Username = u.Username
		config.System.User.Uid = system.MustInt(u.Uid)
		config.System.User.Gid = system.MustInt(u.Gid)
		return nil
	}

	log.WithField("username", config.System.Username).Info("checking for sls system user")
	u, err := user.Lookup(config.System.Username)
	// If an error is returned but it isn't the unknown user error just abort
	// the process entirely. If we did find a user, return it immediately.
	if err != nil {
		if _, ok := err.(user.UnknownUserError); !ok {
			return err
		}
	} else {
		config.System.User.Uid = system.MustInt(u.Uid)
		config.System.User.Gid = system.MustInt(u.Gid)
		return nil
	}

	command := fmt.Sprintf("useradd --system --no-create-home --shell /usr/sbin/nologin %s", config.System.Username)
	// Alpine Linux is the only OS we currently support that doesn't work with the useradd
	// command, so in those cases we just modify the command a bit to work as expected.
	if strings.HasPrefix(sysName, "alpine") {
		command = fmt.Sprintf("adduser -S -D -H -G %[1]s -s /sbin/nologin %[1]s", config.System.Username)
		// We have to create the group first on Alpine, so do that here before continuing on
		// to the user creation process.
		if _, err := exec.Command("addgroup", "-S", config.System.Username).Output(); err != nil {
			return err
		}
	}

	split := strings.Split(command, " ")
	if _, err := exec.Command(split[0], split[1:]...).Output(); err != nil {
		return err
	}
	u, err = user.Lookup(config.System.Username)
	if err != nil {
		return err
	}
	config.System.User.Uid = system.MustInt(u.Uid)
	config.System.User.Gid = system.MustInt(u.Gid)
	return nil
}

// EnableLogRotation writes a logrotate file for sls to the system logrotate
// configuration directory if one exists and a logrotate file is not found. This
// allows us to basically automate away the log rotation for most installs, but
// also enable users to make modifications on their own.
//
// This function IS NOT thread-safe.
func EnableLogRotation() error {
	if !config.System.EnableLogRotate {
		log.Info("skipping log rotate configuration, disabled in sls config file")
		return nil
	}

	if st, err := os.Stat("/etc/logrotate.d"); err != nil && !os.IsNotExist(err) {
		return err
	} else if (err != nil && os.IsNotExist(err)) || !st.IsDir() {
		return nil
	}
	if _, err := os.Stat("/etc/logrotate.d/sls"); err == nil || !os.IsNotExist(err) {
		return err
	}

	log.Info("no log rotation configuration found: adding file now")
	// If we've gotten to this point it means the logrotate directory exists on the system
	// but there is not a file for sls already. In that case, let us write a new file to
	// it so files can be rotated easily.
	f, err := os.Create("/etc/logrotate.d/sls")
	if err != nil {
		return err
	}
	defer f.Close()

	t, err := template.New("logrotate").Parse(`{{.LogDirectory}}/sls.log {
    size 10M
    compress
    delaycompress
    dateext
    maxage 7
    missingok
    notifempty
    postrotate
        /usr/bin/systemctl kill -s HUP sls.service >/dev/null 2>&1 || true
    endscript
}`)
	if err != nil {
		return err
	}

	return errors.Wrap(t.Execute(f, config.System), "config: failed to write logrotate to disk")
}

// Gets the system release name.
func getSystemName() (string, error) {
	// use osrelease to get release version and ID
	release, err := osrelease.Read()
	if err != nil {
		return "", err
	}
	return release["ID"], nil
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
func InitConfig() error {
	var configPath = Path
	if !filepath.IsAbs(configPath) {
		absolutePath, err := filepath.Abs(configPath)
		if err != nil {
			log2.Fatalf("config/config: failed to get path to config file: %s", err)
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

	err := loadConfigFromFile(configPath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			exitWithConfigurationNotice()
		}
		log2.Fatalf("config/config: error while reading configuration file: %s", err)
	}

	if err = ConfigureDirectories(); err != nil {
		return errors.Wrap(err, "config/config: Failed to configure directories")
	}
	return nil
}

// RemoteQueryConfiguration defines the configuration settings for remote requests
// from the daemon1 to the Protocube.
type RemoteQueryConfiguration struct {
	// The amount of time in seconds that the daemon should allow for a request to the Protocube API
	// to complete. If this time passes the request will be marked as failed. If your requests
	// are taking longer than 30 seconds to complete it is likely a performance issue that
	// should be resolved on Protocube, and not something that should be resolved by upping this
	// number.
	Timeout int `yaml:"timeout" default:"30"`

	// The number of servers to load in a single request to protocube API when booting the
	// Daemon instance. A single request is initially made to Protocube to get this number
	// of servers, and then the pagination status is checked and additional requests are
	// fired off in parallel to request the remaining pages.
	//
	// It is not recommended to change this from the default as you will likely encounter
	// memory limits on your Protocube instance. In the grand scheme of things 4 requests for
	// 50 servers is likely just as quick as two for 100 or one for 400, and will certainly
	// be less likely to cause performance issues on Protocube.
	BootServersPerPage int `yaml:"boot_servers_per_page" default:"50"`
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

	// Always apply struct defaults after decoding so missing fields get filled in.
	// This means removing a field from the YAML will cause the default to be used.
	if err := defaults.Set(&config); err != nil {
		return err
	}

	// Override token values with environment variables if present
	if envToken := os.Getenv("SLS_TOKEN"); envToken != "" {
		config.RemoteApi.Token = envToken
	}

	if err := ValidateImagePullPolicy(&config.Docker); err != nil {
		return errors.Wrap(err, "config: invalid docker settings")
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

func writeDefaultConfig(path string) error {
	// Ensure parent directory exists
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}

	var c Configuration
	if err := defaults.Set(&c); err != nil {
		return err
	}
	c.Uuid = uuid.New().String()

	out, err := yaml.Marshal(&c)
	if err != nil {
		return err
	}
	return os.WriteFile(path, out, 0o644)
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
