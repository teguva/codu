package streams

import (
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
)

func formatRDHTTPError(status int, path string, payload []byte) error {
	var body struct {
		Error        string `json:"error"`
		ErrorCode    int    `json:"error_code"`
		ErrorDetails string `json:"error_details"`
	}
	_ = json.Unmarshal(payload, &body)
	name := strings.ToLower(strings.TrimSpace(body.Error))
	details := strings.TrimSpace(body.ErrorDetails)
	msg := rdHuman(status, name)
	if details != "" && !containsFold(msg, details) {
		msg += " (" + details + ")"
	}
	if tag := rdErrorTag(msg, status, name, path); tag != "" {
		msg += " · " + tag
	}
	return fmt.Errorf("%s", msg)
}

func formatRDTorrentStatus(status string) error {
	switch strings.ToLower(strings.TrimSpace(status)) {
	case "magnet_error":
		return fmt.Errorf("Real-Debrid could not fetch this magnet (magnet_error). Try another source.")
	case "virus":
		return fmt.Errorf("Real-Debrid marked this torrent as malware (virus). Try another source.")
	case "dead":
		return fmt.Errorf("Real-Debrid says this torrent is dead. Try another source.")
	case "error":
		return fmt.Errorf("Real-Debrid failed while processing this torrent. Try another source.")
	default:
		return fmt.Errorf("Real-Debrid torrent status %s. Try another source.", status)
	}
}

func rdHuman(status int, name string) string {
	switch {
	case status == 451 || name == "infringing_file" || name == "forbidden_file" || name == "upload_forbidden":
		return "Real-Debrid blocked this torrent: the filename or hash is on their blocklist. Pick another source — Cached / RD+ usually work."
	case name == "file_unavailable" || name == "unavailable_file":
		return "Real-Debrid does not have this file available. Pick another source."
	case status == 401 || name == "bad_token" || name == "invalid_token":
		return "Real-Debrid rejected the API token. Update it in Settings."
	case status == 403 || name == "permission_denied":
		return "Real-Debrid denied this request (account not premium, or permission missing)."
	case status == 429 || name == "too_many_requests":
		return "Real-Debrid rate-limited this request. Wait a moment and try again."
	case status == 503 || name == "service_unavailable":
		return "Real-Debrid is temporarily unavailable. Try again in a bit."
	case status == 509 || name == "traffic_exhausted":
		return "Real-Debrid traffic limit reached. Wait for reset or upgrade the account."
	case name == "hoster_unsupported" || name == "unsupported_hoster":
		return "Real-Debrid does not support this host. Pick another source."
	case name != "":
		if status >= 400 {
			return "Real-Debrid request failed (" + name + ", HTTP " + strconv.Itoa(status) + ")."
		}
		return "Real-Debrid request failed (" + name + ")."
	case status >= 400:
		return "Real-Debrid request failed (HTTP " + strconv.Itoa(status) + ")."
	default:
		return "Real-Debrid request failed."
	}
}

func rdErrorTag(msg string, status int, name, path string) string {
	var parts []string
	if name != "" && !containsFold(msg, name) {
		parts = append(parts, name)
	}
	if status >= 400 && !strings.Contains(msg, strconv.Itoa(status)) {
		parts = append(parts, "HTTP "+strconv.Itoa(status))
	}
	if op := rdAPIOp(path); op != "" {
		parts = append(parts, op)
	}
	return strings.Join(parts, ", ")
}

func rdAPIOp(path string) string {
	switch {
	case strings.Contains(path, "addMagnet"):
		return "addMagnet"
	case strings.Contains(path, "selectFiles"):
		return "selectFiles"
	case strings.Contains(path, "/torrents/info"):
		return "torrent info"
	case strings.Contains(path, "unrestrict"):
		return "unrestrict"
	case strings.Contains(path, "/user"):
		return "user"
	default:
		return strings.Trim(path, "/")
	}
}

func annotateRD(err error, cand Candidate) error {
	if err == nil {
		return nil
	}
	label := shortCandidateLabel(cand)
	if label == "" || strings.Contains(err.Error(), label) {
		return err
	}
	return fmt.Errorf("%s · %s", err.Error(), label)
}

func shortCandidateLabel(cand Candidate) string {
	text := strings.TrimSpace(cand.Title)
	if text == "" {
		text = strings.TrimSpace(cand.Name)
	}
	if text == "" {
		return ""
	}
	if i := strings.IndexAny(text, "\r\n"); i >= 0 {
		text = text[:i]
	}
	text = strings.Join(strings.Fields(text), " ")
	if len(text) > 80 {
		text = strings.TrimSpace(text[:80]) + "…"
	}
	return text
}

func containsFold(haystack, needle string) bool {
	return strings.Contains(strings.ToLower(haystack), strings.ToLower(needle))
}

func ShouldFallbackLocal(err error) bool {
	if err == nil {
		return false
	}
	e := strings.ToLower(err.Error())
	if strings.Contains(e, "token") || strings.Contains(e, "rate-limited") || strings.Contains(e, "traffic limit") {
		return false
	}
	for _, token := range []string{
		"infringing", "copyright", "upload_forbidden", "forbidden_file", "blocklist",
		"timed out", "not be cached", "does not have this file", "file_unavailable",
		"magnet_error", "dead", "infringement stub", "stayed on torrentio",
	} {
		if strings.Contains(e, token) {
			return true
		}
	}
	return false
}
