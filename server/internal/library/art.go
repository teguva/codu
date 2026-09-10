package library

import (
	"os"
	"path/filepath"
)

func IsSeasonDir(name string) bool {
	return seasonDirRe.MatchString(name)
}

// ArtDir is where identity and artwork live for a media file: the file's
// folder, or the show folder when the file sits in Season NN.
func ArtDir(mediaPath string) string {
	dir := filepath.Dir(mediaPath)
	if IsSeasonDir(filepath.Base(dir)) {
		return filepath.Dir(dir)
	}
	return dir
}

func CountVideos(dir string) int {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return 0
	}
	n := 0
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		if IsVideo(e.Name()) {
			n++
		}
	}
	return n
}
