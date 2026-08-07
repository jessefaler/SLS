package blueprint

import (
	"protoxon.com/sls/protocube/environment"
)

// Blueprint is a complete, portable specification describing how to
// create and run a server instance.
type Blueprint struct {
	// Blueprint metadata (name, id, type)
	Meta Meta `yaml:"metadata" json:"metadata"`
	// Mixins this blueprint inherits
	Includes []string `yaml:"includes,omitempty" json:"includes,omitempty"`
	// Server runtime configuration
	Server *Server `yaml:"server,omitempty" json:"server,omitempty"`
	// Data state configuration (volumes, host mounts, env var's and files to copy)
	State *State `yaml:"state,omitempty" json:"state,omitempty"`
	// Controls if a server instance will be saved when it is stopped (false by default)
	Save bool `yaml:"save,omitempty" json:"save,omitempty"`
	// Optional data for external systems to read
	Annotations map[string]interface{} `yaml:"annotations,omitempty" json:"annotations,omitempty"`
}

// Blueprint metadata
type Meta struct {
	ID   string `yaml:"id" json:"id"`
	Name string `yaml:"name" json:"name"`
	Type string `yaml:"type" json:"type"`
}

// Server data state configuration
type State struct {
	Volumes []Volume          `yaml:"volumes,omitempty" json:"volumes,omitempty"`
	Mounts  []Mount           `yaml:"mounts,omitempty" json:"mounts,omitempty"`
	Copy    []Copy            `yaml:"copy,omitempty" json:"copy,omitempty"`
	Env     map[string]string `yaml:"env,omitempty" json:"env,omitempty"`
}

type Copy struct {
	Source string `yaml:"source" json:"source"`
	Target string `yaml:"target" json:"target"`
}

// Host mount configuration
type Mount struct {
	// The target path on the system. This is "/home/container" for all server's Default mount
	// but in non-container environments you can likely ignore the target and just work with the
	// source.
	Target string `json:"target"`

	// The directory from which the files will be read. In Docker environments this is the directory
	// that we're mounting into the container at the Target location.
	Source string `json:"source"`

	// Whether the directory is being mounted as read-only. It is up to the environment to
	// handle this value correctly and ensure security expectations are met with its usage.
	ReadOnly bool `json:"read_only"`
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

// Server runtime configuration
type Server struct {
	Software string `yaml:"software" json:"software"`
	Version  string `yaml:"version" json:"version"`
	Image    string `yaml:"image" json:"image"`

	// Optional override for server base path
	Path string `yaml:"path,omitempty" json:"path,omitempty"`

	Limits  *environment.Limits   `yaml:"limits,omitempty" json:"limits,omitempty"`
	Configs map[string]ConfigFile `yaml:"configs,omitempty" json:"configs,omitempty"`
}

// Configuration patching
type ConfigFile struct {
	Parser string                 `yaml:"parser" json:"parser"`
	Find   map[string]interface{} `yaml:"find" json:"find"`
}
