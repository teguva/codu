package jobs

// Package jobs will own the acquire state machine in Phase 5.
// v1 only exposes an empty list so admin/TV can bind to a stable shape.
type Job struct {
	ID                 string  `json:"id"`
	Type               string  `json:"type"`
	Status             string  `json:"status"`
	Progress           float64 `json:"progress"`
	Ready              bool    `json:"ready"`
	ExpectedDurationMs int64   `json:"expectedDurationMs,omitempty"`
	BufferedMs         int64   `json:"bufferedMs,omitempty"`
}
