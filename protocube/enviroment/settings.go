package enviroment

// Limits is the build settings for a given server that impact docker container
// creation and resource limits for a server instance.
type Limits struct {
	// The total amount of memory in mebibytes that this server is allowed to
	// use on the host system.
	MemoryLimit *int64 `yaml:"memory_limit" json:"memory_limit" default:"2048"`

	// The amount of additional swap space to be provided to a container instance.
	Swap *int64 `yaml:"swap" json:"swap" default:"0"`

	// The relative weight for IO operations in a container. This is relative to other
	// containers on the system and should be a value between 10 and 1000.
	IoWeight *uint16 `yaml:"io_weight" json:"io_weight" default:"500"`

	// The percentage of CPU that this instance is allowed to consume relative to
	// the host. A value of 200% represents complete utilization of two cores. This
	// should be a value between 1 and THREAD_COUNT * 100.
	CpuLimit *int64 `yaml:"cpu_limit" json:"cpu_limit" default:"0"`

	// The amount of disk space in megabytes that a server is allowed to use.
	DiskSpace *int64 `yaml:"disk_space" json:"disk_space" default:"5120"`

	// Sets which CPU threads can be used by the docker instance.
	Threads *string `yaml:"threads" json:"threads" default:""`

	// If true, disables the OOM killer for this container.
	OOMDisabled *bool `yaml:"oom_disabled" json:"oom_disabled" default:"false"`
}
