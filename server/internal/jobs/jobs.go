package jobs

import (
	"bufio"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

const (
	TypeYTDLP  = "ytdlp"
	TypeHTTP   = "http"
	TypeDebrid = "debrid"

	StatusQueued      = "queued"
	StatusDownloading = "downloading"
	StatusReady       = "ready"
	StatusFinished    = "finished"
	StatusError       = "error"
	StatusCancelled   = "cancelled"
	StatusPaused      = "paused"

	PlaylistName = "index.m3u8"
	SegmentTimeS = 4
)

func Dir(dataPath, id string) string {
	return filepath.Join(dataPath, "jobs", id)
}

func HLSDir(dataPath, id string) string {
	return filepath.Join(Dir(dataPath, id), "hls")
}

func SourcePath(dataPath, id string) string {
	return filepath.Join(Dir(dataPath, id), "source.ts")
}

func PlaylistPath(dataPath, id string) string {
	return filepath.Join(HLSDir(dataPath, id), PlaylistName)
}

func SafeName(title string) string {
	title = strings.TrimSpace(title)
	if title == "" {
		return "Untitled"
	}
	repl := strings.NewReplacer(
		"/", "-", "\\", "-", ":", " -", "?", "", "*", "", "\"", "",
		"<", "", ">", "", "|", "", "\n", " ", "\r", "",
	)
	title = strings.Join(strings.Fields(repl.Replace(title)), " ")
	if len(title) > 120 {
		title = strings.TrimSpace(title[:120])
	}
	if title == "" {
		return "Untitled"
	}
	return title
}

func PlaylistBufferedMs(playlistPath string) (int64, int) {
	f, err := os.Open(playlistPath)
	if err != nil {
		return 0, 0
	}
	defer f.Close()
	var total float64
	segments := 0
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if !strings.HasPrefix(line, "#EXTINF:") {
			continue
		}
		payload := strings.TrimPrefix(line, "#EXTINF:")
		payload, _, _ = strings.Cut(payload, ",")
		sec, err := strconv.ParseFloat(strings.TrimSpace(payload), 64)
		if err != nil {
			continue
		}
		total += sec
		segments++
	}
	return int64(total * 1000), segments
}

// DownloadProgress is 0–1 from bytes and/or buffered vs expected duration.
func DownloadProgress(bufferedMs, expectedMs, bytesHave, bytesTotal int64) float64 {
	var p float64
	if bytesTotal > 0 && bytesHave > 0 {
		p = float64(bytesHave) / float64(bytesTotal)
	}
	if expectedMs > 0 && bufferedMs > 0 {
		if t := float64(bufferedMs) / float64(expectedMs); t > p {
			p = t
		}
	}
	if p < 0 {
		return 0
	}
	if p > 0.99 {
		return 0.99
	}
	return p
}

func AppendEndList(playlistPath string) error {
	b, err := os.ReadFile(playlistPath)
	if err != nil {
		return err
	}
	text := string(b)
	if strings.Contains(text, "#EXT-X-ENDLIST") {
		return nil
	}
	if !strings.HasSuffix(text, "\n") {
		text += "\n"
	}
	return os.WriteFile(playlistPath, []byte(text+"#EXT-X-ENDLIST\n"), 0o644)
}
