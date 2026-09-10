package probe

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

type Prober struct {
	ffmpeg  string
	ffprobe string
}

type Info struct {
	VideoCodec string
	AudioCodec string
	Width      int
	Height     int
	HDR        string
	DurationMs int64
	Raw        json.RawMessage
}

func New(ffmpeg, ffprobe string) *Prober {
	return &Prober{ffmpeg: ffmpeg, ffprobe: ffprobe}
}

func (p *Prober) FFmpeg() string  { return p.ffmpeg }
func (p *Prober) FFprobe() string { return p.ffprobe }

func (p *Prober) Version(ctx context.Context) string {
	ctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, p.ffmpeg, "-version")
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	line, _, _ := strings.Cut(string(out), "\n")
	return strings.TrimSpace(line)
}

func (p *Prober) Duration(ctx context.Context, src string) (int64, error) {
	if strings.TrimSpace(src) == "" {
		return 0, fmt.Errorf("empty probe source")
	}
	ctx, cancel := context.WithTimeout(ctx, 12*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, p.ffprobe,
		"-v", "quiet",
		"-probesize", "4M",
		"-analyzeduration", "4M",
		"-show_entries", "format=duration",
		"-of", "csv=p=0",
		src,
	)
	out, err := cmd.Output()
	if err != nil {
		return 0, err
	}
	secs, err := strconv.ParseFloat(strings.TrimSpace(string(out)), 64)
	if err != nil || secs <= 0 {
		return 0, fmt.Errorf("no duration")
	}
	return int64(secs * 1000), nil
}

func (p *Prober) Probe(ctx context.Context, path string) (Info, error) {
	ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, p.ffprobe,
		"-v", "quiet",
		"-print_format", "json",
		"-show_format",
		"-show_streams",
		path,
	)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	if err := cmd.Run(); err != nil {
		return Info{}, fmt.Errorf("ffprobe: %w (%s)", err, strings.TrimSpace(stderr.String()))
	}
	raw := json.RawMessage(bytes.TrimSpace(stdout.Bytes()))
	var parsed ffprobeResult
	if err := json.Unmarshal(raw, &parsed); err != nil {
		return Info{}, err
	}
	info := Info{Raw: raw}
	if parsed.Format.Duration != "" {
		if secs, err := strconv.ParseFloat(parsed.Format.Duration, 64); err == nil {
			info.DurationMs = int64(secs * 1000)
		}
	}
	for _, stream := range parsed.Streams {
		switch stream.CodecType {
		case "video":
			if stream.Disposition.AttachedPic == 1 {
				continue
			}
			if info.VideoCodec == "" {
				info.VideoCodec = normalizeCodec(stream.CodecName)
				info.Width = stream.Width
				info.Height = stream.Height
				info.HDR = detectHDR(stream)
			}
		case "audio":
			if info.AudioCodec == "" {
				info.AudioCodec = normalizeCodec(stream.CodecName)
			}
		}
	}
	return info, nil
}

type ffprobeResult struct {
	Streams []ffStream `json:"streams"`
	Format  ffFormat   `json:"format"`
}

type ffFormat struct {
	Duration string `json:"duration"`
}

type ffStream struct {
	CodecType      string            `json:"codec_type"`
	CodecName      string            `json:"codec_name"`
	Width          int               `json:"width"`
	Height         int               `json:"height"`
	ColorTransfer  string            `json:"color_transfer"`
	ColorPrimaries string            `json:"color_primaries"`
	Disposition    ffDisposition     `json:"disposition"`
	SideDataList   []ffSideData      `json:"side_data_list"`
	Tags           map[string]string `json:"tags"`
}

type ffDisposition struct {
	AttachedPic int `json:"attached_pic"`
}

type ffSideData struct {
	Type string `json:"side_data_type"`
}

func detectHDR(s ffStream) string {
	for _, sd := range s.SideDataList {
		if strings.Contains(strings.ToLower(sd.Type), "dovi") || strings.Contains(strings.ToLower(sd.Type), "dolby vision") {
			return "dolbyvision"
		}
	}
	switch strings.ToLower(s.ColorTransfer) {
	case "smpte2084":
		return "hdr10"
	case "arib-std-b67":
		return "hlg"
	}
	for _, v := range s.Tags {
		lv := strings.ToLower(v)
		if strings.Contains(lv, "dolby vision") || strings.Contains(lv, "dovi") {
			return "dolbyvision"
		}
		if strings.Contains(lv, "hdr10") {
			return "hdr10"
		}
	}
	return ""
}

func normalizeCodec(name string) string {
	switch strings.ToLower(name) {
	case "h264", "avc":
		return "h264"
	case "hevc", "h265":
		return "hevc"
	case "av1":
		return "av1"
	case "vp9":
		return "vp9"
	case "vp8":
		return "vp8"
	case "aac":
		return "aac"
	case "ac3":
		return "ac3"
	case "eac3":
		return "eac3"
	case "truehd":
		return "truehd"
	case "dts":
		return "dts"
	case "opus":
		return "opus"
	case "flac":
		return "flac"
	default:
		return strings.ToLower(name)
	}
}
