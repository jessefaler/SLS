package server

import (
	"encoding/json"
	"runtime"

	"github.com/gammazero/workerpool"
	"protoxon.com/sls/daemon/internal/ufs"
)

// buildServerDataJSON creates a JSON structure containing server information
// that can be used to resolve {{server.X}} placeholders in configuration files.
// The structure matches the format expected by placeholders like:
// - {{server.build.default.port}} -> build.default.port
// - {{server.build.default.ip}} -> build.default.ip
// - {{server.build.memory}} -> build.memory
func (s *Server) buildServerDataJSON() ([]byte, error) {
	cfg := s.Config()
	
	serverData := map[string]interface{}{
		"build": map[string]interface{}{
			"default": map[string]interface{}{
				"ip":   cfg.Allocations.DefaultMapping.Ip,
				"port": cfg.Allocations.DefaultMapping.Port,
			},
			"memory":      cfg.Limits.MemoryLimit,
			"memory_limit": cfg.Limits.MemoryLimit,
			"swap":        cfg.Limits.Swap,
			"cpu":         cfg.Limits.CpuLimit,
			"cpu_limit":   cfg.Limits.CpuLimit,
			"disk":        cfg.Limits.DiskSpace,
			"disk_space":  cfg.Limits.DiskSpace,
			"io_weight":   cfg.Limits.IoWeight,
		},
	}

	return json.Marshal(serverData)
}

// UpdateConfigurationFiles updates all the defined configuration files for
// a server automatically to ensure that they always use the specified values.
func (s *Server) UpdateConfigurationFiles() {
	pool := workerpool.New(runtime.NumCPU())

	s.Log().Debug("acquiring process configuration files...")
	files := s.ProcessConfiguration().ConfigurationFiles
	s.Log().Debug("acquired process configuration files")

	// Build server data JSON once for all files
	serverData, err := s.buildServerDataJSON()
	if err != nil {
		s.Log().WithError(err).Error("failed to build server data for configuration placeholders")
		// Continue with nil server data - placeholders will be left as-is
		serverData = nil
	}

	for _, cf := range files {
		f := cf

		pool.Submit(func() {
			file, err := s.Filesystem().UnixFS().Touch(f.FileName, ufs.O_RDWR|ufs.O_CREATE, 0o644)
			if err != nil {
				s.Log().WithField("file_name", f.FileName).WithField("error", err).Error("failed to open file for configuration")
				return
			}
			defer file.Close()

			if err := f.Parse(file, serverData); err != nil {
				s.Log().WithField("error", err).Error("failed to parse and update server configuration file")
			}

			s.Log().WithField("file_name", f.FileName).Debug("finished processing server configuration file")
		})
	}

	pool.StopWait()
}
