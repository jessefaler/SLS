package log

import (
	"os"
	"path"
	"path/filepath"

	"github.com/NYTimes/logrotate"
	"github.com/apex/log"
	"github.com/apex/log/handlers/multi"
)

// InitLogging Configures the global Apex logger.
// Logs are written to both the console (with colors) and a rotating log file.
// The log level is set to INFO by default, or DEBUG if enabled in the config.
// This allows logging from anywhere in the codebase without passing logger instances around.
func InitLogging() {
	dir := config.Get().System.LogDirectory
	if err := os.MkdirAll(path.Join(dir, "/install"), 0o700); err != nil {
		log.Fatalf("log/log: failed to create install directory path: %s", err)
	}
	p := filepath.Join(dir, "/sls.log")
	w, err := logrotate.NewFile(p)
	if err != nil {
		log.Fatalf("log/log: failed to create sls log: %s", err)
	}
	log.SetLevel(log.InfoLevel)
	if config.Get().Debug {
		log.SetLevel(log.DebugLevel)
	}
	log.SetHandler(multi.New(cli.Default, cli.New(w.File, false)))
	log.WithField("path", p).Info("writing log files to disk")
}
