package remote

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"protoxon.com/sls/daemon/api/auth"
	"protoxon.com/sls/daemon/config"
	"protoxon.com/sls/daemon/system"
)

const heartbeatInterval = 10 * time.Second

var connected = false

// Register will send a single register request to protocube
func (c *client) Register(ctx context.Context) error {
	req := NodeRegistration{
		Id:       config.Get().Uuid,
		Name:     config.Get().Name,
		Location: config.Get().Location,
		Url:      config.Get().Api.Url,
		Version:  system.Version,
	}
	bodyBytes, err := json.Marshal(req)
	if err != nil {
		return errors.Wrap(err, "failed to marshal register payload")
	}

	// Use requestOnce directly to avoid recursion
	nodeId := config.Get().Uuid
	resp, err := c.requestOnce(ctx, http.MethodPost, "/api/nodes/"+nodeId+"/register", bytes.NewReader(bodyBytes))
	if err != nil {
		return errors.Wrap(err, "failed to register")
	}
	defer resp.Body.Close()

	// Read response body
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return errors.Wrap(err, "failed to read register response")
	}

	// Parse JSON
	var data map[string]interface{}
	if err := json.Unmarshal(body, &data); err != nil {
		return errors.Wrap(err, "failed to parse register response")
	}

	// Extract token
	token, ok := data["token"].(string)
	if !ok {
		return errors.New("register response missing token field or wrong type")
	}

	// Set token in auth
	if err := auth.SetToken(token); err != nil {
		return errors.Wrap(err, "failed to set auth token")
	}

	return nil
}

// Heartbeat sends a heartbeat request to protocube
// Logs any errors directly to console
func (c *client) Heartbeat(ctx context.Context) {
	heartbeat := HeartBeat{}
	_, err := Post[d](c, ctx, "/internal/heartbeat", heartbeat)
	if err == nil {
		if connected == false {
			connected = true
			log.Info("Successfully registered with protocube.")
		}
	}
	if err != nil {
		connected = false
		log.WithError(err).Error("failed to connect to protocube: heartbeat failed")
	}
}

// Disconnect sends a disconnect request to protocube if connected
// And cancels the sending of heartbeats
func (c *client) Disconnect(ctx context.Context) {
	c.cancel() // Cancel the clients main context to stop sending heartbeats
	if connected {
		_, _ = Post[d](c, ctx, "/internal/disconnect", nil)
	}
}

// StartHeartbeats starts a goroutine that sends heartbeats at the specified interval.
func (c *client) StartHeartbeats() {
	ctx := c.ctx
	go func() {
		c.Heartbeat(ctx)
		ticker := time.NewTicker(heartbeatInterval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				c.Heartbeat(ctx)
			}
		}
	}()
}
