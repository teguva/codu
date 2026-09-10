package auth

import (
	"crypto/subtle"
	"net/http"
	"strings"
)

// Bearer authenticates API requests with a shared secret.
// Health checks are always public. If token is empty, all requests are allowed (dev mode).
func Bearer(token string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if token == "" || isPublic(r.URL.Path) {
				next.ServeHTTP(w, r)
				return
			}
			got := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
			got = strings.TrimSpace(got)
			if got == "" {
				got = strings.TrimSpace(r.URL.Query().Get("token"))
			}
			if subtle.ConstantTimeCompare([]byte(got), []byte(token)) != 1 {
				http.Error(w, `{"error":"unauthorized"}`, http.StatusUnauthorized)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func isPublic(path string) bool {
	return path == "/health" || path == "/health/"
}
