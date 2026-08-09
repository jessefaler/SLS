package blueprint

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	githttp "github.com/go-git/go-git/v5/plumbing/transport/http"
	"protoxon.com/sls/protocube/config"
)

func TestSyncGitSource(t *testing.T) {
	repo := t.TempDir()
	writeGitRepo(t, repo, map[string]string{
		"lobby.yaml":    "blueprint:\n  id: lobby\n  name: Lobby\n",
		"nested/x.yaml": "mixin:\n  id: base\n  name: Base\n",
	})

	root := t.TempDir()
	sources := []config.BlueprintSource{{
		Type: "git",
		URL:  repo,
		Ref:  "master",
		Path: ".",
		Dest: "from-git",
	}}

	SyncSources(root, sources, false)

	got := filepath.Join(root, "from-git", "lobby.yaml")
	if _, err := os.Stat(got); err != nil {
		t.Fatalf("expected synced file: %v", err)
	}

	writeFile(t, filepath.Join(repo, "lobby.yaml"), "blueprint:\n  id: lobby\n  name: Lobby Updated\n")
	run(t, repo, "git", "add", "-A")
	run(t, repo, "git", "commit", "-m", "update")

	SyncSources(root, sources, true)
	data, err := os.ReadFile(got)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(data), "Lobby Updated") {
		t.Fatalf("expected updated content, got %q", data)
	}
}

