package blueprint

import (
	"protoxon.com/sls/protocube/enviroment"
	"protoxon.com/sls/protocube/parser"
)

// Blueprint is a high-level specification of a server environment.
//
// A Blueprint acts as a complete, portable recipe that describes everything
// required to launch and run a server instance. It combines metadata,
// world information, and server configuration into a single definition.
type Blueprint struct {
	Meta        Meta                   `yaml:"metadata" json:"metadata"`
	World       *World                 `yaml:"world" json:"world"`
	Server      *Server                `yaml:"server" json:"server"`
	Save        bool                   `yaml:"save,omitempty" json:"save,omitempty"`
	Annotations map[string]interface{} `yaml:"annotations,omitempty" json:"annotations,omitempty"`
}

type Meta struct {
	ID   string `yaml:"id" json:"id"`
	Name string `yaml:"name" json:"name"`
	Type string `yaml:"type" json:"type"`
}

type World struct {
	Name    string `yaml:"name" json:"name"`
	Authors string `yaml:"authors" json:"authors"`
	Path    string `yaml:"path" json:"path"`
}

type Server struct {
	Software       string                `yaml:"software" json:"software"`
	Version        string                `yaml:"version" json:"version"`
	Image          string                `yaml:"image" json:"image"`
	AllowedClients string                `yaml:"allowed_clients,omitempty" json:"allowed_clients,omitempty"`
	Path           string                `yaml:"path" json:"path"`
	Limits         *enviroment.Limits    `yaml:"limits,omitempty" json:"limits,omitempty"`
	Configs        map[string]ConfigFile `yaml:"configs,omitempty" json:"configs,omitempty"`
	Content        []Content             `yaml:"content,omitempty" json:"content,omitempty"`
}

type ConfigFile struct {
	Parser string                 `yaml:"parser" json:"parser"`
	Find   map[string]interface{} `yaml:"find" json:"find"`
}

// ToConfigurationFile converts a blueprint ConfigFile to a parser.ConfigurationFile
// that can be used by the daemon to patch configuration files.
// serverData can be nil if server information is not yet available (e.g., allocations).
// In that case, placeholders like {{server.build.default.port}} will be left for the daemon to handle.
func (cf *ConfigFile) ToConfigurationFile(fileName string, serverData *parser.ServerPlaceholderData) (*parser.ConfigurationFile, error) {
	configFile := &parser.ConfigurationFile{
		FileName: fileName,
		Parser:   parser.ConfigurationParser(cf.Parser),
		Replace:  make([]parser.ConfigurationFileReplacement, 0, len(cf.Find)),
	}

	// Convert each find entry to a replacement
	for match, value := range cf.Find {
		// Replace placeholders in the value if serverData is available
		processedValue := parser.ReplacePlaceholders(value, serverData)

		replaceValue, err := parser.ValueToReplaceValue(processedValue)
		if err != nil {
			return nil, err
		}

		configFile.Replace = append(configFile.Replace, parser.ConfigurationFileReplacement{
			Match:       match,
			ReplaceWith: *replaceValue,
		})
	}

	return configFile, nil
}

// Content represents additional files to include with a server instance, such as plugins, datapacks, or mods.
type Content struct {
	Name   string `yaml:"name" json:"name"`
	Source string `yaml:"source" json:"source"` // The source path relative to the root content folder
}

type Compression struct {
	Enabled   bool   `yaml:"enabled" json:"enabled"`
	Algorithm string `yaml:"algorithm" json:"algorithm"`
	Level     int    `yaml:"level" json:"level"`
}
