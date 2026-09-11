package acquire

import (
	"path/filepath"
	"regexp"
	"strings"
)

var videoExts = map[string]bool{
	".mkv": true, ".mp4": true, ".avi": true, ".mov": true, ".m4v": true,
	".wmv": true, ".ts": true, ".m2ts": true, ".webm": true,
}

var tailMetaExts = map[string]bool{
	".mp4": true, ".m4v": true, ".mov": true,
}

var (
	sxxexxRe        = regexp.MustCompile(`(?i)\bs\s*(\d{1,2})\s*[.\-_ ]*\s*[ex]\s*[.\-_ ]*\s*(\d{1,3})\b`)
	nxnnRe          = regexp.MustCompile(`(?i)\b(\d{1,2})\s*x\s*(\d{1,3})\b`)
	seasonEpisodeRe = regexp.MustCompile(`(?i)\bseason\s*[.\-_ ]*\s*(\d{1,2})\s*[.\-_ ]*\s*(?:episode|ep\.?|e)\s*[.\-_ ]*\s*(\d{1,3})\b`)
)

type torrentFileMeta struct {
	Path   string
	Length int64
	Index  int
}

func isVideoPath(path string) bool {
	return videoExts[strings.ToLower(filepath.Ext(path))]
}

func wantsMoovTail(path string) bool {
	return tailMetaExts[strings.ToLower(filepath.Ext(path))]
}

func pickVideoFile(files []torrentFileMeta, filename string, season, episode, preferred int) int {
	if preferred >= 0 && preferred < len(files) {
		f := files[preferred]
		if f.Length > 0 && isVideoPath(f.Path) {
			return preferred
		}
	}
	hint := strings.ToLower(filepath.Base(strings.TrimSpace(filename)))
	var episodeHits []torrentFileMeta
	var nameHits []torrentFileMeta
	var scored []torrentFileMeta
	for _, f := range files {
		if f.Length <= 0 || !isVideoPath(f.Path) {
			continue
		}
		name := strings.ToLower(filepath.Base(f.Path))
		size := f.Length
		if strings.Contains(name, "sample") || strings.Contains(name, "trailer") || strings.Contains(name, "preview") {
			size = size / 20
			if size < 1 {
				size = 1
			}
			f.Length = size
		}
		if season > 0 && episode > 0 && episodeMarkersMatch(f.Path, season, episode) {
			episodeHits = append(episodeHits, f)
			continue
		}
		if hint != "" && (hint == name || strings.Contains(name, hint) || strings.Contains(hint, name)) {
			nameHits = append(nameHits, f)
		}
		scored = append(scored, f)
	}
	if pick := largest(episodeHits); pick >= 0 {
		return pick
	}
	if pick := largest(nameHits); pick >= 0 {
		return pick
	}
	return largest(scored)
}

func largest(files []torrentFileMeta) int {
	best := -1
	var bestSize int64
	for _, f := range files {
		if f.Length > bestSize {
			bestSize = f.Length
			best = f.Index
		}
	}
	return best
}

func episodeMarkersMatch(text string, season, episode int) bool {
	for _, m := range extractEpisodeMarkers(text) {
		if m[0] == season && m[1] == episode {
			return true
		}
	}
	return false
}

func extractEpisodeMarkers(text string) [][2]int {
	seen := map[[2]int]bool{}
	var out [][2]int
	add := func(s, e int) {
		if s < 0 || e < 0 {
			return
		}
		key := [2]int{s, e}
		if seen[key] {
			return
		}
		seen[key] = true
		out = append(out, key)
	}
	for _, m := range sxxexxRe.FindAllStringSubmatch(text, -1) {
		add(atoiSafe(m[1]), atoiSafe(m[2]))
	}
	for _, m := range nxnnRe.FindAllStringSubmatch(text, -1) {
		add(atoiSafe(m[1]), atoiSafe(m[2]))
	}
	for _, m := range seasonEpisodeRe.FindAllStringSubmatch(text, -1) {
		add(atoiSafe(m[1]), atoiSafe(m[2]))
	}
	return out
}

func atoiSafe(s string) int {
	n := 0
	for _, r := range s {
		if r < '0' || r > '9' {
			break
		}
		n = n*10 + int(r-'0')
	}
	return n
}
