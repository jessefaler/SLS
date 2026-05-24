package environment

import (
	"emperror.dev/errors"
	"github.com/creasty/defaults"
)

// Limits is the build settings for a given server that impact docker container
// creation and resource limits for a server instance.
type Limits struct {
	// The total amount of memory in mebibytes that this server is allowed to
	// use on the host system.
	MemoryLimit *int64 `yaml:"memory_limit" json:"memory_limit" default:"4096"`

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
	DiskSpace *int64 `yaml:"disk_space" json:"disk_space" default:"8192"`

	// Sets which CPU threads can be used by the docker instance.
	Threads *string `yaml:"threads" json:"threads" default:""`

	// If true, disables the OOM killer for this container.
	OOMDisabled *bool `yaml:"oom_disabled" json:"oom_disabled" default:"true"`
}

func CopyLimits(orig *Limits) *Limits {
	if orig == nil {
		return nil
	}

	copy := &Limits{}

	if orig.MemoryLimit != nil {
		val := *orig.MemoryLimit
		copy.MemoryLimit = &val
	}
	if orig.Swap != nil {
		val := *orig.Swap
		copy.Swap = &val
	}
	if orig.IoWeight != nil {
		val := *orig.IoWeight
		copy.IoWeight = &val
	}
	if orig.CpuLimit != nil {
		val := *orig.CpuLimit
		copy.CpuLimit = &val
	}
	if orig.DiskSpace != nil {
		val := *orig.DiskSpace
		copy.DiskSpace = &val
	}
	if orig.Threads != nil {
		val := *orig.Threads
		copy.Threads = &val
	}
	if orig.OOMDisabled != nil {
		val := *orig.OOMDisabled
		copy.OOMDisabled = &val
	}

	return copy
}

// MergeLimits merges override into base. Non-nil fields in override replace base.
// If base is nil, it starts as an empty Limits. If override is nil, base is returned unchanged.
func MergeLimits(base *Limits, override *Limits) *Limits {
	if override == nil {
		return base
	}
	if base == nil {
		base = &Limits{}
	}
	if override.MemoryLimit != nil {
		base.MemoryLimit = override.MemoryLimit
	}
	if override.Swap != nil {
		base.Swap = override.Swap
	}
	if override.IoWeight != nil {
		base.IoWeight = override.IoWeight
	}
	if override.CpuLimit != nil {
		base.CpuLimit = override.CpuLimit
	}
	if override.DiskSpace != nil {
		base.DiskSpace = override.DiskSpace
	}
	if override.Threads != nil {
		base.Threads = override.Threads
	}
	if override.OOMDisabled != nil {
		base.OOMDisabled = override.OOMDisabled
	}

	return base
}

// ValidateLimits validates limits and sets defaults for nil fields.
func ValidateLimits(limit *Limits) error {
	if err := defaults.Set(limit); err != nil {
		return err
	}

	if limit.IoWeight != nil && (*limit.IoWeight < 10 || *limit.IoWeight > 1000) {
		return errors.New("io_weight must be between 10 and 1000")
	}

	return nil
}
