package models

import (
	"protoxon.com/sls/daemon/environment"
)

// ServerConfigurationResponse holds the server configuration data returned from
// Protocube. When a server process is started, The daemon communicates with Protocube
// to fetch the latest build information.
//
// This means we do not need to hit the daemon each time part of the server is
// updated, and Protocube serves as the source of truth at all times. This also
// means if a configuration is accidentally wiped on the daemon we can self-recover
// without too much hassle, so long as the daemon is aware of what servers should
// exist on it.
type ServerConfigurationResponse struct {
	Id                   string                  `json:"id"`
	ProcessConfiguration *ProcessConfiguration   `json:"process-configuration"`
	Image                string                  `json:"image"`
	Invocation           string                  `json:"invocation"`
	State                State                   `json:"state"`
	Limits               environment.Limits      `json:"limits"`
	Save                 bool                    `json:"save"`
	Allocations          environment.Allocations `json:"allocations"`
	ServerFolder         string                  `json:"server-folder"`
	SoftwareId           string                  `json:"software-id"`
	SoftwareVersion      string                  `json:"software-version"`
}

// Server data state configuration
type State struct {
	Volumes []Volume            `yaml:"volumes,omitempty" json:"volumes,omitempty"`
	Mounts  []environment.Mount `yaml:"mounts,omitempty" json:"mounts,omitempty"`
	Copy    []Copy              `yaml:"copy,omitempty" json:"copy,omitempty"`
	Env     map[string]string
}

type Copy struct {
	Source string `yaml:"source" json:"source"`
	Target string `yaml:"target" json:"target"`
}

// Volumes are managed storage units.
// They must exist within the configured volumes directory
// (e.g. /sls/volumes).
type Volume struct {
	Name   string     `yaml:"name" json:"name"`
	Source string     `yaml:"source" json:"source"`
	Target string     `yaml:"target" json:"target"`
	Mode   VolumeMode `yaml:"mode,omitempty" json:"mode,omitempty"`
}

type VolumeMode string

const (
	VolumeModeCOW VolumeMode = "cow" // copy-on-write overlay
	VolumeModeRO  VolumeMode = "ro"  // read-only bind
	VolumeModeRW  VolumeMode = "rw"  // read-write bind
)
