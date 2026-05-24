package software

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"

	"emperror.dev/errors"
	"github.com/apex/log"
	"gopkg.in/yaml.v3"
)

// maybeRefreshSoftwareFromURL replaces the on-disk file when update is enabled and the
// remote body differs from the local file. Remote content must pass the same YAML
// validation as local software definitions.
func maybeRefreshSoftwareFromURL(path string, data *[]byte, cfg *config) error {
	s := &cfg.Software
	if s.Update == nil || !s.Update.Enabled || strings.TrimSpace(s.Update.URL) == "" {
		return nil
	}

	fetchURL, err := resolveSoftwareUpdateURL(strings.TrimSpace(s.Update.URL))
	if err != nil {
		return err
	}

	remote, err := httpGetBody(fetchURL)
	if err != nil {
		return err
	}

	if bytes.Equal(normalizeLineEndings(*data), normalizeLineEndings(remote)) {
		return nil
	}

	var probe config
	if err := yaml.Unmarshal(remote, &probe); err != nil {
		return errors.Wrap(err, "remote software YAML is invalid")
	}

	mode := os.FileMode(0o644)
	if fi, err := os.Stat(path); err == nil {
		mode = fi.Mode() & 0o777
	}

	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, remote, mode); err != nil {
		return err
	}
	if err := os.Rename(tmp, path); err != nil {
		_ = os.Remove(tmp)
		return err
	}

	newData, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	*data = newData
	if err := yaml.Unmarshal(*data, cfg); err != nil {
		return err
	}

	log.WithField("file", path).WithField("url", fetchURL).Info("updated software definition from remote URL")
	return nil
}

func normalizeLineEndings(b []byte) []byte {
	b = bytes.ReplaceAll(b, []byte("\r\n"), []byte("\n"))
	return bytes.ReplaceAll(b, []byte("\r"), []byte("\n"))
}

// resolveSoftwareUpdateURL returns a fetchable URL. GitHub repository browser URLs
// (…/blob/…) are converted to raw.githubusercontent.com; other http(s) URLs are unchanged.
func resolveSoftwareUpdateURL(raw string) (string, error) {
	u, err := url.Parse(raw)
	if err != nil {
		return "", err
	}
	switch u.Scheme {
	case "https", "http":
	default:
		return "", fmt.Errorf("unsupported URL scheme %q", u.Scheme)
	}

	host := strings.ToLower(strings.TrimPrefix(u.Host, "www."))
	if host != "github.com" {
		return raw, nil
	}

	parts := strings.Split(strings.TrimPrefix(u.Path, "/"), "/")
	if len(parts) < 5 || parts[2] != "blob" {
		return raw, nil
	}

	owner, repo, ref := parts[0], parts[1], parts[3]
	subPath := strings.Join(parts[4:], "/")
	if owner == "" || repo == "" || ref == "" || subPath == "" {
		return "", errors.New("invalid GitHub blob URL")
	}

	return fmt.Sprintf("https://raw.githubusercontent.com/%s/%s/%s/%s", owner, repo, ref, subPath), nil
}

func httpGetBody(fetchURL string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, fetchURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "protocube-software-update")

	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return nil, fmt.Errorf("GET %s: %s %s", fetchURL, resp.Status, strings.TrimSpace(string(body)))
	}

	return io.ReadAll(io.LimitReader(resp.Body, 8<<20))
}
