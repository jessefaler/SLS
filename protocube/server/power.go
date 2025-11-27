package server

import (
	"encoding/json"
	"fmt"
)

type Status int

const (
	Unknown Status = iota
	Offline
	Starting
	Running
	Stopping
)

func (p Status) String() string {
	return [...]string{"unknown", "offline", "starting", "running", "stopping"}[p]
}

// Custom unmarshaler for Status
func (s *Status) UnmarshalJSON(data []byte) error {
	var statusStr string
	if err := json.Unmarshal(data, &statusStr); err != nil {
		return err
	}

	// Convert string to Status
	switch statusStr {
	case "unknown":
		*s = Unknown
	case "offline":
		*s = Offline
	case "starting":
		*s = Starting
	case "running":
		*s = Running
	case "stopping":
		*s = Stopping
	default:
		return fmt.Errorf("invalid status: %s", statusStr)
	}
	return nil
}