func TestSyncGitSourceSubpath(t *testing.T) {
	repo := t.TempDir()
	writeGitRepo(t, repo, map[string]string{
		"pack/a.yaml":  "blueprint:\n  id: a\n  name: A\n",
		"other/b.yaml": "blueprint:\n  id: b\n  name: B\n",
	})

	root := t.TempDir()
	SyncSources(root, []config.BlueprintSource{{
		Type: "git",
		URL:  repo,
		Ref:  "master",
		Path: "pack",
		Dest: "imported",
	}}, false)

	if _, err := os.Stat(filepath.Join(root, "imported", "a.yaml")); err != nil {
		t.Fatalf("expected pack file: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, "imported", "b.yaml")); !os.IsNotExist(err) {
		t.Fatal("did not expect other/b.yaml to be synced")
	}
}

func TestSyncRemovesStaleManifestFiles(t *testing.T) {
	repo := t.TempDir()
	writeGitRepo(t, repo, map[string]string{
		"keep.yaml": "blueprint:\n  id: keep\n  name: Keep\n",
		"gone.yaml": "blueprint:\n  id: gone\n  name: Gone\n",
	})

	root := t.TempDir()
	sources := []config.BlueprintSource{{
		Type: "git",
		URL:  repo,
		Ref:  "master",
		Dest: "d",
	}}
	SyncSources(root, sources, false)

	run(t, repo, "git", "rm", "gone.yaml")
	run(t, repo, "git", "commit", "-m", "remove")

	SyncSources(root, sources, true)
	if _, err := os.Stat(filepath.Join(root, "d", "keep.yaml")); err != nil {
		t.Fatalf("expected keep.yaml: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, "d", "gone.yaml")); !os.IsNotExist(err) {
		t.Fatal("expected gone.yaml to be removed")
	}
}

func TestSyncSkipsUpdateOnReloadFalse(t *testing.T) {
	repo := t.TempDir()
	writeGitRepo(t, repo, map[string]string{
		"x.yaml": "blueprint:\n  id: x\n  name: X\n",
	})

	root := t.TempDir()
	falseVal := false
	sources := []config.BlueprintSource{{
		Type:           "git",
		URL:            repo,
		Ref:            "master",
		Dest:           "d",
		UpdateOnReload: &falseVal,
	}}

	SyncSources(root, sources, false)

	writeFile(t, filepath.Join(repo, "x.yaml"), "blueprint:\n  id: x\n  name: Y\n")
	run(t, repo, "git", "add", "-A")
	run(t, repo, "git", "commit", "-m", "update")

	SyncSources(root, sources, true)
	data, err := os.ReadFile(filepath.Join(root, "d", "x.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(data), "name: Y") {
		t.Fatal("source with update_on_reload:false should not refresh on reload")
	}
}

func TestSyncContinuesAfterSourceFailure(t *testing.T) {
	good := t.TempDir()
	writeGitRepo(t, good, map[string]string{
		"ok.yaml": "blueprint:\n  id: ok\n  name: OK\n",
	})

	root := t.TempDir()
	SyncSources(root, []config.BlueprintSource{
		{Type: "git", URL: filepath.Join(t.TempDir(), "missing.git"), Ref: "master", Dest: "bad"},
		{Type: "git", URL: good, Ref: "master", Dest: "good"},
	}, false)

	if _, err := os.Stat(filepath.Join(root, "good", "ok.yaml")); err != nil {
		t.Fatalf("expected good source to sync after earlier failure: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, "bad")); !os.IsNotExist(err) {
		t.Fatal("did not expect bad dest to exist")
	}
}

func TestSyncSkipsDuplicateDest(t *testing.T) {
	first := t.TempDir()
	writeGitRepo(t, first, map[string]string{
		"a.yaml": "blueprint:\n  id: a\n  name: A\n",
	})
	second := t.TempDir()
	writeGitRepo(t, second, map[string]string{
		"b.yaml": "blueprint:\n  id: b\n  name: B\n",
	})

	root := t.TempDir()
	SyncSources(root, []config.BlueprintSource{
		{Type: "git", URL: first, Ref: "master", Dest: "shared"},
		{Type: "git", URL: second, Ref: "master", Dest: "shared"},
	}, false)

	if _, err := os.Stat(filepath.Join(root, "shared", "a.yaml")); err != nil {
		t.Fatalf("expected first source file: %v", err)
	}
	if _, err := os.Stat(filepath.Join(root, "shared", "b.yaml")); !os.IsNotExist(err) {
		t.Fatal("duplicate dest source should be skipped")
	}
}

func TestMirrorTreeIgnoresEscapingManifestEntries(t *testing.T) {
	dst := t.TempDir()
	src := t.TempDir()
	outside := t.TempDir()
	victim := filepath.Join(outside, "victim.txt")
	writeFile(t, victim, "secret")
	writeFile(t, filepath.Join(src, "ok.yaml"), "x")

	rel, err := filepath.Rel(dst, victim)
	if err != nil {
		t.Fatal(err)
	}
	manifest := filepath.Join(t.TempDir(), "m.files")
	if err := os.WriteFile(manifest, []byte(filepath.ToSlash(rel)+"\n"), 0o600); err != nil {
		t.Fatal(err)
	}

	if err := mirrorTree(src, dst, manifest); err != nil {
		t.Fatalf("mirrorTree: %v", err)
	}
	if _, err := os.Stat(victim); err != nil {
		t.Fatalf("victim should still exist: %v", err)
	}
	if _, err := os.Stat(filepath.Join(dst, "ok.yaml")); err != nil {
		t.Fatalf("expected mirrored file: %v", err)
	}
}

func TestGitAuthIgnoresTokenForSSH(t *testing.T) {
	auth, err := gitAuth("git@github.com:org/repo.git", "secret-token")
	if err != nil {
		// SSH agent may be unavailable; the important part is we did not fall
		// back to HTTP basic auth for an SSH URL.
		return
	}
	if _, ok := auth.(*githttp.BasicAuth); ok {
		t.Fatal("SSH URL must not use HTTP token auth")
	}
}

func TestGitAuthUsesTokenForHTTPS(t *testing.T) {
	auth, err := gitAuth("https://github.com/org/repo.git", "secret-token")
	if err != nil {
		t.Fatal(err)
	}
	ba, ok := auth.(*githttp.BasicAuth)
	if !ok {
		t.Fatalf("expected BasicAuth, got %T", auth)
	}
	if ba.Password != "secret-token" {
		t.Fatalf("unexpected password %q", ba.Password)
	}
}

func TestResolveUnderRootRejectsEscape(t *testing.T) {
	root := t.TempDir()
	if _, err := resolveUnderRoot(root, "../outside"); err == nil {
		t.Fatal("expected escape to fail")
	}
}

func writeGitRepo(t *testing.T, dir string, files map[string]string) {
	t.Helper()
	run(t, dir, "git", "init", "-b", "master")
	run(t, dir, "git", "config", "user.email", "test@example.com")
	run(t, dir, "git", "config", "user.name", "test")
	for name, body := range files {
		writeFile(t, filepath.Join(dir, name), body)
	}
	run(t, dir, "git", "add", "-A")
	run(t, dir, "git", "commit", "-m", "initial")
}

func writeFile(t *testing.T, path, body string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
}

func run(t *testing.T, dir string, name string, args ...string) {
	t.Helper()
	cmd := exec.Command(name, args...)
	cmd.Dir = dir
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("%s %v: %v\n%s", name, args, err, out)
	}
}
