package streams

import (
	"net/url"
	"regexp"
	"strconv"
	"strings"
)

var (
	hashRe             = regexp.MustCompile(`(?i)\b([a-f0-9]{40})\b`)
	torrentioResolveRe = regexp.MustCompile(`(?i)/resolve/[^/]+/[^/]+/([a-f0-9]{40})(?:/[^/]*/(\d+)/([^/?#]+))?`)
)

type torrentioResolve struct {
	InfoHash  string
	FileIndex int
	Filename  string
}

func parseTorrentioResolve(raw string) (torrentioResolve, bool) {
	m := torrentioResolveRe.FindStringSubmatch(strings.TrimSpace(raw))
	if m == nil {
		return torrentioResolve{}, false
	}
	out := torrentioResolve{InfoHash: strings.ToLower(m[1])}
	if m[2] != "" {
		n, err := strconv.Atoi(m[2])
		if err == nil {
			out.FileIndex = n
		}
	}
	if m[3] != "" {
		name, err := url.PathUnescape(m[3])
		if err != nil {
			name = m[3]
		}
		out.Filename = strings.TrimSpace(name)
	}
	return out, true
}

func InfoHash(value string) string {
	text := strings.TrimSpace(value)
	if text == "" {
		return ""
	}
	lower := strings.ToLower(text)
	if strings.HasPrefix(lower, "magnet:") {
		if i := strings.Index(lower, "btih:"); i >= 0 {
			rest := text[i+5:]
			if m := hashRe.FindString(rest); m != "" {
				return strings.ToLower(m)
			}
		}
	}
	if m := hashRe.FindString(text); m != "" {
		return strings.ToLower(m)
	}
	return ""
}

func Magnet(hashOrMagnet string) string {
	text := strings.TrimSpace(hashOrMagnet)
	if strings.HasPrefix(strings.ToLower(text), "magnet:") {
		return text
	}
	h := InfoHash(text)
	if h == "" {
		return ""
	}
	return "magnet:?xt=urn:btih:" + h
}
