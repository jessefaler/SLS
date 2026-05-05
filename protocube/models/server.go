package models

import (
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/environment"
	"protoxon.com/sls/protocube/system"
)

type PowerAction struct {
	Action      string `json:"action"`
	WaitSeconds int    `json:"wait_seconds"`
}

type ServerData struct {
	Id          string                  `json:"id"`
	BlueprintId string                  `json:"blueprint_id"`
	NodeName    string                  `json:"node_name"`
	NodeId      string                  `json:"node_id"`
	Allocations environment.Allocations `json:"allocations"`
	// Overrides that were set when the server was created (nil if none).
	Overrides *ServerOverrides `json:"overrides,omitempty"`
}

type ServerStore struct {
	Id          string                  `gorm:"primaryKey"`
	NodeName    string                  `gorm:"index"`
	NodeId      string                  `gorm:"index"`
	BlueprintId string                  `gorm:"index"`
	Allocation  environment.Allocations `gorm:"serializer:json"`
	Overrides   *ServerOverrides        `gorm:"serializer:json"`
}

type StatusResponse struct {
	Status string `json:"status"`
}

// ResourceUsage defines the current resource usage for a given server instance. If a server is offline you
// should obviously expect memory and CPU usage to be 0. However, disk will always be returned
// since that is not dependent on the server being running to collect that data.
type ResourceUsage struct {

	// Embed the current environment stats into this server specific resource usage struct.
	environment.Stats

	// The current server status.
	State *system.AtomicString `json:"state"`

	// The current disk space being used by the server. This value is not guaranteed to be accurate
	// at all times. It is "manually" set whenever server.Proc() is called. This is kind of just a
	// hacky solution for now to avoid passing events all over the place.
	Disk int64 `json:"disk_bytes"`
	// Max size of the file system
	MaxDisk int64 `json:"disk_max"`
	// The disk usage of the overlay filesystem
	// The represents the actual disk space this server takes up
	Overlay int64 `json:"overlay_bytes"`
}

// ContentItem is the wire format for content to copy into a server (replaces blueprint.Content).
type ContentItem struct {
	Name   string `json:"name"`
	Source string `json:"source"`
}

// MountConfig is the wire format for a bind mount (source/target match daemon environment.Mount).
type MountConfig struct {
	Source   string `json:"source"`
	Target   string `json:"target"`
	ReadOnly bool   `json:"read_only"`
}

type ServerConfigurationResponse struct {
	Id                   string                  `json:"id"`
	ProcessConfiguration *ProcessConfiguration   `json:"process-configuration"`
	Image                string                  `json:"image"`
	Invocation           string                  `json:"invocation"`
	State                *blueprint.State        `json:"state"`
	Limits               *environment.Limits     `json:"limits"`
	Save                 bool                    `json:"save"`
	Allocations          environment.Allocations `json:"allocations"`
	SoftwareId           string                  `json:"software-id"`
	ServerFolder         string                  `json:"server-folder"`
	SoftwareVersion      string                  `json:"software-version"`
	HasInstallScript     bool                    `json:"has-install-script"`
	SkipInstallScript    bool                    `json:"skip-install-script"`
}

type CreateServerRequest struct {
	BlueprintID string           `json:"blueprint_id"`
	NodeId      string           `json:"node_id,omitempty"`
	Overrides   *ServerOverrides `json:"overrides,omitempty"`
}

type ServerOverrides struct {
	Save   *bool               `json:"save,omitempty"`
	Limits *environment.Limits `json:"limits,omitempty"`
	// Configs are configuration file patches applied after software and blueprint
	// patches. Same file/key is overridden; new keys are merged.
	Configs map[string]blueprint.ConfigFile `json:"configs,omitempty"`
	// Software overrides the blueprint's server.software (registry lookup and path).
	Software *string `json:"software,omitempty"`
	// Version overrides the blueprint's server.version (path segment).
	Version *string `json:"version,omitempty"`
	// Image overrides the blueprint's server.image (container image).
	Image *string `json:"image,omitempty"`
	// Env adds or overrides keys in blueprint state.env for the container environment.
	Env map[string]string `json:"env,omitempty"`
}

type CreateServerResponse struct {
	Allocation environment.Allocations `json:"allocations"`
}
