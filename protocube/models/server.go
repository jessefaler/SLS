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
	Id   string `json:"id"`
	Ip   string `json:"ip"`
	Port int    `json:"port"`
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
	Save                 bool                  `json:"save"`
}

type CreateServerRequest struct {
	BlueprintID string `json:"blueprint_id"`
	NodeId      string `json:"node_id,omitempty"`
}

type CreateServerResponse struct {
	Allocation enviroment.Allocations `json:"allocations"`
}
