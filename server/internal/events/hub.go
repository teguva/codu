package events

import (
	"context"
	"encoding/json"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
)

const activityLimit = 500

type Event struct {
	Type     string    `json:"type"`
	Job      any       `json:"job,omitempty"`
	Activity *Activity `json:"activity,omitempty"`
}

type Activity struct {
	TS        int64  `json:"ts"`
	Level     string `json:"level"`
	Source    string `json:"source"`
	Type      string `json:"type"`
	Message   string `json:"message"`
	MediaID   string `json:"mediaId,omitempty"`
	JobID     string `json:"jobId,omitempty"`
	SessionID string `json:"sessionId,omitempty"`
}

type Hub struct {
	mu       sync.Mutex
	clients  map[*websocket.Conn]struct{}
	activity []Activity
}

func NewHub() *Hub {
	return &Hub{clients: map[*websocket.Conn]struct{}{}}
}

func (h *Hub) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true,
		OriginPatterns:     []string{"*"},
	})
	if err != nil {
		return
	}
	h.mu.Lock()
	h.clients[conn] = struct{}{}
	h.mu.Unlock()
	defer func() {
		h.mu.Lock()
		delete(h.clients, conn)
		h.mu.Unlock()
		_ = conn.Close(websocket.StatusNormalClosure, "")
	}()
	for {
		if _, _, err := conn.Read(r.Context()); err != nil {
			return
		}
	}
}

func (h *Hub) Broadcast(ev Event) {
	payload, err := json.Marshal(ev)
	if err != nil {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	h.mu.Lock()
	defer h.mu.Unlock()
	for conn := range h.clients {
		if err := conn.Write(ctx, websocket.MessageText, payload); err != nil {
			delete(h.clients, conn)
			_ = conn.CloseNow()
		}
	}
}

func (h *Hub) Record(a Activity) Activity {
	if a.TS == 0 {
		a.TS = time.Now().UnixMilli()
	}
	if a.Level == "" {
		a.Level = "info"
	}
	if a.Source == "" {
		a.Source = "api"
	}
	a.Message = Redact(a.Message)
	h.mu.Lock()
	h.activity = append(h.activity, a)
	if len(h.activity) > activityLimit {
		h.activity = append([]Activity(nil), h.activity[len(h.activity)-activityLimit:]...)
	}
	h.mu.Unlock()
	copy := a
	h.Broadcast(Event{Type: "activity", Activity: &copy})
	return a
}

func (h *Hub) Recent() []Activity {
	h.mu.Lock()
	defer h.mu.Unlock()
	out := make([]Activity, len(h.activity))
	for i := range h.activity {
		out[len(h.activity)-1-i] = h.activity[i]
	}
	return out
}
