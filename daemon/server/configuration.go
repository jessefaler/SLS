package server

import (
	"sync"

	"protoxon.com/sls/daemon/environment"
	"protoxon.com/sls/daemon/models"
)

type ConfigurationMeta struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

type Configuration struct {
	mu sync.RWMutex

	Meta ConfigurationMeta `json:"meta"`

	// Whether the server is in a suspended state. Suspended servers cannot
	// be started or modified except in certain scenarios by an admin user.
	Suspended bool `json:"suspended"`

	// By default, this is false, however if selected within the software configuration while installing or re-installing a
	// server, specific installation scripts will be skipped for the server process.
	SkipInstallScripts bool `json:"skip_install_scripts"`

	// The command that should be usced when booting up the server instane.
	Invocation string `json:"invocation"`

	// An array of environment variables that should be passed along to the running
	// server process.
	EnvVars environment.Variables `json:"environment"`

	// Labels is a map of container labels that should be applied to the running server process.
	Labels map[string]string `json:"labels"`

	Allocations           environment.Allocations `json:"allocations"`
	Build                 environment.Limits      `json:"build"`
	CrashDetectionEnabled bool                    `json:"crash_detection_enabled"`
	Mounts                []Mount                 `json:"mounts"`
	// VolumeMounts are RO/RW binds from State.Volumes
	VolumeMounts []Mount `json:"volume_mounts"`
	// Copy is a list of source/target entries to copy into the server filesystem at start
	Copy []models.Copy `json:"copy,omitempty"`

	Container struct {
		// Defines the Docker image that will be used for this server
		Image string `json:"image,omitempty"`
	} `json:"container,omitempty"`

	// SoftwareVersion is the server's software version from the panel (e.g. egg version).
	// Exposed as VERSION in the server and install container environment.
	SoftwareVersion string `json:"software_version,omitempty"`
}

func (s *Server) Config() *Configuration {
	s.cfg.mu.RLock()
	defer s.cfg.mu.RUnlock()
	return &s.cfg
}

// DiskSpace returns the amount of disk space available to a server in bytes.
func (s *Server) DiskSpace() int64 {
	s.cfg.mu.RLock()
	defer s.cfg.mu.RUnlock()
	return s.cfg.Build.DiskSpace * 1024.0 * 1024.0
}

func (s *Server) MemoryLimit() int64 {
	s.cfg.mu.RLock()
	defer s.cfg.mu.RUnlock()
	return s.cfg.Build.MemoryLimit
}

func (c *Configuration) SetSuspended(s bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.Suspended = s
}
