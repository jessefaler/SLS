package log

import (
	"fmt"

	apexlog "github.com/apex/log"
)

// log provides a simple facade over Apex Log. The main plugin calls
// SetLogger() to inject the logger, and all subpackages use this wrapper (Info,
// Debug, Error, etc.) instead of relying on the log entry passed through
// OnEnable. This keeps logging consistent and avoids passing logger instances
// throughout the plugin.

var entry *apexlog.Entry = apexlog.WithFields(apexlog.Fields{})

// SetLogger sets the log entry to use for logs
// This should be set using the log entry from the plugin context (plugins.SLS)
func SetLogger(l *apexlog.Entry) {
	if l != nil {
		entry = l
	}
}

func Debug(msg string) {
	entry.Debug(msg)
}

func Info(msg string) {
	entry.Info(msg)
}

func Warn(msg string) {
	entry.Warn(msg)
}

func Error(msg string) {
	entry.Error(msg)
}

func Fatal(msg string) {
	entry.Fatal(msg)
}

func Trace(msg string) {
	entry.Trace(msg)
}

func Debugf(format string, args ...any) {
	entry.Debugf(format, args...)
}

func Infof(format string, args ...any) {
	entry.Infof(format, args...)
}

func Warnf(format string, args ...any) {
	entry.Warnf(format, args...)
}

func Errorf(format string, args ...any) {
	entry.Errorf(format, args...)
}

func Fatalf(format string, args ...any) {
	entry.Fatalf(format, args...)
}

func WithField(key string, value any) *apexlog.Entry {
	return entry.WithField(key, value)
}

func WithFields(fields apexlog.Fields) *apexlog.Entry {
	return entry.WithFields(fields)
}

// Wrap attaches a message to an error and logs it
func Wrap(err error, message string) error {
	wrapped := fmt.Errorf("%s: %w", message, err)
	entry.Error(wrapped.Error())
	return wrapped
}

func Wrapf(err error, format string, args ...any) error {
	wrapped := fmt.Errorf(format+": %w", append(args, err)...)
	entry.Error(wrapped.Error())
	return wrapped
}
