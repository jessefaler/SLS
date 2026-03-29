package software

import "protoxon.com/sls/protocube/environment"

type Software struct {
	Id            string                `yaml:"id"`
	Name          string                `yaml:"name"`
	DockerImages  map[string]string     `yaml:"images"`
	Mappings      []map[string]string   `yaml:"mappings,omitempty"`
	StopCommand   string                `yaml:"stop-command"`
	Invocation    string                `yaml:"invocation"`
	OnlineSignal  string                `yaml:"online-signal"`
	InstallScript InstallationScript    `yaml:"install-script"`
	// Optional default resource limits for servers using this software; blueprint server.limits override per field.
	Limits        *environment.Limits   `yaml:"limits,omitempty" json:"limits,omitempty"`
	Configs       map[string]ConfigFile `yaml:"configs,omitempty" json:"configs,omitempty"`
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
