package server

import (
	"bufio"
	"context"
	"os"
	"path/filepath"
	"strconv"
	"sync"
	"time"

	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/client"
	"protoxon.com/sls/daemon/environment"
)

type InstallPhase string

const (
	InstallPhaseIdle             InstallPhase = "idle"
	InstallPhaseInstalling       InstallPhase = "installing"
	InstallPhaseWarming          InstallPhase = "warming"
	InstallPhasePostWarmup       InstallPhase = "post_warmup"
	InstallPhaseInstallFailed    InstallPhase = "install_failed"
	InstallPhaseWarmupFailed     InstallPhase = "warmup_failed"
	InstallPhasePostWarmupFailed InstallPhase = "post_warmup_failed"
	InstallPhaseReady            InstallPhase = "ready"
)

type InstallInfo struct {
	Phase         InstallPhase `json:"phase"`
	ContainerID   string       `json:"container_id,omitempty"`
	ContainerName string       `json:"container_name,omitempty"`
	Status        string       `json:"status,omitempty"`
	ExitCode      *int64       `json:"exit_code,omitempty"`
	StartedAt     *time.Time   `json:"started_at,omitempty"`
	FinishedAt    *time.Time   `json:"finished_at,omitempty"`
	FailureReason string       `json:"failure_reason,omitempty"`
	Logs          []string     `json:"logs"`
}

type installState struct {
	mu sync.RWMutex

	phase         InstallPhase
	containerID   string
	containerName string
	status        string
	exitCode      *int64
	startedAt     *time.Time
	finishedAt    *time.Time
	failureReason string
	logPath       string
}

func newInstallState() *installState {
	return &installState{phase: InstallPhaseIdle}
}

func (s *Server) StartInstallPhase(phase InstallPhase, containerName, logPath string) {
	now := time.Now()
	s.installState.mu.Lock()
	defer s.installState.mu.Unlock()
	s.installState.phase = phase
	s.installState.containerID = ""
	s.installState.containerName = containerName
	s.installState.status = ""
	s.installState.exitCode = nil
	s.installState.startedAt = &now
	s.installState.finishedAt = nil
	s.installState.failureReason = ""
	s.installState.logPath = logPath
}

func (s *Server) SetInstallContainer(containerID string) {
	s.installState.mu.Lock()
	defer s.installState.mu.Unlock()
	s.installState.containerID = containerID
	s.installState.status = "created"
}

func (s *Server) SetInstallStatus(status string) {
	s.installState.mu.Lock()
	defer s.installState.mu.Unlock()
	s.installState.status = status
}

func (s *Server) SetInstallExitCode(code int64) {
	s.installState.mu.Lock()
	defer s.installState.mu.Unlock()
	s.installState.exitCode = &code
}

func (s *Server) FinishInstallPhase(phase InstallPhase, failureReason string) {
	now := time.Now()
	s.installState.mu.Lock()
	defer s.installState.mu.Unlock()
	s.installState.phase = phase
	s.installState.finishedAt = &now
	s.installState.failureReason = failureReason
	if phase == InstallPhaseReady && s.installState.exitCode == nil {
		var code int64
		s.installState.exitCode = &code
	}
}

func (s *Server) InstallInfo(ctx context.Context, lines int) InstallInfo {
	if lines <= 0 {
		lines = 100
	} else if lines > 500 {
		lines = 500
	}

	info := s.installInfoSnapshot()
	s.enrichInstallInfoFromDocker(ctx, &info, lines)
	if len(info.Logs) == 0 {
		info.Logs = readTailLines(s.installLogPath(), lines)
	}
	if len(info.Logs) == 0 {
		info.Logs = []string{}
	}
	return info
}

func (s *Server) installInfoSnapshot() InstallInfo {
	s.installState.mu.RLock()
	defer s.installState.mu.RUnlock()
	return InstallInfo{
		Phase:         s.installState.phase,
		ContainerID:   s.installState.containerID,
		ContainerName: s.installState.containerName,
		Status:        s.installState.status,
		ExitCode:      cloneInt64(s.installState.exitCode),
		StartedAt:     cloneTime(s.installState.startedAt),
		FinishedAt:    cloneTime(s.installState.finishedAt),
		FailureReason: s.installState.failureReason,
	}
}

func (s *Server) installLogPath() string {
	s.installState.mu.RLock()
	path := s.installState.logPath
	s.installState.mu.RUnlock()
	return path
}

func (s *Server) enrichInstallInfoFromDocker(ctx context.Context, info *InstallInfo, lines int) {
	containerRef := info.ContainerID
	if containerRef == "" {
		containerRef = info.ContainerName
	}
	if containerRef == "" {
		return
	}

	cli, err := environment.Docker()
	if err != nil {
		return
	}

	inspect, err := cli.ContainerInspect(ctx, containerRef)
	if err != nil {
		if client.IsErrNotFound(err) {
			return
		}
		return
	}

	info.ContainerID = inspect.ID
	if info.ContainerName == "" && inspect.Name != "" {
		info.ContainerName = inspect.Name
	}
	if inspect.State != nil {
		info.Status = inspect.State.Status
		code := int64(inspect.State.ExitCode)
		if inspect.State.Status == "exited" || inspect.State.Status == "dead" {
			info.ExitCode = &code
		}
		if info.StartedAt == nil && inspect.State.StartedAt != "" {
			if t, err := time.Parse(time.RFC3339Nano, inspect.State.StartedAt); err == nil && !t.IsZero() {
				info.StartedAt = &t
			}
		}
		if info.FinishedAt == nil && inspect.State.FinishedAt != "" {
			if t, err := time.Parse(time.RFC3339Nano, inspect.State.FinishedAt); err == nil && !t.IsZero() {
				info.FinishedAt = &t
			}
		}
	}

	reader, err := cli.ContainerLogs(ctx, containerRef, container.LogsOptions{
		ShowStdout: true,
		ShowStderr: true,
		Tail:       strconv.Itoa(lines),
	})
	if err != nil {
		return
	}
	defer reader.Close()

	scanner := bufio.NewScanner(reader)
	for scanner.Scan() {
		info.Logs = append(info.Logs, scanner.Text())
	}
}

func readTailLines(path string, max int) []string {
	if path == "" {
		return nil
	}
	f, err := os.Open(filepath.Clean(path))
	if err != nil {
		return nil
	}
	defer f.Close()

	var lines []string
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		lines = append(lines, scanner.Text())
		if len(lines) > max {
			lines = lines[len(lines)-max:]
		}
	}
	return lines
}

func cloneInt64(v *int64) *int64 {
	if v == nil {
		return nil
	}
	c := *v
	return &c
}

func cloneTime(v *time.Time) *time.Time {
	if v == nil {
		return nil
	}
	c := *v
	return &c
}
