package software

type Software struct {
	Id            string              `yaml:"id"`
	Name          string              `yaml:"name"`
	DockerImages  map[string]string   `yaml:"images"`
	StopCommand   string              `yaml:"stop-command"`
	Invocation    string              `yaml:"invocation"`
	OnlineSignal  string              `yaml:"online-signal"`
	InstallScript *InstallationScript `yaml:"install-script"`
}

type InstallationScript struct {
	ContainerImage string `yaml:"image"`
	Entrypoint     string `yaml:"entrypoint"`
	Script         string `yaml:"script"`
}
