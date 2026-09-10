package playback

import (
	"testing"

	"coog/internal/store"
)

func TestNegotiateNoProfileDirect(t *testing.T) {
	got, err := Negotiate(store.MediaItem{CodecVideo: "hevc", CodecAudio: "truehd"}, nil)
	if err != nil {
		t.Fatal(err)
	}
	if got.Method != MethodDirect {
		t.Fatalf("method %s", got.Method)
	}
}

func TestNegotiateTrueHDTriesDirect(t *testing.T) {
	caps := &Capabilities{VideoCodecs: []string{"hevc"}, AudioCodecs: []string{"aac"}}
	got, err := Negotiate(store.MediaItem{CodecVideo: "hevc", CodecAudio: "truehd"}, caps)
	if err != nil {
		t.Fatal(err)
	}
	if got.Method != MethodDirect {
		t.Fatalf("method %s reason %s", got.Method, got.Reason)
	}
}

func TestNegotiateUnknownVideoErrors(t *testing.T) {
	caps := &Capabilities{VideoCodecs: []string{"h264"}}
	_, err := Negotiate(store.MediaItem{CodecVideo: "mpeg2video"}, caps)
	if err == nil {
		t.Fatal("expected transcode error")
	}
}
