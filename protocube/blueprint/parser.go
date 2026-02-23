package blueprint

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/creasty/defaults"
	"gopkg.in/yaml.v3"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/environment"
	"protoxon.com/sls/protocube/software"
)

var softwareRegistry *software.Registry

// LoadAllBlueprints walks the Blueprints root directory recursively,
// finds all .yaml/.yml files, and loads them into Blueprint structs.
// It validates that no two blueprints share the same ID.
func LoadAllBlueprints(blueprintsRoot string, sw *software.Registry) ([]*Blueprint, error) {
	softwareRegistry = sw // Assign software registry so we can use it later
	if softwareRegistry == nil {
		return nil, errors.New("Failed to load blueprints software registry was nil")
	}
	var blueprints []*Blueprint
	seenIDs := make(map[string]struct{}) // tracks loaded blueprint IDs

	err := filepath.Walk(blueprintsRoot, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			log.WithField("path", path).Warnf("blueprint loader: Failed to access file: %v", err)
			return nil
		}

		// Skip directories
		if info.IsDir() {
			return nil
		}

		// Only process .yaml / .yml files
		ext := strings.ToLower(filepath.Ext(info.Name()))
		if ext != ".yaml" && ext != ".yml" {
			return nil
		}

		// Load the blueprint file
		bp, loadErr := load(path)
		if loadErr != nil {
			log.WithField("blueprint", path).Warnf("Failed to load blueprint: %v", loadErr)
			return nil
		}

		// Check for duplicate ID
		if _, exists := seenIDs[bp.Meta.ID]; exists {
			log.WithField("id", bp.Meta.ID).
				WithField("file", path).
				Errorf("Blueprint validation failed: blueprint with ID '%s' already exists", bp.Meta.ID)
			return nil // skip duplicate
		}

		// Mark ID as seen and append blueprint
		seenIDs[bp.Meta.ID] = struct{}{}
		blueprints = append(blueprints, bp)

		return nil
	})

	if err != nil {
		return nil, err
	}

	return blueprints, nil
}

// Load reads a YAML blueprint file from the given path
// and unmarshal's it into a Blueprint struct.
func load(path string) (*Blueprint, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var blueprint Blueprint
	if err := yaml.Unmarshal(data, &blueprint); err != nil {
		return nil, err
	}

	return &blueprint, nil
}

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
// It ensures required sections (blueprint, server) are present,
// validates their contents, and applies defaults for optional fields.
func (bp *Blueprint) UnmarshalYAML(unmarshal func(interface{}) error) error {
	type rawBlueprint struct {
		Meta        *Meta                  `yaml:"blueprint"`
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

	if raw.Server == nil {
		return errors.New("missing required section: server")
	}
	if err := raw.Server.Validate(); err != nil {
		return err
	}

	if raw.State != nil {
		if err := raw.State.Validate(); err != nil {
			return err
		}
	}

	// Commit validated data
	bp.Meta = *raw.Meta
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

func (s *State) Validate() error {
	for i := range s.Volumes {
		if err := s.Volumes[i].Validate(); err != nil {
			return errors.Wrap(err, "state.volumes")
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
	if s.Image == "" {
		return errors.New("missing required field: server.image")
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

	// Ensure the image this blueprint uses is defined in the software's docker images
	if url, ok := sw.DockerImages[s.Image]; ok {
		// Replace s.Image with the actual Docker image URL
		s.Image = url
	} else {
		return fmt.Errorf("image %q is not defined in software %q", s.Image, sw.Name)
	}

	// Apply default limits when blueprint omits limits, or fill defaults for partial limits
	if s.Limits == nil {
		s.Limits = &environment.Limits{}
	}
	if err := ValidateLimits(s.Limits); err != nil {
		return errors.Wrap(err, "server.limits")
	}

	// Sets Path to "<Software>/<Version>" if it is not specified
	if s.Path == "" {
		s.Path = filepath.Join(s.Software, s.Version)
	}

	return nil
}

// ValidateLimits validates blueprint limits and sets defaults for nil fields.
func ValidateLimits(limit *environment.Limits) error {

	// Fill in defaults for nil fields
	if err := defaults.Set(limit); err != nil {
		return err
	}

	// Ensure IoWeight is between 10-1000
	if limit.IoWeight != nil && (*limit.IoWeight < 10 || *limit.IoWeight > 1000) {
		return errors.New("io_weight must be between 10 and 1000")
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
