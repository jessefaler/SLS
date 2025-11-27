package cli

import (
	"fmt"
	"io"
	"os"
	"strings"
	"sync"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/apex/log/handlers/cli"
	color2 "github.com/fatih/color"
	"github.com/mattn/go-colorable"
)

var (
	Default = New(os.Stderr, true)
	bold    = color2.New(color2.Bold)
	boldred = color2.New(color2.Bold, color2.FgRed)
)

var Strings = [...]string{
	log.DebugLevel: "DEBUG",
	log.InfoLevel:  " INFO",
	log.WarnLevel:  " WARN",
	log.ErrorLevel: "ERROR",
	log.FatalLevel: "FATAL",
}

type Handler struct {
	mu      sync.Mutex
	Writer  io.Writer
	Padding int
}

// New creates a log Handler
// useColors controls whether ANSI color codes are included when writing to a file.
func New(w io.Writer, useColors bool) *Handler {
	if f, ok := w.(*os.File); ok {
		if useColors {
			return &Handler{Writer: colorable.NewColorable(f), Padding: 2}
		}
	}

	return &Handler{Writer: colorable.NewNonColorable(w), Padding: 2}
}

// HandleLog implements log.Handler.
func (h *Handler) HandleLog(e *log.Entry) error {
	color := cli.Colors[e.Level]
	level := Strings[e.Level]
	names := e.Fields.Names()

	// Check if a plugin name is present
	var pluginName string
	if v := e.Fields.Get("plugin"); v != nil {
		if s, ok := v.(string); ok {
			pluginName = s
		}
	}

	h.mu.Lock()
	defer h.mu.Unlock()

	// Print level, timestamp, and optionally plugin name
	if pluginName != "" {
		color.Fprintf(h.Writer, "%s: [%s] [%s] %-25s",
			bold.Sprintf("%*s", h.Padding+1, level),
			time.Now().Format(time.StampMilli),
			pluginName,
			e.Message)
	} else {
		color.Fprintf(h.Writer, "%s: [%s] %-25s",
			bold.Sprintf("%*s", h.Padding+1, level),
			time.Now().Format(time.StampMilli),
			e.Message)
	}

	// Print other fields except "source" and "plugin"
	for _, name := range names {
		if name == "source" || name == "plugin" {
			continue
		}
		fmt.Fprintf(h.Writer, " %s=%v", color.Sprint(name), e.Fields.Get(name))
	}

	fmt.Fprintln(h.Writer)

	// Handle error stacktrace exactly as in original
	for _, name := range names {
		if name != "error" {
			continue
		}

		if err, ok := e.Fields.Get("error").(error); ok {
			// Attach the stacktrace if it is missing at this point, but don't point
			// it specifically to this line since that is irrelevant.
			err = errors.WithStackDepthIf(err, 4)
			formatted := fmt.Sprintf("\n%s\n%+v\n\n", boldred.Sprintf("Stacktrace:"), err)

			if !strings.Contains(formatted, "runtime.goexit") {
				_, _ = fmt.Fprint(h.Writer, formatted)
				break
			}

			// Inserts a new-line between sections of a stack.
			var b strings.Builder
			var endOfStack bool
			for _, s := range strings.Split(formatted, "\n") {
				b.WriteString(s + "\n")

				if s == "runtime.goexit" {
					endOfStack = true
					continue
				}

				if !endOfStack {
					continue
				}

				b.WriteString("\n")
				endOfStack = false
			}

			_, _ = fmt.Fprint(h.Writer, b.String())
		}

		// Only one key with the name "error" can be in the map.
		break
	}

	return nil
}
