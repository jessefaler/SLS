package software

import (
	"os"
	"path/filepath"
	"strings"

	"emperror.dev/errors"
	"github.com/apex/log"
	"gopkg.in/yaml.v3"
)

// LoadAllSoftware walks the Software root directory recursively,
// finds all .yaml/.yml files, and loads them into Software structs.
// It validates that no two software share the same ID.
func LoadAllSoftware(softwareRoot string) ([]*Software, error) {
	software := make([]*Software, 0)
	seenIDs := make(map[string]struct{}) // tracks loaded software IDs

	err := filepath.Walk(softwareRoot, func(path string, info os.FileInfo, err error) error {
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
		s, loadErr := load(path)
		if loadErr != nil {
			log.WithField("file", path).Warnf("Failed to load software: %v", loadErr)
			return nil
		}

		// Check for duplicate ID
		if _, exists := seenIDs[s.Id]; exists {
			log.WithField("id", s.Id).
				WithField("file", path).
				Errorf("Software validation failed: software with ID '%s' already exists", s.Id)
			return nil // skip duplicate
		}

		// Mark ID as seen and append blueprint
		seenIDs[s.Id] = struct{}{}
		software = append(software, s)

		return nil
	})

	if err != nil {
		return nil, err
	}

	return software, nil
}

// Load reads a YAML blueprint file from the given path
// and unmarshal's it into a Blueprint struct.
// Load reads a YAML blueprint file from the given path
// and unmarshals it into a Software struct inside a config wrapper.
func load(path string) (*Software, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	var cfg config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, err
	}

	// Access the Software struct inside the config struct
	return &cfg.Software, nil
}

// String returns the blueprint as a nicely formatted YAML string.
func (s *Software) String() (string, error) {
	if s == nil {
		return "", errors.New("software is nil")
	}

	// Create a YAML node to enable nicer formatting
	node := yaml.Node{}
	if err := node.Encode(s); err != nil {
		return "", errors.Wrap(err, "failed to encode software to YAML")
	}

	// Marshal the node with indentation
	data, err := yaml.Marshal(&node)
	if err != nil {
		return "", errors.Wrap(err, "failed to marshal software to YAML")
	}

	return string(data), nil
}

// UnmarshalYAML implements a custom YAML unmarshaler for Software.
// It ensures required sections are present, validates their contents,
// and applies defaults for optional fields.
func (s *Software) UnmarshalYAML(unmarshal func(interface{}) error) error {
	// Use a temporary struct to unmarshal directly into Software fields
	// (not into a config struct, since we're already at the software: level)
	var tmp struct {
		Id            string                `yaml:"id"`
		Name          string                `yaml:"name"`
		DockerImages  map[string]string     `yaml:"images"`
		StopCommand   string                `yaml:"stop-command"`
		Invocation    string                `yaml:"invocation"`
		OnlineSignal  string                `yaml:"online-signal"`
		InstallScript *InstallationScript   `yaml:"install-script"`
		Configs       map[string]ConfigFile `yaml:"configs,omitempty" json:"configs,omitempty"`
	}
	if err := unmarshal(&tmp); err != nil {
		return err
	}

	// Ensure required fields are present
	if tmp.Id == "" {
		return errors.New("missing required field: software.id")
	}
	if tmp.Name == "" {
		return errors.New("missing required field: software.name")
	}
	if len(tmp.DockerImages) == 0 {
		return errors.New("missing required field: software.images")
	}
	if tmp.StopCommand == "" {
		return errors.New("missing required field: software.stop-command")
	}
	if tmp.Invocation == "" {
		return errors.New("missing required field: software.invocation")
	}
	if tmp.OnlineSignal == "" {
		return errors.New("missing required field: software.online-signal")
	}

	// Apply the unmarshalled data to the Software struct
	s.Id = tmp.Id
	s.Name = tmp.Name
	s.DockerImages = tmp.DockerImages
	s.StopCommand = tmp.StopCommand
	s.Invocation = tmp.Invocation
	s.OnlineSignal = tmp.OnlineSignal
	s.Configs = tmp.Configs

	// If install-script is provided, validate it
	if tmp.InstallScript != nil {
		if err := tmp.InstallScript.Validate(); err != nil {
			return err
		}
		s.InstallScript = *tmp.InstallScript
	}

	return nil
}

// Validate validates the InstallationScript fields.
func (is *InstallationScript) Validate() error {
	if is.ContainerImage == "" {
		return errors.New("missing required field: install-script.container_image")
	}
	if is.Entrypoint == "" {
		return errors.New("missing required field: install-script.entrypoint")
	}
	if is.Script == "" {
		return errors.New("missing required field: install-script.script")
	}
	return nil
}

type config struct {
	Software Software `yaml:"software"`
}
