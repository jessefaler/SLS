//go:build linux

package system

import (
	"fmt"
	"os"
	"strings"

	"golang.org/x/sys/unix"
)

// DefaultTCPCC returns the default TCP congestion control algorithm on Linux.
// If reading fails, it returns "unknown".
func DefaultTCPCC() string {
	data, err := os.ReadFile("/proc/sys/net/ipv4/tcp_congestion_control")
	if err != nil {
		return "unknown"
	}
	return strings.TrimSpace(string(data))
}

func HasCapSysAdmin() (bool, error) {
	var hdr unix.CapUserHeader
	var data unix.CapUserData

	hdr.Version = unix.LINUX_CAPABILITY_VERSION_3
	hdr.Pid = 0

	if err := unix.Capget(&hdr, &data); err != nil {
		return false, fmt.Errorf("capget failed: %w", err)
	}

	const CapSysAdmin = 21
	mask := uint32(1) << (CapSysAdmin % 32)
	effective := data.Effective & mask

	return effective != 0, nil
}
