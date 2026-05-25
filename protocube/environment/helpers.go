package environment

import (
	"emperror.dev/errors"
	"github.com/creasty/defaults"
)

func CopyLimits(orig *Limits) *Limits {
	if orig == nil {
		return nil
	}

	c := &Limits{}

	if orig.MemoryLimit != nil {
		val := *orig.MemoryLimit
		c.MemoryLimit = &val
	}
	if orig.Swap != nil {
		val := *orig.Swap
		c.Swap = &val
	}
	if orig.IoWeight != nil {
		val := *orig.IoWeight
		c.IoWeight = &val
	}
	if orig.CpuLimit != nil {
		val := *orig.CpuLimit
		c.CpuLimit = &val
	}
	if orig.DiskSpace != nil {
		val := *orig.DiskSpace
		c.DiskSpace = &val
	}
	if orig.Threads != nil {
		val := *orig.Threads
		c.Threads = &val
	}
	if orig.OOMDisabled != nil {
		val := *orig.OOMDisabled
		c.OOMDisabled = &val
	}

	return c
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
