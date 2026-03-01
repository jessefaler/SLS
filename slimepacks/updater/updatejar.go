package updater

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"protoxon.com/sls/slimepacks/config"
	"protoxon.com/sls/slimepacks/log"
	"strings"

	"emperror.dev/errors"
)

const metadata = "https://api.github.com/repos/agentdid127/ResourcePackConverter/releases/latest"                          // Url for getting metadata from the latest release
const download = "https://github.com/agentdid127/ResourcePackConverter/releases/latest/download/ResourcePackConverter.jar" // Direct download url for the latest release

// Update checks asynchronously for the latest release of ResourcePackConverter.jar.
// If a new version is found, it will download the jar from the specified URL and replace the current file in the given output directory.
// This function runs asynchronously
func Update(outputDir string) {
	go func() {
		if !config.Get().AutoUpdate {
			return
		}
		updated, err := updateIfNeeded(metadata, download, outputDir)
		if err != nil {
			log.WithField("error", err).Warn("Failed to update ResourcePackConverter.jar")
		}
		if updated {
			log.Info("Updated ResourcePackConverter.jar")
		}
	}()
}

func updateIfNeeded(metadataURL, downloadURL, outputDir string) (bool, error) {
	// Ensure the output directory exists
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return false, errors.Wrap(err, "failed to create output directory")
	}

	// Define the output file path
	outputFile := filepath.Join(outputDir, "ResourcePackConverter.jar")

	// Fetch the release metadata from GitHub API
	resp, err := http.Get(metadataURL)
	if err != nil {
		return false, errors.Wrap(err, "failed to fetch metadata from GitHub")
	}
	defer resp.Body.Close()

	// Parse the JSON response to extract the digest and download URL
	var releaseData struct {
		Assets []struct {
			Name        string `json:"name"`
			Digest      string `json:"digest,omitempty"`
			DownloadURL string `json:"browser_download_url"`
		} `json:"assets"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&releaseData); err != nil {
		return false, errors.Wrap(err, "failed to parse release metadata")
	}

	// Find the digest (SHA256 hash) of the jar file in the release data
	var newSHA256 string
	for _, asset := range releaseData.Assets {
		if asset.Name == "ResourcePackConverter.jar" {
			newSHA256 = asset.Digest
			break
		}
	}

	// If the digest is not provided in metadata, we will skip comparison
	if newSHA256 == "" {
		return false, errors.New("SHA256 digest not provided in metadata")
	}

	// Remove the "sha256:" prefix from the newSHA256 hash (if it exists)
	newSHA256 = strings.TrimPrefix(newSHA256, "sha256:")

	// Check if the file exists before calculating the SHA256 hash
	var currentSHA256 string
	if _, err := os.Stat(outputFile); os.IsNotExist(err) {
		currentSHA256 = ""
	} else {
		// Calculate the current SHA256 hash of the locally saved file
		currentSHA256, err = getFileSHA256(outputFile)
		if err != nil {
			return false, errors.Wrap(err, "failed to calculate local file hash")
		}
	}

	// Compare local and remote SHA256 hash
	if newSHA256 == currentSHA256 {
		return false, nil
	}

	// If hashes are different, download the new file and calculate its hash
	if err := downloadFile(downloadURL, outputFile); err != nil {
		return false, errors.Wrap(err, "failed to download file")
	}

	// Optionally, calculate the new SHA256 hash after downloading (not returning it)
	_, err = getFileSHA256(outputFile)
	if err != nil {
		return false, errors.Wrap(err, "failed to calculate hash of newly downloaded file")
	}

	// Return true to indicate the file was updated
	return true, nil
}

// getFileSHA256 calculates the SHA256 hash of a file
func getFileSHA256(filename string) (string, error) {
	file, err := os.Open(filename)
	if err != nil {
		return "", errors.Wrap(err, "failed to open file")
	}
	defer file.Close()

	hash := sha256.New()
	if _, err := io.Copy(hash, file); err != nil {
		return "", errors.Wrap(err, "failed to compute hash")
	}

	return hex.EncodeToString(hash.Sum(nil)), nil
}

// downloadFile downloads a file from a given URL and saves it to a local path
func downloadFile(url, filepath string) error {
	resp, err := http.Get(url)
	if err != nil {
		return errors.Wrap(err, "failed to download file")
	}
	defer resp.Body.Close()

	outFile, err := os.Create(filepath)
	if err != nil {
		return errors.Wrap(err, "failed to create file")
	}
	defer outFile.Close()

	_, err = io.Copy(outFile, resp.Body)
	if err != nil {
		return errors.Wrap(err, "failed to write file")
	}

	return nil
}
