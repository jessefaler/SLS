package converter

import (
	"compress/flate"
	"os/exec"
	"path/filepath"

	"protoxon.com/sls/slimepacks/config"
	"protoxon.com/sls/slimepacks/log"
	"protoxon.com/sls/slimepacks/utils"

	"emperror.dev/errors"
)

func Convert(rootDir string, sourceDir string, outputDir string, from string, to string, pRange string) error {
	err := utils.IsDir(sourceDir)
	if err != nil {
		return errors.Wrap(err, "An error occurred while converting: source directory is not a directory")
	}

	cmd := exec.Command(
		"java",
		"-jar", config.Get().ConverterJar,
		"--from", from,
		"--to", to,
		"--input", rootDir,
	)
	output, err := cmd.CombinedOutput()
	if err != nil {
		log.WithField("output", string(output)).WithError(err).Error("An error occurred while converting pack: " + filepath.Base(rootDir))
		return errors.Wrapf(err, "An error occurred while converting, output: %s", output)
	}

	convertedDir := filepath.Join(rootDir, "pack_converted")

	// Check if the converted resourcepack directory exists
	if !utils.DirectoryExists(convertedDir) {
		return errors.Wrap(err, "An error occurred while converting: Could not find converted resourcepack directory")
	}

	// Rename the converted resourcepack directory
	name := filepath.Base(rootDir) + "=" + pRange
	convertedDir, err = utils.RenameDirectory(convertedDir, name)
	if err != nil {
		return errors.Wrap(err, "An error occurred while converting")
	}

	// compress the converted resourcepack directory
	err = utils.CompressContents(convertedDir, outputDir, name, flate.DefaultCompression)
	if err != nil {
		return errors.Wrap(err, "An error occurred while converting")
	}

	// Attempt to delete the converted directory.
	// If deletion fails, log the error with the directory path, but do not return an error.
	// The function continues to return nil as the necessary operations were successful,
	// and the failure to delete the directory is non-critical.
	err = utils.DeleteDirectory(convertedDir)
	if err != nil {
		log.WithField("path", convertedDir).WithError(err).Error("Failed to delete directory")
	}
	return nil
}
