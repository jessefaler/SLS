package mixin

import "protoxon.com/sls/protocube/blueprint"

// Mixins define reusable configuration that can be applied to blueprints.

type Mixin struct {
	// Mixin metadata (name, id, type)
	Meta Meta `yaml:"metadata" json:"metadata"`
	// Mixins this mixin extends
	Extends []string `yaml:"extends,omitempty" json:"extends,omitempty"`
	// Server runtime configuration
	Server *blueprint.Server `yaml:"server,omitempty" json:"server,omitempty"`
	// Data state configuration (volumes, host mounts, env var's and files to copy)
	State *blueprint.State `yaml:"state,omitempty" json:"state,omitempty"`
	// Optional data for external systems to read
	Annotations map[string]interface{} `yaml:"annotations,omitempty" json:"annotations,omitempty"`
}

type Meta struct {
	ID          string `yaml:"id" json:"id"`
	Description string `yaml:"description" json:"description"`
}
