package config

import (
	"fmt"
	"log"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"emperror.dev/errors"
	"github.com/mitchellh/colorstring"
	"gopkg.in/yaml.v3"
)

var SoftwareConfigPath = "/mnt/c/slime/software.yml"

var (
	serversMutex   sync.RWMutex
	serverSoftware map[string]SoftwareConfig
)

type SoftwareConfig struct {
	Software    string             `yaml:"software"`
	DockerImage string             `yaml:"docker-image"`
	StopCommand string             `yaml:"stop-command"`
	Invocation  string             `yaml:"invocation"`
	Done        string             `yaml:"done"`
	Install     InstallationScript `yaml:"install_script"`
}

type InstallationScript struct {
	ContainerImage string `json:"container_image"`
	Entrypoint     string `json:"entrypoint"`
	Script         string `json:"script"`
}

// InitSoftwareConfig loads the servers.yaml config into the global state.
func InitSoftwareConfig() {
	var configPath = SoftwareConfigPath
	if !filepath.IsAbs(configPath) {
		absolutePath, err := filepath.Abs(configPath)
		if err != nil {
			log.Fatalf("config/servers: failed to get path to software.yaml: %s", err)
		}
		configPath = absolutePath
	}

	err := loadSoftwareConfigFromFile(configPath)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			exitWithSoftwareConfigNotice()
		}
		log.Fatalf("config/servers: error while reading software.yaml: %s", err)
	}
}

// loadServersFromFile reads servers.yaml into serverConfig
func loadSoftwareConfigFromFile(path string) error {
	bytes, err := os.ReadFile(path)
	if err != nil {
		return err
	}

	var software map[string]SoftwareConfig
	if err := yaml.Unmarshal(bytes, &software); err != nil {
		return err
	}

	setSoftwareConfigs(software)
	return nil
}

func setSoftwareConfigs(cfg map[string]SoftwareConfig) {
	// Convert all software names to lowercase
	for key, sc := range cfg {
		sc.Software = strings.ToLower(sc.Software)
		cfg[key] = sc
	}

	serversMutex.Lock()
	defer serversMutex.Unlock()
	serverSoftware = cfg
}

// GetSoftwareByName returns the SoftwareConfig for a given software name
// (e.g. "Paper", "Spigot", "Velocity"). Returns (config, true) if found,
// or (SoftwareConfig{}, false) if not found.
func GetSoftwareByName(name string) (SoftwareConfig, bool) {
	serversMutex.RLock()
	defer serversMutex.RUnlock()

	for _, cfg := range serverSoftware {
		if cfg.Software == name {
			return cfg, true
		}
	}
	return SoftwareConfig{}, false
}

// SetSoftware sets or updates a single SoftwareConfig in memory.
// The software name is normalized to lowercase.
// This is used for testing
func SetSoftware(cfg SoftwareConfig) {
	cfg.Software = strings.ToLower(cfg.Software)

	serversMutex.Lock()
	defer serversMutex.Unlock()

	if serverSoftware == nil {
		serverSoftware = make(map[string]SoftwareConfig)
	}

	serverSoftware[cfg.Software] = cfg
}

func exitWithSoftwareConfigNotice() {
	fmt.Printf(colorstring.Color(`
[_red_][white][bold]Error: Server Software Configuration File Not Found[reset]

SLS was not able to locate the software.yaml file. Please ensure the file 
exists at the location below.

Location: %s[reset]

`), SoftwareConfigPath)
	os.Exit(1)
}
