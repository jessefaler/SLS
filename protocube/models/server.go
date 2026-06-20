package models

import (
	"errors"
	"time"

	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/environment"
	"protoxon.com/sls/protocube/software"
	"protoxon.com/sls/protocube/system"
)

type PowerAction struct {
	Action      string `json:"action"`
	WaitSeconds int    `json:"wait_seconds"`
}

// ServerData is the public API representation of a managed server.
type ServerData struct {
	Id              string                   `json:"id"`
	BlueprintId     string                   `json:"blueprint_id"`
	NodeName        string                   `json:"node_name"`
	NodeId          string                   `json:"node_id"`
	Allocations     *environment.Allocations `json:"allocations"`
	Overrides       *ServerOverrides         `json:"overrides,omitempty"`
	SoftwareId      string                   `json:"software_id"`
	SoftwareVersion string                   `json:"software_version"`
	Image           string                   `json:"image"`
	Limits          *environment.Limits      `json:"limits"`
}

// ServerRecord is the database representation of a server.
type ServerRecord struct {
	Id            string                       `gorm:"primaryKey"`
	NodeName      string                       `gorm:"index"`
	NodeId        string                       `gorm:"index"`
	BlueprintId   string                       `gorm:"index"`
	Overrides     *ServerOverrides             `gorm:"serializer:json"`
	Configuration *ServerConfiguration         `gorm:"serializer:json"`
	InstallScript *software.InstallationScript `gorm:"serializer:json"`
}

// Validate reports whether the server record has the required fields.
func (r *ServerRecord) Validate() error {
	if r == nil {
		return errors.New("server record is nil")
	}
	if r.Configuration == nil {
		return errors.New("server configuration is missing")
	}
	if r.InstallScript == nil {
		return errors.New("server install script is missing")
	}
	return nil
}

// ServerConfiguration is the servers runtime configuration information
type ServerConfiguration struct {
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

type StatusResponse struct {
	Status string `json:"status"`
}

type InstallInfo struct {
	Phase         string     `json:"phase"`
	ContainerID   string     `json:"container_id,omitempty"`
	ContainerName string     `json:"container_name,omitempty"`
	Status        string     `json:"status,omitempty"`
	ExitCode      *int64     `json:"exit_code,omitempty"`
	StartedAt     *time.Time `json:"started_at,omitempty"`
	FinishedAt    *time.Time `json:"finished_at,omitempty"`
	FailureReason string     `json:"failure_reason,omitempty"`
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

type CreateServerRequest struct {
	BlueprintID string           `json:"blueprint_id"`
	NodeId      string           `json:"node_id,omitempty"`
	Overrides   *ServerOverrides `json:"overrides,omitempty"`
}

type ServerOverrides struct {
	// Save overrides the save flag
	Save *bool `json:"save,omitempty"`
	// Resource limit overrides
	Limits *environment.Limits `json:"limits,omitempty"`
	// Configs are configuration file patches applied after software and blueprint
	// patches. Same file/key is overridden and new keys are merged.
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
