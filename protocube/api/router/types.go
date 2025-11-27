package router

import (
	"emperror.dev/errors"
)

// A generic type allowing for easy binding use when making requests to API
// endpoints that only expect a singular argument or something that would not
// benefit from being a typed struct.
//
// Inspired by gin.H, same concept.
type d map[string]interface{}

// Same concept as d, but a map of strings, used for querying GET requests.
type q map[string]string

type NodeRegistration struct {
	Id       string `json:"id"`
	Name     string `json:"name"`
	Location string `json:"location"`
	Url      string `json:"url"`
	Version  string `json:"version"`
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
