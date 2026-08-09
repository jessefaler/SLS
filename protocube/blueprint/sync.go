package blueprint

import (
	"crypto/sha1"
	"encoding/hex"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"

	"emperror.dev/errors"
	"github.com/apex/log"
	git "github.com/go-git/go-git/v5"
	gitconfig "github.com/go-git/go-git/v5/config"
	"github.com/go-git/go-git/v5/plumbing"
	"github.com/go-git/go-git/v5/plumbing/transport"
	"github.com/go-git/go-git/v5/plumbing/transport/http"
	gitssh "github.com/go-git/go-git/v5/plumbing/transport/ssh"
	"protoxon.com/sls/protocube/config"
	"protoxon.com/sls/protocube/software"
)

const sourcesCacheDir = ".sources"

// syncMu serializes sync+load so concurrent reloads cannot race on caches/dests.
var syncMu sync.Mutex

// SyncAndLoadConfigured syncs blueprint.sources then loads blueprints under one
// lock. Source sync failures are logged and skipped; load errors are returned.
// When isReload is true, sources with update_on_reload: false are skipped.
func SyncAndLoadConfigured(isReload bool, sw *software.Registry) (*LoadResult, error) {
	syncMu.Lock()
	defer syncMu.Unlock()

	cfg := config.Get()
	if cfg == nil {
		return nil, errors.New("config is nil")
	}
	syncSourcesLocked(cfg.System.Blueprints, cfg.Blueprint.Sources, isReload)
	return LoadAll(cfg.System.Blueprints, sw)
}

// SyncSources clones/updates configured sources into root, then mirrors each
// source path into its dest under root. Per-source failures are logged and
// skipped so other sources still sync. Existing LoadAll still reads from root.
func SyncSources(root string, sources []config.BlueprintSource, isReload bool) {
	syncMu.Lock()
	defer syncMu.Unlock()
	syncSourcesLocked(root, sources, isReload)
}

func syncSourcesLocked(root string, sources []config.BlueprintSource, isReload bool) {
	if len(sources) == 0 {
		return
	}
	if strings.TrimSpace(root) == "" {
		log.Error("blueprint sync: blueprints root is empty")
		return
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		log.WithError(err).Error("blueprint sync: create blueprints root")
		return
	}

	skipDupDest := duplicateDestIndexes(root, sources)
	for i, src := range sources {
		if skipDupDest[i] {
			continue
		}
		if isReload && !sourceUpdatesOnReload(src) {
			continue
		}
		if err := syncSource(root, src); err != nil {
			log.WithError(err).
				WithField("index", i).
				WithField("type", src.Type).
				WithField("url", src.URL).
				Error("blueprint sync: source failed; continuing with remaining sources")
			continue
		}
	}
}

// duplicateDestIndexes returns indexes to skip because another earlier source
// already claims the same resolved dest. Shared dests are unsafe: each source
// has its own manifest and stale cleanup can delete the other's files.
func duplicateDestIndexes(root string, sources []config.BlueprintSource) map[int]bool {
	skip := make(map[int]bool)
	seen := make(map[string]int)
	for i, src := range sources {
		dest := strings.TrimSpace(src.Dest)
		if dest == "" {
			dest = "."
		}
		destDir, err := resolveUnderRoot(root, dest)
		if err != nil {
			continue
		}
		if prev, ok := seen[destDir]; ok {
			skip[i] = true
			log.WithField("index", i).
				WithField("dest", destDir).
				WithField("conflicts_with", prev).
				Error("blueprint sync: duplicate dest; skipping source")
			continue
		}
		seen[destDir] = i
	}
	return skip
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
	auth, err := gitAuth(rawURL, token)
	if err != nil {
		return err
	}

	repo, err := git.PlainOpen(dir)
	if err != nil {
		if remErr := os.RemoveAll(dir); remErr != nil {
			return errors.Wrap(remErr, "clear incomplete clone")
		}
		if mkErr := os.MkdirAll(filepath.Dir(dir), 0o700); mkErr != nil {
			return mkErr
		}
		repo, err = cloneRepo(dir, rawURL, ref, auth)
		if err != nil {
			_ = os.RemoveAll(dir)
			return err
		}
	} else {
		if err := setOriginURL(repo, rawURL); err != nil {
			return errors.Wrap(err, "set origin url")
		}
		if err := fetchRepo(repo, auth); err != nil {
			return err
		}
	}

	return checkoutRef(repo, ref)
}

