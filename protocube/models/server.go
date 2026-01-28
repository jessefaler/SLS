package models

import (
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/enviroment"
	"protoxon.com/sls/protocube/system"
)

type PowerAction struct {
	Action      string `json:"action"`
	WaitSeconds int    `json:"wait_seconds"`
}

type ServerData struct {
	Id          string `json:"id"`
	BlueprintId string `json:"blueprint_id"`
	NodeName    string `json:"node_name"`
	NodeId      string `json:"node_id"`
	Ip          string `json:"ip"`
	Port        int    `json:"port"`
}

type ServerStore struct {
	Id          string           `gorm:"primaryKey"`
	NodeName    string           `gorm:"index"`
	NodeId      string           `gorm:"index"`
	BlueprintId string           `gorm:"index"`
	Overrides   *ServerOverrides `gorm:"serializer:json"`
}

// ResourceUsage defines the current resource usage for a given server instance. If a server is offline you
// should obviously expect memory and CPU usage to be 0. However, disk will always be returned
// since that is not dependent on the server being running to collect that data.
type ResourceUsage struct {

	// Embed the current environment stats into this server specific resource usage struct.
	enviroment.Stats

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

type ServerConfigurationResponse struct {
	ID                   string                `json:"id"`
	ProcessConfiguration *ProcessConfiguration `json:"process-configuration"`
	Image                string                `json:"image"`
	Invocation           string                `json:"invocation"`
	Limits               *enviroment.Limits    `json:"limits"`
	ServerFolder         string                `json:"server-folder"`
	WorldFolder          string                `json:"world-folder"`
	Content              []blueprint.Content   `json:"content,omitempty"`
	Save                 bool                  `json:"save"`
}

type CreateServerRequest struct {
	BlueprintID string           `json:"blueprint_id"`
	NodeId      string           `json:"node_id,omitempty"`
	Overrides   *ServerOverrides `json:"overrides,omitempty"`
}

type ServerOverrides struct {
	Save   *bool              `json:"save,omitempty"`
	Limits *enviroment.Limits `json:"limits,omitempty"`
}

type CreateServerResponse struct {
	Allocation enviroment.Allocations `json:"allocations"`
}
