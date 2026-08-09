package blueprint

import (
	"fmt"
	"path/filepath"
	"strings"

	"emperror.dev/errors"
	"gopkg.in/yaml.v3"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/environment"
	"protoxon.com/sls/protocube/software"
)

var softwareRegistry *software.Registry

// String returns the blueprint as a nicely formatted YAML string.
func (bp *Blueprint) String() (string, error) {
	if bp == nil {
		return "", errors.New("blueprint is nil")
	}

	// Create a YAML node to enable nicer formatting
	node := yaml.Node{}
	if err := node.Encode(bp); err != nil {
		return "", errors.Wrap(err, "failed to encode blueprint to YAML")
	}

	// Marshal the node with indentation
	data, err := yaml.Marshal(&node)
	if err != nil {
		return "", errors.Wrap(err, "failed to marshal blueprint to YAML")
	}

	return string(data), nil
}

// UnmarshalYAML implements a custom YAML unmarshaler for Blueprint.
// It validates metadata and field shape. Completeness checks (software,
// version, image) run after mixin includes are applied during Resolve.
func (bp *Blueprint) UnmarshalYAML(unmarshal func(interface{}) error) error {
	type rawBlueprint struct {
		Meta        *Meta                  `yaml:"blueprint"`
		Includes    []string               `yaml:"includes"`
		State       *State                 `yaml:"state"`
		Server      *Server                `yaml:"server"`
		Save        bool                   `yaml:"save"`
		Annotations map[string]interface{} `yaml:"annotations"`
	}

	var raw rawBlueprint
	if err := unmarshal(&raw); err != nil {
		return err
	}

	if raw.Meta == nil {
		return errors.New("missing required section: blueprint")
	}
	if err := raw.Meta.Validate(); err != nil {
		return err
	}

	if err := validateIncludes(raw.Includes); err != nil {
		return err
	}

	// Server may be omitted when mixins supply it; validate shape only if present.
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

	bp.Meta = *raw.Meta
	bp.Includes = raw.Includes
	bp.State = raw.State
	bp.Server = raw.Server
	bp.Save = raw.Save
	bp.Annotations = raw.Annotations

	return nil
}

func (m *Meta) Validate() error {
	if m.ID == "" {
		return errors.New("missing required field: blueprint.id")
	}
	if m.Name == "" {
		return errors.New("missing required field: blueprint.name")
	}
	if m.Type == "" {
		return errors.New("missing required field: blueprint.type")
	}
	return nil
}

func validateIncludes(includes []string) error {
	seen := make(map[string]struct{}, len(includes))
	for i, id := range includes {
		id = strings.TrimSpace(id)
		if id == "" {
			return errors.New("includes contains an empty mixin id")
		}
		includes[i] = id
		if _, exists := seen[id]; exists {
			return errors.Errorf("includes contains duplicate mixin id %q", id)
		}
		seen[id] = struct{}{}
	}
	return nil
}

func (v *Volume) UnmarshalYAML(unmarshal func(interface{}) error) error {
	var s string
	if err := unmarshal(&s); err == nil && strings.TrimSpace(s) != "" {
		return v.unmarshalShorthand(strings.TrimSpace(s))
	}

	type volumeAlias Volume
	var tmp volumeAlias
	if err := unmarshal(&tmp); err != nil {
		return err
	}
	*v = Volume(tmp)
	return nil
}

// unmarshalShorthand parses name:source:target[:mode], e.g.
// world:worlds/world:/world:cow
func (v *Volume) unmarshalShorthand(s string) error {
	parts := strings.Split(s, ":")
	if len(parts) < 3 {
		return fmt.Errorf("invalid volume shorthand %q: expected name:source:target[:mode]", s)
	}
	if len(parts) > 4 {
		return fmt.Errorf("invalid volume shorthand %q: too many ':' segments (use mapping form if paths contain ':')", s)
	}
	v.Name = parts[0]
	v.Source = parts[1]
	v.Target = parts[2]
	if len(parts) == 4 {
		v.Mode = VolumeMode(parts[3])
	} else {
		v.Mode = VolumeModeCOW
	}
	return nil
}

func (v *Volume) Validate() error {
	if v.Name == "" {
		return errors.New("volume.name cannot be empty")
	}
	if v.Source == "" {
		return errors.New("missing required field: volume.source for volume " + v.Name)
	}
	if v.Target == "" {
		return errors.New("missing required field: volume.target for volume " + v.Name)
	}
	if v.Mode != "" && v.Mode != VolumeModeCOW && v.Mode != VolumeModeRO && v.Mode != VolumeModeRW {
		return errors.New("volume.mode must be one of: cow, ro, rw")
	}
	return nil
}

