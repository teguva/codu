package subtitles

import (
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
)

var subExts = map[string]bool{
	".srt": true,
	".ass": true,
	".ssa": true,
	".vtt": true,
	".sub": true,
}

var langSuffix = regexp.MustCompile(`(?i)[._\-]([a-z]{2,3})$`)

// FindSidecars lists subtitle files next to a video, matching predecessor rules.
func FindSidecars(videoPath string) []Track {
	path := strings.TrimSpace(videoPath)
	if path == "" {
		return nil
	}
	info, err := os.Stat(path)
	if err != nil || info.IsDir() {
		return nil
	}
	stem := strings.ToLower(strings.TrimSuffix(filepath.Base(path), filepath.Ext(path)))
	parent := filepath.Dir(path)
	entries, err := os.ReadDir(parent)
	if err != nil {
		return nil
	}
	type cand struct {
		name string
		track Track
	}
	var cands []cand
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := e.Name()
		ext := strings.ToLower(filepath.Ext(name))
		if !subExts[ext] {
			continue
		}
		nameL := strings.ToLower(name)
		childStem := strings.TrimSuffix(nameL, ext)
		if !(childStem == stem ||
			strings.HasPrefix(nameL, stem+".") ||
			strings.HasPrefix(nameL, stem+"_") ||
			strings.Contains(childStem, stem)) {
			continue
		}
		lang := ""
		if m := langSuffix.FindStringSubmatch(childStem); len(m) == 2 {
			lang = strings.ToLower(m[1])
		}
		abs := filepath.Join(parent, name)
		label := name
		if lang != "" {
			label = strings.ToUpper(lang) + " · " + name
		}
		cands = append(cands, cand{
			name: nameL,
			track: Track{
				ID:       "file:" + abs,
				Source:   "sidecar",
				Language: lang,
				Label:    label,
				Path:     abs,
				Filename: name,
			},
		})
	}
	sort.Slice(cands, func(i, j int) bool { return cands[i].name < cands[j].name })
	out := make([]Track, 0, len(cands))
	for _, c := range cands {
		out = append(out, c.track)
	}
	return out
}
