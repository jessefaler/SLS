package software

import "protoxon.com/sls/protocube/environment"

type Software struct {
	Id            string              `yaml:"id"`
	Name          string              `yaml:"name"`
	DockerImages  map[string]string   `yaml:"images"`
	Mappings      []map[string]string `yaml:"mappings,omitempty"`
	StopCommand   string              `yaml:"stop-command"`
	Invocation    string              `yaml:"invocation"`
	OnlineSignal  string              `yaml:"online-signal"`
	InstallScript InstallationScript  `yaml:"install-script"`
	// Optional default resource limits for servers using this software; blueprint server.limits override per field.
	Limits  *environment.Limits   `yaml:"limits,omitempty" json:"limits,omitempty"`
	Configs map[string]ConfigFile `yaml:"configs,omitempty" json:"configs,omitempty"`
	// Optional: fetch newer YAML from URL when enabled (see SoftwareUpdate).
	Update *SoftwareUpdate `yaml:"update,omitempty" json:"update,omitempty"`
}

// SoftwareUpdate configures automatic refresh of this software definition from a remote URL
// when Protocube loads software (startup and software reload). GitHub "blob" page URLs are
// resolved to raw.githubusercontent.com for download.
type SoftwareUpdate struct {
	Enabled bool   `yaml:"enabled" json:"enabled"`
	URL     string `yaml:"url" json:"url"`
}

type ConfigFile struct {
	Parser string                 `yaml:"parser" json:"parser"`
	Find   map[string]interface{} `yaml:"find" json:"find"`
}

type InstallationScript struct {
	Entrypoint  string `yaml:"entrypoint" json:"entrypoint"`
	Script      string `yaml:"script" json:"script"`
	SkipScripts bool   `yaml:"skip-scripts,omitempty" json:"skip_scripts" default:"false"`
}
