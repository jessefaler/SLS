package software

import "testing"

func TestResolveSoftwareUpdateURL(t *testing.T) {
	t.Parallel()
	cases := []struct {
		in   string
		want string
	}{
		{
			"https://github.com/jessefaler/SLS/blob/main/software/paper.yml",
			"https://raw.githubusercontent.com/jessefaler/SLS/main/software/paper.yml",
		},
		{
			"https://raw.githubusercontent.com/jessefaler/SLS/main/software/paper.yml",
			"https://raw.githubusercontent.com/jessefaler/SLS/main/software/paper.yml",
		},
		{
			"https://www.github.com/foo/bar/blob/v1.0/path/to/file.yml",
			"https://raw.githubusercontent.com/foo/bar/v1.0/path/to/file.yml",
		},
	}
	for _, tc := range cases {
		got, err := resolveSoftwareUpdateURL(tc.in)
		if err != nil {
			t.Fatalf("resolveSoftwareUpdateURL(%q): %v", tc.in, err)
		}
		if got != tc.want {
			t.Errorf("resolveSoftwareUpdateURL(%q) = %q, want %q", tc.in, got, tc.want)
		}
	}
}

func TestResolveSoftwareUpdateURL_invalidScheme(t *testing.T) {
	t.Parallel()
	_, err := resolveSoftwareUpdateURL("ftp://example.com/a.yml")
	if err == nil {
		t.Fatal("expected error for ftp scheme")
	}
}
