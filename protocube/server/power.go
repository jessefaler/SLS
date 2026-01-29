package server

// Status is the server power state string, matching the daemon environment design
// (environment.ProcessOfflineState, ProcessStartingState, etc.).
type Status = string

// Process state constants matching daemon/environment:
// ProcessOfflineState, ProcessStartingState, ProcessRunningState, ProcessStoppingState.
const (
	ProcessOfflineState  = "offline"
	ProcessStartingState = "starting"
	ProcessRunningState  = "running"
	ProcessStoppingState = "stopping"
)
