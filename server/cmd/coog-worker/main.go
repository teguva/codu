package main

import (
	"fmt"
	"os"
)

func main() {
	fmt.Fprintln(os.Stderr, "coog-worker is a placeholder until Phase 5 (acquire + progressive play).")
	fmt.Fprintln(os.Stderr, "The API binary (coog-api) is the native Linux entry point for Phases 0–4.")
	os.Exit(0)
}
