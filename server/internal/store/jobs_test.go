package store

import (
	"path/filepath"
	"testing"
)

func TestJobClaim(t *testing.T) {
	dir := t.TempDir()
	st, err := Open(filepath.Join(dir, "coog.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer st.Close()
	if err := st.InsertJob(Job{ID: "a", Type: "ytdlp", URL: "https://example.com", Status: "queued"}); err != nil {
		t.Fatal(err)
	}
	job, err := st.ClaimNextJob()
	if err != nil {
		t.Fatal(err)
	}
	if job.ID != "a" || job.Status != "downloading" {
		t.Fatalf("%+v", job)
	}
	if _, err := st.ClaimNextJob(); err != ErrNotFound {
		t.Fatalf("expected empty queue, got %v", err)
	}
}

func TestJobLogTailAndCancel(t *testing.T) {
	dir := t.TempDir()
	st, err := Open(filepath.Join(dir, "coog.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer st.Close()
	job := Job{ID: "b", Type: "ytdlp", URL: "https://example.com", Status: "downloading", LogTail: "ffmpeg line"}
	if err := st.InsertJob(job); err != nil {
		t.Fatal(err)
	}
	got, err := st.GetJob("b")
	if err != nil || got.LogTail != "ffmpeg line" {
		t.Fatalf("%+v %v", got, err)
	}
	job.Status = "cancelled"
	if err := st.UpdateJob(job); err != nil {
		t.Fatal(err)
	}
	job.Status = "downloading"
	job.Progress = 0.5
	if err := st.UpdateJob(job); err != nil {
		t.Fatal(err)
	}
	got, err = st.GetJob("b")
	if err != nil || got.Status != "cancelled" {
		t.Fatalf("cancel overwritten: %+v %v", got, err)
	}
	job.Status = "paused"
	if err := st.UpdateJob(job); err != nil {
		t.Fatal(err)
	}
	job.Status = "downloading"
	job.Progress = 0.8
	if err := st.UpdateJob(job); err != nil {
		t.Fatal(err)
	}
	got, err = st.GetJob("b")
	if err != nil || got.Status != "paused" {
		t.Fatalf("pause overwritten: %+v %v", got, err)
	}
	job.Status = "queued"
	if err := st.UpdateJob(job); err != nil {
		t.Fatal(err)
	}
	got, err = st.GetJob("b")
	if err != nil || got.Status != "queued" {
		t.Fatalf("resume failed: %+v %v", got, err)
	}
	if err := st.InsertJob(Job{ID: "c", Type: "debrid", URL: "imdb:tt1", Status: "queued", InfoHash: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", ImdbID: "tt1"}); err != nil {
		t.Fatal(err)
	}
	got, err = st.FindActiveJobByHash("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
	if err != nil || got.ID != "c" {
		t.Fatalf("hash lookup: %+v %v", got, err)
	}

	if err := st.TouchWorkerHeartbeat(42); err != nil {
		t.Fatal(err)
	}
	hb, err := st.WorkerHeartbeat()
	if err != nil || hb.PID != 42 || hb.UpdatedAt == 0 {
		t.Fatalf("%+v %v", hb, err)
	}

	if err := st.DeleteJob("b"); err != nil {
		t.Fatal(err)
	}
	if _, err := st.GetJob("b"); err != ErrNotFound {
		t.Fatalf("expected deleted job, got %v", err)
	}
	if err := st.DeleteJob("missing"); err != ErrNotFound {
		t.Fatalf("expected not found, got %v", err)
	}
}
