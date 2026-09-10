package events

import "regexp"

var (
	bearerRe  = regexp.MustCompile(`(?i)(bearer\s+)[A-Za-z0-9._\-]+`)
	rdEqRe    = regexp.MustCompile(`(?i)(realdebrid(?:token|apikey)?[=:/])[A-Za-z0-9._\-]+`)
	tokenEqRe = regexp.MustCompile(`(?i)((?:api[_-]?token|access_token|auth_token|\btoken)=)[A-Za-z0-9._\-]+`)
)

// Redact strips Real-Debrid and bearer tokens from log lines and URLs.
func Redact(s string) string {
	s = bearerRe.ReplaceAllString(s, "${1}***")
	s = rdEqRe.ReplaceAllString(s, "${1}***")
	s = tokenEqRe.ReplaceAllString(s, "${1}***")
	return s
}
