package blueprint

import (
	"crypto/sha1"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strings"
	"sync"

	"emperror.dev/errors"
	"github.com/apex/log"
	"protoxon.com/sls/protocube/config"
)

const sourcesCacheDir = ".sources"

// syncMu serializes SyncSources so concurrent reloads cannot race on caches/dests.
var syncMu sync.Mutex

// SyncConfiguredSources syncs blueprint.sources from config into system.blueprints.
// When isReload is true, sources with update_on_reload: false are skipped.
func SyncConfiguredSources(isReload bool) error {
	cfg := config.Get()
	if cfg == nil {
		return nil
	}
	return SyncSources(cfg.System.Blueprints, cfg.Blueprint.Sources, isReload)
}

// SyncSources clones/updates configured sources into root, then mirrors each
// source path into its dest under root. Existing LoadAll still reads from root.
func SyncSources(root string, sources []config.BlueprintSource, isReload bool) error {
	syncMu.Lock()
	defer syncMu.Unlock()

	if len(sources) == 0 {
		return nil
	}
	if strings.TrimSpace(root) == "" {
		return errors.New("blueprint sync: blueprints root is empty")
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return errors.Wrap(err, "blueprint sync: create blueprints root")
	}

	for i, src := range sources {
		if isReload && !sourceUpdatesOnReload(src) {
			continue
		}
		if err := syncSource(root, src); err != nil {
			return errors.Wrapf(err, "blueprint sync: source[%d]", i)
		}
	}
	return nil
}

func sourceUpdatesOnReload(src config.BlueprintSource) bool {
	if src.UpdateOnReload == nil {
		return true
	}
	return *src.UpdateOnReload
}

func syncSource(root string, src config.BlueprintSource) error {
	switch strings.ToLower(strings.TrimSpace(src.Type)) {
	case "git":
		return syncGitSource(root, src)
	case "":
		return errors.New("missing type")
	default:
		return errors.Errorf("unsupported type %q", src.Type)
	}
}

func syncGitSource(root string, src config.BlueprintSource) error {
	rawURL := strings.TrimSpace(src.URL)
	if rawURL == "" {
		return errors.New("git source missing url")
	}

	ref := strings.TrimSpace(src.Ref)
	if ref == "" {
		ref = "main"
	}
	repoPath := strings.TrimSpace(src.Path)
	if repoPath == "" {
		repoPath = "."
	}
	dest := strings.TrimSpace(src.Dest)
	if dest == "" {
		dest = "."
	}

	destDir, err := resolveUnderRoot(root, dest)
	if err != nil {
		return errors.Wrap(err, "dest")
	}

	token := ""
	if src.Auth != nil {
		envName := strings.TrimSpace(src.Auth.TokenEnv)
		if envName != "" {
			token = os.Getenv(envName)
			if token == "" {
				log.WithField("token_env", envName).
					Warn("blueprint sync: auth token env is empty; continuing without token")
			}
		}
	}

	cacheKey := sourceCacheKey(rawURL, ref, repoPath, dest)
	cacheDir := filepath.Join(root, sourcesCacheDir, cacheKey)
	if err := gitCloneOrUpdate(cacheDir, rawURL, ref, token); err != nil {
		return err
	}

	srcDir, err := resolveUnderRoot(cacheDir, repoPath)
	if err != nil {
		return errors.Wrap(err, "path")
	}
	info, err := os.Stat(srcDir)
	if err != nil {
		return errors.Wrapf(err, "path %q not found in repository", repoPath)
	}
	if !info.IsDir() {
		return errors.Errorf("path %q is not a directory", repoPath)
	}

	manifestPath := filepath.Join(root, sourcesCacheDir, cacheKey+".files")
	if err := mirrorTree(srcDir, destDir, manifestPath); err != nil {
		return errors.Wrap(err, "mirror into dest")
	}

	log.WithField("url", rawURL).
		WithField("ref", ref).
		WithField("path", repoPath).
		WithField("dest", destDir).
		Info("synced blueprint git source")
	return nil
}

func sourceCacheKey(rawURL, ref, path, dest string) string {
	sum := sha1.Sum([]byte(rawURL + "\n" + ref + "\n" + path + "\n" + dest))
	return hex.EncodeToString(sum[:8])
}

func gitCloneOrUpdate(dir, rawURL, ref, token string) error {
	gitDir := filepath.Join(dir, ".git")
	if _, err := os.Stat(gitDir); err != nil {
		if err := os.RemoveAll(dir); err != nil {
			return errors.Wrap(err, "clear incomplete clone")
		}
		if err := os.MkdirAll(filepath.Dir(dir), 0o700); err != nil {
			return err
		}
		// Prefer a shallow clone of the ref when it is a branch or tag.
		// Always clone the clean URL; auth is supplied via env for this process only.
		if err := runGit("", token, "clone", "--depth", "1", "--branch", ref, rawURL, dir); err != nil {
			if err := runGit("", token, "clone", rawURL, dir); err != nil {
				return errors.Wrap(err, "git clone")
			}
			if err := runGit(dir, token, "checkout", "-f", ref); err != nil {
				return errors.Wrapf(err, "git checkout %s", ref)
			}
		}
		// Ensure origin never stores credentials even if an older git wrote them.
		if err := runGit(dir, "", "remote", "set-url", "origin", rawURL); err != nil {
			return errors.Wrap(err, "git remote set-url")
		}
		return nil
	}

	if err := runGit(dir, "", "remote", "set-url", "origin", rawURL); err != nil {
		return errors.Wrap(err, "git remote set-url")
	}
	if err := runGit(dir, token, "fetch", "--depth", "1", "origin", ref); err != nil {
		if err := runGit(dir, token, "fetch", "origin", ref); err != nil {
			return errors.Wrapf(err, "git fetch %s", ref)
		}
	}
	if err := runGit(dir, "", "checkout", "-f", "FETCH_HEAD"); err != nil {
		return errors.Wrap(err, "git checkout FETCH_HEAD")
	}
	return nil
}

