package node

import (
	"time"

	"github.com/apex/log"
)

const timeout = time.Second * 15

type Health struct {
	LastSeen time.Time `json:"last_seen"`
	Online   bool      `json:"online"`
}

func (n *Node) OnHeartBeat() {
	n.LastSeen = time.Now()
}

func (n *Node) IsOnline() bool {
	return n.Online
}

func (n *Node) IsOffline() bool {
	return !n.Online
}

func (m *Manager) StartHealthMonitor() {
	go func() {
		ticker := time.NewTicker(timeout / 2)
		defer ticker.Stop()
		for range ticker.C {
			now := time.Now()
			var timedOut []*Node

			m.mutex.RLock()
			for _, n := range m.nodes {
				n.mutex.Lock()
				if now.Sub(n.LastSeen) > timeout {
					n.Online = false
					timedOut = append(timedOut, n)
					log.WithField("node", n.id).Infof("Node disconnected. Reason: %s", "Timeout.")
				}
				n.mutex.Unlock()
			}
			m.mutex.RUnlock()

			for _, node := range timedOut {
				m.Disconnect(node)
			}
		}
	}()
}
