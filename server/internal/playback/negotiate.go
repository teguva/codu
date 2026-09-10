package playback

import (
	"fmt"
	"strings"

	"coog/internal/store"
)

type Capabilities struct {
	VideoCodecs []string `json:"videoCodecs"`
	AudioCodecs []string `json:"audioCodecs"`
	Containers  []string `json:"containers"`
	HDR         []string `json:"hdr"`
}

type Result struct {
	Method string
	Reason string
}

const (
	MethodDirect      = "direct"
	MethodRemux       = "remux"
	MethodTranscode   = "transcode"
	MethodProgressive = "progressive"
)

// ConcurrentTranscodeLimit is documented for Phase 6; not enforced yet.
const ConcurrentTranscodeLimit = 2

func Negotiate(item store.MediaItem, caps *Capabilities) (Result, error) {
	if caps == nil || len(caps.VideoCodecs) == 0 {
		return Result{Method: MethodDirect, Reason: "no client profile; try direct play"}, nil
	}
	if item.CodecVideo != "" && !containsCodec(caps.VideoCodecs, item.CodecVideo) {
		return Result{}, fmt.Errorf("transcode required: video codec %s is not in client profile", item.CodecVideo)
	}
	if awkwardAudio(item.CodecAudio) && !containsCodec(caps.AudioCodecs, item.CodecAudio) {
		return Result{
			Method: MethodDirect,
			Reason: "awkward audio " + item.CodecAudio + "; trying direct play (remux not implemented)",
		}, nil
	}
	return Result{Method: MethodDirect, Reason: "client can direct play"}, nil
}

func awkwardAudio(codec string) bool {
	switch strings.ToLower(codec) {
	case "truehd", "dts", "dts-hd", "dtshd", "pcm_bluray", "pcm_s24le":
		return true
	default:
		return false
	}
}

func containsCodec(list []string, codec string) bool {
	want := normalize(codec)
	for _, c := range list {
		if normalize(c) == want {
			return true
		}
		if want == "hevc" && (normalize(c) == "h265" || normalize(c) == "hevc") {
			return true
		}
		if want == "h264" && (normalize(c) == "avc" || normalize(c) == "h264") {
			return true
		}
	}
	return false
}

func normalize(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	s = strings.TrimPrefix(s, "video/")
	s = strings.TrimPrefix(s, "audio/")
	s = strings.ReplaceAll(s, ".", "")
	switch s {
	case "h265", "hev1", "hvc1":
		return "hevc"
	case "avc", "avc1":
		return "h264"
	}
	return s
}