func (m *Mount) Validate() error {
	if m.Source == "" {
		return errors.New("mount.source cannot be empty")
	}
	if m.Target == "" {
		return errors.New("mount.target cannot be empty")
	}
	return nil
}

func (c *Copy) Validate() error {
	if c.Source == "" {
		return errors.New("copy.source cannot be empty")
	}
	if c.Target == "" {
		return errors.New("copy.target cannot be empty")
	}
	return nil
}

func (s *State) Validate() error {
	seenVolumes := make(map[string]struct{}, len(s.Volumes))
	for i := range s.Volumes {
		if err := s.Volumes[i].Validate(); err != nil {
			return errors.Wrap(err, "state.volumes")
		}
		name := s.Volumes[i].Name
		if _, exists := seenVolumes[name]; exists {
			return errors.Errorf("state.volumes: duplicate volume name %q", name)
		}
		seenVolumes[name] = struct{}{}
	}

	for i := range s.Mounts {
		if err := s.Mounts[i].Validate(); err != nil {
			return errors.Wrap(err, "state.mounts")
		}
	}

	for i := range s.Copy {
		if err := s.Copy[i].Validate(); err != nil {
			return errors.Wrap(err, "state.copy")
		}
	}

	for key := range s.Env {
		if key == "" {
			return errors.New("state.env key cannot be empty")
		}
	}

	return nil
}

func (s *Server) Validate() error {
	if s.Version == "" {
		return errors.New("missing required field: server.version")
	}
	if s.Software == "" {
		return errors.New("missing required field: server.software")
	}

	// Make the software name all lowercase
	s.Software = strings.ToLower(s.Software)

	sw := softwareRegistry.Get(s.Software)
	// Ensure a startup configuration exists so the system can detect
	// when the server has finished booting.
	if sw == nil {
		return fmt.Errorf(
			"unknown software %q: you must define configuration for this software in %s",
			s.Software,
			config.Get().System.Software,
		)
	}

	// If no image was defined in the blueprint get it from software mappings
	imageFromVersion := false
	if s.Image == "" {
		version, err := sw.ImageForVersion(s.Version)
		if err != nil {
			return err
		}
		s.Image = version
		imageFromVersion = true
	}

	// Resolve image: blueprint may specify either a variant key (e.g. "java_8") or a full URL.
	// When we got the image from ImageForVersion, it is already the final URL; otherwise look up by key.
	if !imageFromVersion {
		if url, ok := sw.DockerImages[s.Image]; ok {
			s.Image = url
		} else {
			return fmt.Errorf("image %q is not defined in software %q", s.Image, sw.Name)
		}
	}

	// Apply default limits when blueprint omits limits, or fill defaults for partial limits.
	// Software-defined limits (per egg) apply first; blueprint overrides any set field.
	if s.Limits == nil {
		s.Limits = &environment.Limits{}
	}
	s.Limits = environment.MergeLimits(environment.CopyLimits(sw.Limits), s.Limits)
	if err := environment.ValidateLimits(s.Limits); err != nil {
		return errors.Wrap(err, "server.limits")
	}

	// Sets Path to "<Software>/<Version>" if it is not specified
	if s.Path == "" {
		s.Path = filepath.Join(s.Software, s.Version)
	}

	return nil
}

func (m *Mount) UnmarshalYAML(unmarshal func(interface{}) error) error {
	var s string
	if err := unmarshal(&s); err != nil {
		return err
	}

	parts := strings.Split(s, ":")
	if len(parts) < 2 {
		return fmt.Errorf("invalid mount: %s", s)
	}

	m.Source = parts[0]
	m.Target = parts[1]
	m.ReadOnly = len(parts) > 2 && parts[2] == "ro"

	return nil
}

func (m *Copy) UnmarshalYAML(unmarshal func(interface{}) error) error {
	type copyAlias Copy
	var tmp copyAlias
	if err := unmarshal(&tmp); err == nil {
		*m = Copy(tmp)
		return nil
	}

	// Fallback to string shorthand
	var s string
	if err := unmarshal(&s); err != nil {
		return err
	}

	parts := strings.SplitN(s, ":", 2)
	if len(parts) < 2 {
		return fmt.Errorf("invalid copy shorthand: %s", s)
	}

	m.Source = parts[0]
	m.Target = parts[1]
	return nil
}
