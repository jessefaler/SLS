package software

type Software struct {
	Id            string                `yaml:"id"`
	Name          string                `yaml:"name"`
	DockerImages  map[string]string     `yaml:"images"`
	Mappings      []map[string]string   `yaml:"mappings,omitempty"`
	StopCommand   string                `yaml:"stop-command"`
	Invocation    string                `yaml:"invocation"`
	OnlineSignal  string                `yaml:"online-signal"`
	InstallScript InstallationScript    `yaml:"install-script"`
	Configs       map[string]ConfigFile `yaml:"configs,omitempty" json:"configs,omitempty"`
}

type ConfigFile struct {
	Parser string                 `yaml:"parser" json:"parser"`
	Find   map[string]interface{} `yaml:"find" json:"find"`
}

type InstallationScript struct {
	// Image is the Docker image used for the installation container.
	// It is configured as "image" in YAML and exposed to the daemon as
	// "container_image" in JSON to match daemon expectations.
	Image       string `yaml:"image" json:"container_image"`
	Entrypoint  string `yaml:"entrypoint" json:"entrypoint"`
	Script      string `yaml:"script" json:"script"`
	SkipScripts bool   `yaml:"skip-scripts,omitempty" json:"skip_scripts" default:"false"`
}
