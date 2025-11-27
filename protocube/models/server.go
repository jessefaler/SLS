package models

import (
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/enviroment"
)

type PowerAction struct {
	Action      string `json:"action"`
	WaitSeconds int    `json:"wait_seconds"`
}

type ServerData struct {
	Id string `json:"id"`
}

type NodeCreateServerRequest struct {
	ID                   string                `json:"id"`
	ProcessConfiguration *ProcessConfiguration `json:"process-configuration"`
	Image                string                `json:"image"`
	Invocation           string                `json:"invocation"`
	Limits               *enviroment.Limits    `json:"limits"`
	ServerFolder         string                `json:"server-folder"`
	WorldFolder          string                `json:"world-folder"`
	Content              []blueprint.Content   `json:"content,omitempty"`
}

type CreateServerRequest struct {
	BlueprintID string `json:"blueprint_id"`
}
