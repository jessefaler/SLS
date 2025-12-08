package models

import "protoxon.com/sls/daemon/environment"

type CreateServerRequest struct {
	ID                   string                `json:"id"`
	ProcessConfiguration *ProcessConfiguration `json:"process-configuration"`
	Image                string                `json:"image"`
	Invocation           string                `json:"invocation"`
	Limits               environment.Limits    `json:"limits"`
	ServerFolder         string                `json:"server-folder"`
	WorldFolder          string                `json:"world-folder"`
	Content              []Content             `json:"content,omitempty"`
}

type CreateServerResponse struct {
	Allocation environment.Allocations `json:"allocations"`
}
