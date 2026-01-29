package models

import "emperror.dev/errors"

type NodeData struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Location string `json:"location"`
	URL      string `json:"url"`
	Drained  bool   `json:"drained"`
}

type NodeState struct {
	Id      string `gorm:"primaryKey"`
	Drained bool   `gorm:"not null;default:false"`
}

type Information struct {
	Version string            `json:"version"`
	Docker  DockerInformation `json:"docker"`
	System  System            `json:"system"`
}

type NodeRegistration struct {
	Id          string       `json:"id"`
	Name        string       `json:"name"`
	Location    string       `json:"location"`
	Url         string       `json:"url"`
	Version     string       `json:"version"`
	Allocations []Allocation `json:"allocations"`
}

type Allocation struct {
	Address         string `yaml:"address"`
	Alias           string `yaml:"alias"`
	ForceOutgoingIP bool   `yaml:"force_outgoing_ip"`
	Ports           string `yaml:"ports"`
}

type NodeRegistrationResponse struct {
	SessionToken string `json:"token"`
}

func (r *NodeRegistration) Validate() error {
	if r.Id == "" {
		return errors.New("Node ID cannot be blank")
	}
	if r.Name == "" {
		return errors.New("Node url cannot be blank")
	}
	return nil
}

type DockerInformation struct {
	Version    string           `json:"version"`
	Cgroups    DockerCgroups    `json:"cgroups"`
	Containers DockerContainers `json:"containers"`
	Storage    DockerStorage    `json:"storage"`
	Runc       DockerRunc       `json:"runc"`
}

type DockerCgroups struct {
	Driver  string `json:"driver"`
	Version string `json:"version"`
}

type DockerContainers struct {
	Total   int `json:"total"`
	Running int `json:"running"`
	Paused  int `json:"paused"`
	Stopped int `json:"stopped"`
}

type DockerStorage struct {
	Driver     string `json:"driver"`
	Filesystem string `json:"filesystem"`
}

type DockerRunc struct {
	Version string `json:"version"`
}

type System struct {
	Architecture  string `json:"architecture"`
	CPUThreads    int    `json:"cpu_threads"`
	MemoryBytes   int64  `json:"memory_bytes"`
	KernelVersion string `json:"kernel_version"`
	OS            string `json:"os"`
	OSType        string `json:"os_type"`
}