// runGit runs git with prompt disabled. When token is non-empty, HTTPS auth is
// injected via GIT_CONFIG_* env (Authorization: Basic x-access-token) so the
// token is never written into .git/config.
func runGit(dir, token string, args ...string) error {
	cmd := exec.Command("git", args...)
	if dir != "" {
		cmd.Dir = dir
	}
	env := append(os.Environ(),
		"GIT_TERMINAL_PROMPT=0",
		"GIT_ASKPASS=echo",
	)
	if token != "" {
		auth := "Authorization: Basic " + base64.StdEncoding.EncodeToString(
			[]byte("x-access-token:"+token),
		)
		env = append(env,
			"GIT_CONFIG_COUNT=1",
			"GIT_CONFIG_KEY_0=http.extraHeader",
			"GIT_CONFIG_VALUE_0="+auth,
		)
	}
	cmd.Env = env
	out, err := cmd.CombinedOutput()
	if err != nil {
		msg := strings.TrimSpace(string(out))
		if msg == "" {
			msg = err.Error()
		}
		return fmt.Errorf("%s", msg)
	}
	return nil
}

// resolveUnderRoot joins root/rel and ensures the result stays under root.
func resolveUnderRoot(root, rel string) (string, error) {
	cleanRoot, err := filepath.Abs(root)
	if err != nil {
		return "", err
	}
	target := filepath.Join(cleanRoot, filepath.Clean(rel))
	target, err = filepath.Abs(target)
	if err != nil {
		return "", err
	}
	sep := string(os.PathSeparator)
	if target != cleanRoot && !strings.HasPrefix(target, cleanRoot+sep) {
		return "", errors.Errorf("path %q escapes %q", rel, root)
	}
	return target, nil
}

// mirrorTree copies src into dst (create/overwrite), then removes files that
// were written by a previous sync of this source but are no longer upstream.
// Local files never recorded in the manifest are left alone.
func mirrorTree(src, dst, manifestPath string) error {
	prev, err := readManifest(manifestPath)
	if err != nil {
		return err
	}
	current := make(map[string]struct{})

	if err := filepath.Walk(src, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		rel, err := filepath.Rel(src, path)
		if err != nil {
			return err
		}
		if info.IsDir() {
			if info.Name() == ".git" {
				return filepath.SkipDir
			}
			if rel == "." {
				return os.MkdirAll(dst, 0o700)
			}
			return os.MkdirAll(filepath.Join(dst, rel), 0o700)
		}
		if strings.HasPrefix(rel, ".git"+string(os.PathSeparator)) {
			return nil
		}
		rel = filepath.ToSlash(rel)
		current[rel] = struct{}{}
		return copyFile(path, filepath.Join(dst, filepath.FromSlash(rel)), info.Mode())
	}); err != nil {
		return err
	}

	for rel := range prev {
		if _, ok := current[rel]; ok {
			continue
		}
		target := filepath.Join(dst, filepath.FromSlash(rel))
		if err := os.Remove(target); err != nil && !os.IsNotExist(err) {
			return errors.Wrapf(err, "remove stale %s", rel)
		}
		removeEmptyParents(dst, filepath.Dir(filepath.FromSlash(rel)))
	}

	return writeManifest(manifestPath, current)
}

func removeEmptyParents(root, rel string) {
	for rel != "." && rel != "" && rel != string(os.PathSeparator) {
		dir := filepath.Join(root, rel)
		if err := os.Remove(dir); err != nil {
			return
		}
		rel = filepath.Dir(rel)
	}
}

func readManifest(path string) (map[string]struct{}, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return map[string]struct{}{}, nil
		}
		return nil, err
	}
	out := make(map[string]struct{})
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		out[line] = struct{}{}
	}
	return out, nil
}

func writeManifest(path string, files map[string]struct{}) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	lines := make([]string, 0, len(files))
	for rel := range files {
		lines = append(lines, rel)
	}
	sort.Strings(lines)
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, []byte(strings.Join(lines, "\n")+"\n"), 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func copyFile(src, dst string, mode os.FileMode) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	if err := os.MkdirAll(filepath.Dir(dst), 0o700); err != nil {
		return err
	}

	tmp := dst + ".tmp"
	out, err := os.OpenFile(tmp, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, mode.Perm())
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(out, in)
	closeErr := out.Close()
	if copyErr != nil {
		_ = os.Remove(tmp)
		return copyErr
	}
	if closeErr != nil {
		_ = os.Remove(tmp)
		return closeErr
	}
	if err := os.Rename(tmp, dst); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return nil
}
