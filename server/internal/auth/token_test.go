package auth

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestBearerAdminHTMLPublic(t *testing.T) {
	h := Bearer("secret")(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	cases := []struct {
		path string
		want int
	}{
		{"/", http.StatusOK},
		{"/assets/index.js", http.StatusOK},
		{"/health", http.StatusOK},
		{"/api/v1/library", http.StatusUnauthorized},
		{"/ws", http.StatusUnauthorized},
	}
	for _, tc := range cases {
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, tc.path, nil))
		if rec.Code != tc.want {
			t.Fatalf("%s: got %d want %d", tc.path, rec.Code, tc.want)
		}
	}
}

func TestBearerAcceptsQueryToken(t *testing.T) {
	h := Bearer("secret")(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/ws?token=secret", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("got %d", rec.Code)
	}
}
