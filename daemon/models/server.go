package models

import "protoxon.com/sls/daemon/environment"

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
	ID                   string                `json:"id"`
	ProcessConfiguration *ProcessConfiguration `json:"process-configuration"`
	Image                string                `json:"image"`
	Invocation           string                `json:"invocation"`
	Limits               environment.Limits    `json:"limits"`
	ServerFolder         string                `json:"server-folder"`
	WorldFolder          string                `json:"world-folder"`
	Content              []Content             `json:"content,omitempty"`
	Save                 bool                  `json:"save"`
}

type CreateServerResponse struct {
	Allocation environment.Allocations `json:"allocations"`
}

type InstallationScript struct {
	ContainerImage string `yaml:"image"`
	Entrypoint     string `yaml:"entrypoint"`
	Script         string `yaml:"script"`
}
