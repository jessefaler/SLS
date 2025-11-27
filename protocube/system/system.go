package system

import (
	"os"
	"strings"
)

type Information struct {
	Version string `json:"version"`
	System  System `json:"system"`
}

type System struct {
	Architecture  string `json:"architecture"`
	CPUThreads    int    `json:"cpu_threads"`
	MemoryBytes   int64  `json:"memory_bytes"`
	KernelVersion string `json:"kernel_version"`
	OS            string `json:"os"`
	OSType        string `json:"os_type"`
}

// DefaultTCPCC returns the default TCP congestion control algorithm on Linux.
// If reading fails, it returns "unknown".
func DefaultTCPCC() string {
	data, err := os.ReadFile("/proc/sys/net/ipv4/tcp_congestion_control")
	if err != nil {
		return "unknown"
	}
	return strings.TrimSpace(string(data))
}
