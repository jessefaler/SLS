package telemetry

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"time"

	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/node"
	"protoxon.com/sls/protocube/server"
	"protoxon.com/sls/protocube/system"
)

const endpoint = "https://protoxon.com/sls/telemetry"

// Payload is the JSON body sent to the telemetry endpoint.
type Payload struct {
	Version string `json:"version"`
	Servers struct {
		Total   int `json:"total"`
		Running int `json:"running"`
		Stopped int `json:"stopped"`
	} `json:"servers"`
	Nodes int `json:"nodes"`
}

// Start periodically reports protocube state to the configured endpoint.
// The first report runs immediately; subsequent reports run every interval.
func Start(ctx context.Context, sm *server.Manager, nm *node.Manager, interval time.Duration) {
	if interval <= 0 {
		interval = 15 * time.Minute
	}
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		send := func() {
			cctx, cancel := context.WithTimeout(ctx, 30*time.Second)
			defer cancel()
			_ = sendOnce(cctx, sm, nm)
		}

		send()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				send()
			}
		}
	}()
}

func sendOnce(ctx context.Context, sm *server.Manager, nm *node.Manager) error {
	var payload Payload
	payload.Version = system.Version

	all := sm.All()
	payload.Servers.Total = len(all)
	for _, s := range all {
		switch s.GetStatus() {
		case server.ProcessRunningState:
			payload.Servers.Running++
		default:
			payload.Servers.Stopped++
		}
	}

	payload.Nodes = len(nm.GetNodes())

	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", "sls-protocube/"+system.Version)
	if id := config.Get().Uuid; id != "" {
		req.Header.Set("X-SLS-UUID", id)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer func() { _ = resp.Body.Close() }()
	return nil
}