func cloneRepo(dir, rawURL, ref string, auth transport.AuthMethod) (*git.Repository, error) {
	// Prefer a shallow single-branch clone of the requested ref.
	for _, name := range []plumbing.ReferenceName{
		plumbing.NewBranchReferenceName(ref),
		plumbing.NewTagReferenceName(ref),
	} {
		repo, err := git.PlainClone(dir, false, &git.CloneOptions{
			URL:           rawURL,
			Auth:          auth,
			Depth:         1,
			SingleBranch:  true,
			ReferenceName: name,
			Tags:          git.NoTags,
		})
		if err == nil {
			if err := setOriginURL(repo, rawURL); err != nil {
				return nil, errors.Wrap(err, "set origin url")
			}
			return repo, nil
		}
		_ = os.RemoveAll(dir)
	}

	// Fall back to a full clone (needed for arbitrary commit SHAs).
	repo, err := git.PlainClone(dir, false, &git.CloneOptions{
		URL:  rawURL,
		Auth: auth,
		Tags: git.AllTags,
	})
	if err != nil {
		return nil, errors.Wrap(err, "git clone")
	}
	if err := setOriginURL(repo, rawURL); err != nil {
		return nil, errors.Wrap(err, "set origin url")
	}
	return repo, nil
}

func fetchRepo(repo *git.Repository, auth transport.AuthMethod) error {
	err := repo.Fetch(&git.FetchOptions{
		RemoteName: "origin",
		Auth:       auth,
		Force:      true,
		Depth:      1,
		Tags:       git.AllTags,
	})
	if err == nil || err == git.NoErrAlreadyUpToDate {
		return nil
	}

	// Shallow fetch can fail for some refs; retry without depth.
	err = repo.Fetch(&git.FetchOptions{
		RemoteName: "origin",
		Auth:       auth,
		Force:      true,
		Tags:       git.AllTags,
	})
	if err == nil || err == git.NoErrAlreadyUpToDate {
		return nil
	}
	return errors.Wrap(err, "git fetch")
}

func setOriginURL(repo *git.Repository, rawURL string) error {
	cfg, err := repo.Config()
	if err != nil {
		return err
	}
	remote, ok := cfg.Remotes["origin"]
	if !ok || remote == nil {
		cfg.Remotes["origin"] = &gitconfig.RemoteConfig{
			Name: "origin",
			URLs: []string{rawURL},
		}
	} else {
		remote.URLs = []string{rawURL}
	}
	return repo.Storer.SetConfig(cfg)
}

func checkoutRef(repo *git.Repository, ref string) error {
	hash, err := resolveRef(repo, ref)
	if err != nil {
		return err
	}
	wt, err := repo.Worktree()
	if err != nil {
		return errors.Wrap(err, "git worktree")
	}
	if err := wt.Checkout(&git.CheckoutOptions{Hash: hash, Force: true}); err != nil {
		return errors.Wrapf(err, "git checkout %s", ref)
	}
	return nil
}

func resolveRef(repo *git.Repository, ref string) (plumbing.Hash, error) {
	// Prefer remote-tracking refs so a fetch actually moves the worktree on update.
	candidates := []plumbing.Revision{
		plumbing.Revision("refs/remotes/origin/" + ref),
		plumbing.Revision("refs/tags/" + ref),
		plumbing.Revision(ref),
		plumbing.Revision("refs/heads/" + ref),
	}
	var last error
	for _, rev := range candidates {
		hash, err := repo.ResolveRevision(rev)
		if err == nil {
			return *hash, nil
		}
		last = err
	}
	return plumbing.ZeroHash, errors.Wrapf(last, "resolve ref %q", ref)
}

func gitAuth(rawURL, token string) (transport.AuthMethod, error) {
	if isSSHGitURL(rawURL) {
		if token != "" {
			log.Warn("blueprint sync: auth token ignored for SSH URL; using SSH agent")
		}
		auth, err := gitssh.DefaultAuthBuilder("git")
		if err != nil {
			return nil, errors.Wrap(err, "ssh auth")
		}
		return auth, nil
	}
	if token != "" {
		return &http.BasicAuth{
			Username: "x-access-token",
			Password: token,
		}, nil
	}
	return nil, nil
}

func isSSHGitURL(rawURL string) bool {
	u := strings.TrimSpace(rawURL)
	switch {
	case strings.HasPrefix(u, "git@"):
		return true
	case strings.HasPrefix(u, "ssh://"):
		return true
	default:
		return false
	}
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
		target, err := resolveUnderRoot(dst, filepath.FromSlash(rel))
		if err != nil {
			return errors.Wrapf(err, "mirror path %q", rel)
		}
		current[rel] = struct{}{}
		return copyFile(path, target, info.Mode())
	}); err != nil {
		return err
	}

	for rel := range prev {
		if _, ok := current[rel]; ok {
			continue
		}
		target, err := resolveUnderRoot(dst, filepath.FromSlash(rel))
		if err != nil {
			log.WithError(err).
				WithField("rel", rel).
				Warn("blueprint sync: ignoring stale manifest path that escapes dest")
			continue
		}
		if err := os.Remove(target); err != nil && !os.IsNotExist(err) {
			return errors.Wrapf(err, "remove stale %s", rel)
		}
		removeEmptyParents(dst, filepath.Dir(filepath.FromSlash(rel)))
	}

	return writeManifest(manifestPath, current)
}

func removeEmptyParents(root, rel string) {
	for rel != "." && rel != "" && rel != string(os.PathSeparator) {
		dir, err := resolveUnderRoot(root, rel)
		if err != nil {
			return
		}
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
