package config

import (
	"testing"
)

func TestParseImagePullPolicy(t *testing.T) {
	for _, tc := range []struct {
		in   string
		want ImagePullPolicy
	}{
		{"", ImagePullPolicySchedule},
		{"IfNotPresent", ImagePullPolicyIfNotPresent},
		{"always", ImagePullPolicyAlways},
		{"Never", ImagePullPolicyNever},
		{"Schedule", ImagePullPolicySchedule},
	} {
		got, err := ParseImagePullPolicy(tc.in)
		if err != nil {
			t.Fatalf("ParseImagePullPolicy(%q): %v", tc.in, err)
		}
		if got != tc.want {
			t.Fatalf("ParseImagePullPolicy(%q) = %q, want %q", tc.in, got, tc.want)
		}
	}
	if _, err := ParseImagePullPolicy("bogus"); err == nil {
		t.Fatal("expected error for bogus policy")
	}
}

func TestNormalizeAndValidateDocker_ScheduleDefaultsCron(t *testing.T) {
	cfg := DockerConfiguration{
		ImagePullPolicy: ImagePullPolicySchedule,
	}
	if err := ValidateImagePullPolicy(&cfg); err != nil {
		t.Fatal(err)
	}
	if cfg.ImagePullSchedule != DefaultImagePullSchedule {
		t.Fatalf("empty schedule: got %q, want %q", cfg.ImagePullSchedule, DefaultImagePullSchedule)
	}
	cfg.ImagePullSchedule = "not a cron"
	if err := ValidateImagePullPolicy(&cfg); err == nil {
		t.Fatal("expected error for invalid cron")
	}
	cfg.ImagePullSchedule = "0 3 * * *"
	if err := ValidateImagePullPolicy(&cfg); err != nil {
		t.Fatal(err)
	}
}
