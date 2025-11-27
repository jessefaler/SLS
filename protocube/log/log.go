package log

import (
	"os"
	"path/filepath"

	"github.com/NYTimes/logrotate"
	"github.com/apex/log"
	"github.com/apex/log/handlers/multi"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/log/cli"
)

// InitLogging Configures the global Apex logger.
// Logs are written to both the console (with colors) and a rotating log file.
// The log level is set to INFO by default, or DEBUG if enabled in the config.
// This allows logging from anywhere in the codebase without passing logger instances around.
func InitLogging() {
	dir := config.Get().System.LogDirectory
	// Make sure the directory exists
	if err := os.MkdirAll(dir, 0755); err != nil {
		log.Fatalf("log/log: failed to create log directory: %s", err)
	}
	p := filepath.Join(dir, "/protocube.log")
	w, err := logrotate.NewFile(p)
	if err != nil {
		log.Fatalf("log/log: failed to create protocube log: %s", err)
	}
	log.SetLevel(log.InfoLevel)
	if config.Get().Debug {
		log.SetLevel(log.DebugLevel)
	}
	log.SetHandler(multi.New(cli.Default, cli.New(w.File, false)))
	log.WithField("path", p).Info("writing log files to disk")
}
