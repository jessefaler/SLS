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
// It ensures required sections (blueprint, server, world) are present,
// validates their contents, and applies defaults for optional fields.
func (bp *Blueprint) UnmarshalYAML(unmarshal func(interface{}) error) error {
	type rawBlueprint struct {
		Meta        *Meta                  `yaml:"blueprint"`
		World       *World                 `yaml:"world"`
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

	if raw.World == nil {
		return errors.New("missing required section: world")
	}

	if raw.World != nil {
		if err := raw.World.Validate(); err != nil {
			return err
		}
	}

	// Commit validated data
	bp.Meta = *raw.Meta
	bp.World = raw.World
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

func (c *Content) Validate() error {
	if c.Name == "" {
		return errors.New("content.name cannot be empty")
	}
	if c.Source == "" {
		return errors.New("missing required field: content.Source for content `" + c.Name + "`")
	}
	return nil
}

func (w *World) Validate() error {
	if w.Name == "" {
		return errors.New("missing required field: world.name")
	}
	if w.Path == "" {
		return errors.New("missing required field: world.path")
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
	// Sets Path to "<Software>/<Version>" if it is not specified
	if s.Path == "" {
		s.Path = filepath.Join(s.Software, s.Version)
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
			config.Get().Software.Root,
		)
	}

	// Ensure the image this blueprint uses is defined in the software's docker images
	found := false
	for _, image := range sw.DockerImages {
		if image == s.Image {
			found = true
			break
		}
	}
	if !found {
		return fmt.Errorf("image %q is not defined in software %q", s.Image, sw.Name)
	}

	// Verify Limits - only create new Limits if not already set from blueprint
	if s.Limits == nil {
		s.Limits = &environment.Limits{}
	}
	if err := Validate(s.Limits); err != nil {
		return errors.Wrap(err, "server.limits")
	}

	// Verify Content
	for _, c := range s.Content {
		if err := c.Validate(); err != nil {
			return errors.Wrap(err, "server.content")
		}
	}

	return nil
}

// Validate Validates blueprint limits and sets default if a field is nil
func Validate(limit *environment.Limits) error {

	// Fill in defaults for nil fields
	if err := defaults.Set(limit); err != nil {
		return err
	}

	// Ensure IoWeight is between 10-1000
	if *limit.IoWeight < 10 || *limit.IoWeight > 1000 {
		return errors.New("io_weight must be between 10 and 1000")
	}

	return nil
}
