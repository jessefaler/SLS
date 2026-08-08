package blueprint

// Mixins define reusable configuration that can be applied to blueprints.
// Fields are partial overlays; completeness is validated on the composed blueprint.

type Mixin struct {
	// Mixin metadata (id, description)
	Meta MixinMeta `yaml:"mixin" json:"mixin"`
	// Mixins this mixin extends
	Extends []string `yaml:"extends,omitempty" json:"extends,omitempty"`
	// Server runtime configuration (partial; software/version not required)
	Server *Server `yaml:"server,omitempty" json:"server,omitempty"`
	// Data state configuration (volumes, host mounts, env var's and files to copy)
	State *State `yaml:"state,omitempty" json:"state,omitempty"`
	// Optional data for external systems to read
	Annotations map[string]interface{} `yaml:"annotations,omitempty" json:"annotations,omitempty"`
}

type MixinMeta struct {
	ID          string `yaml:"id" json:"id"`
	Description string `yaml:"description,omitempty" json:"description,omitempty"`
}
