package system

import (
	"bufio"
	"os"
	"runtime"
	"strconv"
	"strings"

	"github.com/acobaugh/osrelease"
	"github.com/docker/docker/pkg/parsers/kernel"
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

// GetTotalMemoryBytes reads the total system memory from /proc/meminfo and returns it in bytes.
// Returns -1 if the memory information cannot be read or parsed.
func GetTotalMemoryBytes() int64 {
	file, err := os.Open("/proc/meminfo")
	if err != nil {
		return -1
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "MemTotal:") {
			fields := strings.Fields(line)
			if len(fields) >= 2 {
				// Memory is reported in KB, so we need to convert to bytes
				kb, err := strconv.ParseInt(fields[1], 10, 64)
				if err != nil {
					return -1
				}
				return kb * 1024
			}
		}
	}
	return -1
}

func GetSystemInformation() (*Information, error) {
	k, err := kernel.GetKernelVersion()
	if err != nil {
		return nil, err
	}

	release, err := osrelease.Read()
	if err != nil {
		return nil, err
	}

	var os string
	if release["PRETTY_NAME"] != "" {
		os = release["PRETTY_NAME"]
	} else if release["NAME"] != "" {
		os = release["NAME"]
	}

	return &Information{
		Version: Version,
		System: System{
			Architecture:  runtime.GOARCH,
			CPUThreads:    runtime.NumCPU(),
			MemoryBytes:   GetTotalMemoryBytes(),
			KernelVersion: k.String(),
			OS:            os,
			OSType:        runtime.GOOS,
		},
	}, nil
}
