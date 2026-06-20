package server

import "testing"

func TestSanitizeInstallLogKey(t *testing.T) {
	tests := []struct {
		in   string
		want string
	}{
		{"paper/1.20.1", "paper-1.20.1"},
		{"/paper/1.20.1/", "paper-1.20.1"},
		{"minigames/lobby", "minigames-lobby"},
		{"", "unknown"},
		{"/", "root"},
	}

	for _, tc := range tests {
		if got := sanitizeInstallLogKey(tc.in); got != tc.want {
			t.Fatalf("sanitizeInstallLogKey(%q) = %q, want %q", tc.in, got, tc.want)
		}
	}
}
