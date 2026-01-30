package parser

import (
	"fmt"
	"strings"
	
	"protoxon.com/sls/protocube/environment"
)

// ServerPlaceholderData contains server information that can be used to replace placeholders
// in configuration values. Fields can be nil if the data is not yet available.
type ServerPlaceholderData struct {
	Limits     *environment.Limits
	Allocation *environment.Allocations
}

// ReplacePlaceholders replaces {{server.X}} and {{env.X}} placeholders in a value.
// If serverData is nil or a placeholder value is not available, the placeholder is left unchanged
// and will be handled by the daemon.
func ReplacePlaceholders(value interface{}, serverData *ServerPlaceholderData) interface{} {
	// Only process string values
	strValue, ok := value.(string)
	if !ok {
		return value
	}

	// If no server data, return as-is (daemon will handle placeholders)
	if serverData == nil {
		return strValue
	}

	// Replace server.build.* placeholders
	if serverData.Limits != nil {
		if serverData.Limits.MemoryLimit != nil {
			strValue = strings.ReplaceAll(strValue, "{{server.build.memory}}", fmt.Sprintf("%d", *serverData.Limits.MemoryLimit))
			strValue = strings.ReplaceAll(strValue, "{{server.build.memory_limit}}", fmt.Sprintf("%d", *serverData.Limits.MemoryLimit))
		}
		if serverData.Limits.Swap != nil {
			strValue = strings.ReplaceAll(strValue, "{{server.build.swap}}", fmt.Sprintf("%d", *serverData.Limits.Swap))
		}
		if serverData.Limits.CpuLimit != nil {
			strValue = strings.ReplaceAll(strValue, "{{server.build.cpu}}", fmt.Sprintf("%d", *serverData.Limits.CpuLimit))
			strValue = strings.ReplaceAll(strValue, "{{server.build.cpu_limit}}", fmt.Sprintf("%d", *serverData.Limits.CpuLimit))
		}
		if serverData.Limits.DiskSpace != nil {
			strValue = strings.ReplaceAll(strValue, "{{server.build.disk}}", fmt.Sprintf("%d", *serverData.Limits.DiskSpace))
			strValue = strings.ReplaceAll(strValue, "{{server.build.disk_space}}", fmt.Sprintf("%d", *serverData.Limits.DiskSpace))
		}
	}

	// Replace allocation placeholders if available
	if serverData.Allocation != nil {
		strValue = strings.ReplaceAll(strValue, "{{server.build.default.ip}}", serverData.Allocation.DefaultMapping.Ip)
		strValue = strings.ReplaceAll(strValue, "{{server.build.default.port}}", fmt.Sprintf("%d", serverData.Allocation.DefaultMapping.Port))
	}

	return strValue
}
