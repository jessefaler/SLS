package server

import (
	"testing"

	"protoxon.com/sls/daemon/models"
	"protoxon.com/sls/daemon/remote"
)

func TestWarmupDefaults(t *testing.T) {
	ip := &InstallationProcess{Script: &remote.InstallationScript{}}

	ip.applyWarmupDefaults()

	if ip.Script.WarmupTimeout != 300 {
		t.Fatalf("expected default warmup timeout 300, got %d", ip.Script.WarmupTimeout)
	}
	if ip.Script.PostWarmupTimeout != 120 {
		t.Fatalf("expected default post-warmup timeout 120, got %d", ip.Script.PostWarmupTimeout)
	}
	if ip.Script.WarmupFailurePolicy != warmupPolicyFail {
		t.Fatalf("expected default warmup failure policy %q, got %q", warmupPolicyFail, ip.Script.WarmupFailurePolicy)
	}
}

func TestWarmupAttemptsOnlyRetriesWhenPolicyIsRetry(t *testing.T) {
	cases := []struct {
		name     string
		policy   string
		retries  int
		expected int
	}{
		{name: "default", expected: 1},
		{name: "fail ignores retries", policy: warmupPolicyFail, retries: 3, expected: 1},
		{name: "continue ignores retries", policy: warmupPolicyContinue, retries: 3, expected: 1},
		{name: "retry adds retries", policy: warmupPolicyRetry, retries: 3, expected: 4},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			ip := &InstallationProcess{Script: &remote.InstallationScript{
				WarmupFailurePolicy: tc.policy,
				WarmupRetries:       tc.retries,
			}}

			if got := ip.warmupAttempts(); got != tc.expected {
				t.Fatalf("expected %d attempts, got %d", tc.expected, got)
			}
		})
	}
}

func TestWarmupMatchesStartupSignal(t *testing.T) {
	s, err := New(nil)
	if err != nil {
		t.Fatal(err)
	}
	matcher, err := models.NewOutputLineMatcher("server online")
	if err != nil {
		t.Fatal(err)
	}
	s.SetProcessConfiguration(&models.ProcessConfiguration{})
	s.ProcessConfiguration().Startup.Done = []*models.OutputLineMatcher{matcher}

	ip := &InstallationProcess{Server: s, Script: &remote.InstallationScript{}}

	if !ip.matchesStartupSignal([]byte("prefix server online suffix")) {
		t.Fatal("expected warmup startup signal to match")
	}
	if ip.matchesStartupSignal([]byte("still booting")) {
		t.Fatal("did not expect non-matching line to match")
	}
}
