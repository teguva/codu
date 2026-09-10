package acquire

import (
	"context"
	"io"
	"os/exec"
)

func Pipe(ctx context.Context, ytdlp, pageURL string) (io.ReadCloser, func() error, error) {
	if ytdlp == "" {
		ytdlp = "yt-dlp"
	}
	cmd := exec.CommandContext(ctx, ytdlp,
		"--no-playlist",
		"--no-warnings",
		"-f", "b[ext=mp4]/bv*+ba/b",
		"-o", "-",
		"--",
		pageURL,
	)
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, nil, err
	}
	cmd.Stderr = io.Discard
	if err := cmd.Start(); err != nil {
		return nil, nil, err
	}
	return stdout, cmd.Wait, nil
}
