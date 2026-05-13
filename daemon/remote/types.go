package remote

import (
	"time"

	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/environment"
)

// A generic type allowing for easy binding use when making requests to API
// endpoints that only expect a singular argument or something that would not
// benefit from being a typed struct.
//
// Inspired by gin.H, same concept.
type d map[string]interface{}

// Same concept as d, but a map of strings, used for querying GET requests.
type q map[string]string

type ClientOption func(c *client)

type Pagination struct {
	CurrentPage uint `json:"current_page"`
	From        uint `json:"from"`
	LastPage    uint `json:"last_page"`
	PerPage     uint `json:"per_page"`
	To          uint `json:"to"`
	Total       uint `json:"total"`
}

type CreateServerRequest struct {
	BlueprintID string `json:"blueprint_id"`
}

type CreateServerResponse struct {
	ID string `json:"id"`
}

type NodeRegistration struct {
	Id          string              `json:"id"`
	Name        string              `json:"name"`
	Location    string              `json:"location"`
	Url         string              `json:"url"`
	Version     string              `json:"version"`
	Allocations []config.Allocation `json:"allocations"`
}

type InstallStatusRequest struct {
	Successful bool `json:"successful"`
	Reinstall  bool `json:"reinstall"`
}

// InstallationScript defines installation script information for a server
// process. This is used when a server version is installed for the first time, and when
// a server version is marked for re-installation.
type InstallationScript struct {
	ContainerImage string             `json:"container_image"`
	Entrypoint     string             `json:"entrypoint"`
	Script         string             `json:"script"`
	SkipScripts    bool               `json:"skip_scripts"`
	InstallLimits  environment.Limits `json:"limits"`
}

type HeartBeat struct {
}

type CrashData struct {
	Reason    string    `json:"reason"`
	ExitCode  int32     `json:"exit_code"`
	Timestamp time.Time `json:"timestamp"`
}
