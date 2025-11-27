package router

import (
	"context"
	"time"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	ws "github.com/gorilla/websocket"
	"protoxon.com/sls/protocube/api/router/middleware"
	"protoxon.com/sls/protocube/api/router/websocket"
	"protoxon.com/sls/protocube/events"
)

var expectedCloseCodes = []int{
	ws.CloseGoingAway,
	ws.CloseAbnormalClosure,
	ws.CloseNormalClosure,
	ws.CloseNoStatusReceived,
	ws.CloseServiceRestart,
}

// getServerWebsocket upgrades a connection to a websocket and streams status updates
// It works the same as getEventStream but sends events over WebSocket instead of SSE
func (r *Router) getServerWebsocket(c *gin.Context) {
	log.Info("opened websocket event stream")

	handler, err := websocket.GetHandler(c.Writer, c.Request, c)
	if err != nil {
		middleware.CaptureAndAbort(c, err)
		return
	}
	defer handler.Connection.Close()

	// Dedicated context so HTTP server timeouts don't kill the WebSocket
	wsCtx, wsCancel := context.WithCancel(context.Background())
	defer wsCancel()

	// Remove deadlines so the connection can stay open indefinitely
	handler.Connection.SetReadDeadline(time.Time{})
	handler.Connection.SetWriteDeadline(time.Now().Add(60 * time.Second))
	if conn := handler.Connection.UnderlyingConn(); conn != nil {
		_ = conn.SetReadDeadline(time.Time{})
		_ = conn.SetWriteDeadline(time.Time{})
	}

	// Create a sink channel for this client
	ch := make(chan []byte, 32)

	// Subscribe to the global event bus
	r.ServerManager.Events().On(ch)
	// Unsubscribe when client disconnects
	defer r.ServerManager.Events().Off(ch)

	// Ping ticker used to keep connection alive
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	// Read goroutine to consume control frames (pong/close)
	errChan := make(chan error, 1)
	go func() {
		for {
			_, _, readErr := handler.Connection.ReadMessage()
			if readErr != nil {
				if ws.IsUnexpectedCloseError(readErr, expectedCloseCodes...) {
					errChan <- readErr
				} else {
					errChan <- nil
				}
				return
			}
		}
	}()

	for {
		select {
		case <-wsCtx.Done():
			log.Info("websocket stream closed (context canceled)")
			return

		case err := <-errChan:
			if err != nil {
				log.WithError(err).Debug("websocket read error")
			} else {
				log.Debug("websocket connection closed by client")
			}
			return

		case <-ticker.C:
			handler.Connection.SetWriteDeadline(time.Now().Add(60 * time.Second))
			if err := handler.Connection.WriteMessage(ws.PingMessage, nil); err != nil {
				log.WithError(err).Debug("websocket ping failed")
				return
			}

		case data := <-ch:
			ev := events.MustDecode(data)

			handler.Connection.SetWriteDeadline(time.Now().Add(60 * time.Second))

			if err := handler.Connection.WriteJSON(ev); err != nil {
				if ws.IsUnexpectedCloseError(err, expectedCloseCodes...) {
					log.WithError(err).Warn("websocket write error")
				}
				return
			}
		}
	}
}
