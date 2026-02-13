package websocket

import (
	"net/http"
	"sync"
	"time"

	"github.com/apex/log"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
)

type Handler struct {
	sync.RWMutex `json:"-"`
	Connection   *websocket.Conn `json:"-"`
	uuid         uuid.UUID
}

// GetHandler returns a new websocket handler using the context provided.
func GetHandler(w http.ResponseWriter, r *http.Request, c *gin.Context) (*Handler, error) {
	upgrader := websocket.Upgrader{
		// Ensure that the websocket request is originating from the Panel itself,
		// and not some other location.
		CheckOrigin: func(r *http.Request) bool { return true },
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		return nil, err
	}

	_ = conn.SetReadDeadline(time.Time{})
	conn.SetPongHandler(func(string) error {
		_ = conn.SetReadDeadline(time.Now().Add(90 * time.Second))
		return nil
	})

	u, err := uuid.NewRandom()
	if err != nil {
		return nil, err
	}

	return &Handler{
		Connection: conn,
		uuid:       u,
	}, nil
}

func (h *Handler) Uuid() uuid.UUID {
	return h.uuid
}

func (h *Handler) Logger() *log.Entry {
	return log.WithField("subsystem", "websocket").
		WithField("connection", h.Uuid().String())
}

// SendJson sends a JSON message over the WebSocket connection
func (h *Handler) SendJson(v interface{}) error {
	return h.Connection.WriteJSON(v)
}
