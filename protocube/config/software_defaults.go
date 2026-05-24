package config

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/apex/log"
)

// Upstream default software YAMLs live in the public SLS repo:
// https://github.com/jessefaler/SLS/tree/main/software
const (
	ghDefaultSoftwareOwner = "jessefaler"
	ghDefaultSoftwareRepo  = "SLS"
	ghDefaultSoftwarePath  = "software"
	ghDefaultSoftwareRef   = "main"
)

type ghContentEntry struct {
	Name        string `json:"name"`
	Type        string `json:"type"`
	DownloadURL string `json:"download_url"`
}

// syncDefaultSoftwareYAMLs pulls YAML definitions from the upstream repository.
// Called only when writeDefaultConfig creates the main config; skips files that
// already exist in softwareDir.
func syncDefaultSoftwareYAMLs(softwareDir string) {
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()

	listURL := "https://api.github.com/repos/" + ghDefaultSoftwareOwner + "/" + ghDefaultSoftwareRepo +
		"/contents/" + ghDefaultSoftwarePath + "?ref=" + ghDefaultSoftwareRef

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, listURL, nil)
	if err != nil {
		log.WithError(err).Warn("default software: could not build GitHub API request")
		return
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("X-GitHub-Api-Version", "2022-11-28")

	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		log.WithError(err).Warn("default software: failed to list upstream software directory")
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		log.WithField("status", resp.StatusCode).WithField("body", string(body)).Warn("default software: unexpected GitHub API response")
		return
	}

	var entries []ghContentEntry
	if err := json.NewDecoder(resp.Body).Decode(&entries); err != nil {
		log.WithError(err).Warn("default software: failed to decode GitHub API response")
		return
	}

	for _, e := range entries {
		if e.Type != "file" || e.DownloadURL == "" {
			continue
		}
		lower := strings.ToLower(e.Name)
		if !strings.HasSuffix(lower, ".yml") && !strings.HasSuffix(lower, ".yaml") {
			continue
		}
		dest := filepath.Join(softwareDir, e.Name)
		if _, err := os.Stat(dest); err == nil {
			continue
		}
		if err := downloadFile(ctx, e.DownloadURL, dest); err != nil {
			log.WithError(err).WithField("file", e.Name).Warn("default software: failed to download upstream file")
			continue
		}
		log.WithField("file", dest).Info("installed default software definition from upstream repository")
	}
}

func downloadFile(ctx context.Context, url, dest string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		return fmt.Errorf("status %d: %s", resp.StatusCode, body)
	}
	tmp := dest + ".tmp"
	f, err := os.OpenFile(tmp, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o644)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(f, resp.Body)
	closeErr := f.Close()
	if copyErr != nil {
		_ = os.Remove(tmp)
		return copyErr
	}
	if closeErr != nil {
		_ = os.Remove(tmp)
		return closeErr
	}
	return os.Rename(tmp, dest)
}
