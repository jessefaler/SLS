package blueprint

import (
	"os"
	"strings"

	"emperror.dev/errors"
	"gopkg.in/yaml.v3"
)

// LoadAllMixins walks the Mixins directory recursively,
// finds all .yaml/.yml files, and loads them into Mixins structs.
// It validates that no two mixins share the same ID.
func LoadAllMixins(mixinsDirectory string) ([]*Mixin, error) {
	return loadAllYAML(mixinsDirectory, "mixin", loadMixin, func(m *Mixin) string {
		return m.Meta.ID
	})
}

// loadMixin reads a YAML mixin file from the given path
// and unmarshal's it into a Mixin struct.
func loadMixin(path string) (*Mixin, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var mixin Mixin
	if err := yaml.Unmarshal(data, &mixin); err != nil {
		return nil, err
	}

	return &mixin, nil
}

// UnmarshalYAML implements a custom YAML unmarshaler for Mixin.
// It requires the mixin metadata section and validates field shape for any
// optional server/state overlays. Completeness checks (software, version, image)
// are intentionally deferred until the composed blueprint is validated.
func (m *Mixin) UnmarshalYAML(unmarshal func(interface{}) error) error {
	type rawMixin struct {
		Meta        *MixinMeta             `yaml:"mixin"`
		Extends     []string               `yaml:"extends"`
		Server      *Server                `yaml:"server"`
		State       *State                 `yaml:"state"`
		Annotations map[string]interface{} `yaml:"annotations"`
	}

	var raw rawMixin
	if err := unmarshal(&raw); err != nil {
		return err
	}

	if raw.Meta == nil {
		return errors.New("missing required section: mixin")
	}
	if err := raw.Meta.Validate(); err != nil {
		return err
	}

	if err := validateExtends(raw.Extends); err != nil {
		return err
	}

	if raw.Server != nil {
		if err := raw.Server.validateServer(); err != nil {
			return err
		}
	}

	if raw.State != nil {
		if err := raw.State.Validate(); err != nil {
			return err
		}
	}

	m.Meta = *raw.Meta
	m.Extends = raw.Extends
	m.Server = raw.Server
	m.State = raw.State
	m.Annotations = raw.Annotations

	return nil
}

func (m *MixinMeta) Validate() error {
	if m.ID == "" {
		return errors.New("missing required field: mixin.id")
	}
	return nil
}

func validateExtends(extends []string) error {
	seen := make(map[string]struct{}, len(extends))
	for i, id := range extends {
		id = strings.TrimSpace(id)
		if id == "" {
			return errors.New("extends contains an empty mixin id")
		}
		extends[i] = id
		if _, exists := seen[id]; exists {
			return errors.Errorf("extends contains duplicate mixin id %q", id)
		}
		seen[id] = struct{}{}
	}
	return nil
}

// Checks that present fields on the server struct are valid without
// requiring specific fields to exist
// full validation of server fields is done in the blueprint parser
func (s *Server) validateServer() error {
	if s.Software != "" {
		s.Software = strings.ToLower(s.Software)
	}

	if s.Limits != nil {
		if s.Limits.IoWeight != nil && *s.Limits.IoWeight != 0 &&
			(*s.Limits.IoWeight < 10 || *s.Limits.IoWeight > 1000) {
			return errors.New("server.limits.io_weight must be 0 or between 10 and 1000")
		}
	}

	return nil
}
