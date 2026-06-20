package software

import (
	"strings"
	"testing"

	"gopkg.in/yaml.v3"
)

func TestInstallScriptWarmupDefaults(t *testing.T) {
	sw := mustParseSoftware(t, `
software:
  id: old_yaml
  name: Old YAML
  images:
    default: example/image:latest
  invocation: "sleep 1"
  stop-command: "stop"
  online-signal: "online"
  install-script:
    entrypoint: sh
    script: "echo install"
`)

	if sw.InstallScript.Warmup {
		t.Fatal("expected warmup to default false")
	}
	if sw.InstallScript.WarmupTimeout != 300 {
		t.Fatalf("expected default warmup timeout 300, got %d", sw.InstallScript.WarmupTimeout)
	}
	if sw.InstallScript.WarmupRetries != 0 {
		t.Fatalf("expected default warmup retries 0, got %d", sw.InstallScript.WarmupRetries)
	}
	if sw.InstallScript.PostWarmupTimeout != 120 {
		t.Fatalf("expected default post-warmup timeout 120, got %d", sw.InstallScript.PostWarmupTimeout)
	}
	if sw.InstallScript.WarmupFailurePolicy != "fail" {
		t.Fatalf("expected default policy fail, got %q", sw.InstallScript.WarmupFailurePolicy)
	}
}

func TestInstallScriptWarmupFields(t *testing.T) {
	sw := mustParseSoftware(t, `
software:
  id: new_yaml
  name: New YAML
  images:
    default: example/image:latest
  invocation: "sleep 1"
  stop-command: "stop"
  online-signal: "online"
  install-script:
    entrypoint: sh
    script: "echo install"
    warmup: true
    warmup-timeout: 45
    warmup-retries: 2
    post-warmup-script: "echo post"
    post-warmup-timeout: 15
    warmup_failure_policy: retry
`)

	if !sw.InstallScript.Warmup {
		t.Fatal("expected warmup true")
	}
	if sw.InstallScript.WarmupTimeout != 45 {
		t.Fatalf("expected warmup timeout 45, got %d", sw.InstallScript.WarmupTimeout)
	}
	if sw.InstallScript.WarmupRetries != 2 {
		t.Fatalf("expected warmup retries 2, got %d", sw.InstallScript.WarmupRetries)
	}
	if sw.InstallScript.PostWarmupScript != "echo post" {
		t.Fatalf("unexpected post-warmup script %q", sw.InstallScript.PostWarmupScript)
	}
	if sw.InstallScript.PostWarmupTimeout != 15 {
		t.Fatalf("expected post-warmup timeout 15, got %d", sw.InstallScript.PostWarmupTimeout)
	}
	if sw.InstallScript.WarmupFailurePolicy != "retry" {
		t.Fatalf("expected retry policy, got %q", sw.InstallScript.WarmupFailurePolicy)
	}
}

func TestInstallScriptInvalidWarmupPolicy(t *testing.T) {
	var cfg config
	err := yaml.Unmarshal([]byte(`
software:
  id: invalid_policy
  name: Invalid Policy
  images:
    default: example/image:latest
  invocation: "sleep 1"
  stop-command: "stop"
  online-signal: "online"
  install-script:
    entrypoint: sh
    script: "echo install"
    warmup_failure_policy: explode
`), &cfg)
	if err == nil {
		t.Fatal("expected invalid warmup policy to fail")
	}
	if !strings.Contains(err.Error(), "warmup_failure_policy") {
		t.Fatalf("expected warmup policy error, got %v", err)
	}
}

func mustParseSoftware(t *testing.T, data string) *Software {
	t.Helper()

	var cfg config
	if err := yaml.Unmarshal([]byte(data), &cfg); err != nil {
		t.Fatal(err)
	}
	return &cfg.Software
}
