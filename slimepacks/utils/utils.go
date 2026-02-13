package utils

import (
	"archive/zip"
	"compress/flate"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"emperror.dev/errors"
)

// IsDir checks if the given source is a directory.
// Returns an error if the path is not a directory or if an error occurs during the stat operation.
func IsDir(source string) error {
	info, _ := os.Stat(source)
	if !info.IsDir() {
		return errors.New("path to resource pack is not a directory")
	}
	return nil
}

// RenameDirectory takes a directory path and renames the directory at the end of the path.
// It returns the new directory path and an error if any issue occurs during the renaming process.
func RenameDirectory(dirPath string, newName string) (string, error) {
	// Get the directory's parent path
	parentDir := filepath.Dir(dirPath)

	// Create the new path for the directory
	newDirPath := filepath.Join(parentDir, newName)

	// Rename the directory
	err := os.Rename(dirPath, newDirPath)
	if err != nil {
		return "", errors.Wrap(err, "Failed to rename directory")
	}

	// Return the new path
	return newDirPath, nil
}

// DirectoryExists checks if the given directory path exists and is a directory.
// Returns true if the directory exists, false otherwise.
func DirectoryExists(dirPath string) bool {
	info, err := os.Stat(dirPath)
	if err != nil {
		// If there's an error, the directory might not exist
		return false
	}
	// Check if it's indeed a directory
	return info.IsDir()
}

// CompressContents compresses the contents of the source directory into a .zip file
// with a given compression level and saves it to the specified output directory.
func CompressContents(sourceDir, outputDir, zipName string, compressionLevel int) error {
	if err := IsDir(sourceDir); err != nil {
		return errors.Wrap(err, "invalid source directory")
	}

	if err := os.MkdirAll(outputDir, 0o755); err != nil {
		return errors.Wrap(err, "failed to ensure output directory")
	}

	if !strings.HasSuffix(zipName, ".zip") {
		zipName += ".zip"
	}

	zipPath := filepath.Join(outputDir, zipName)

	zipFile, err := os.Create(zipPath)
	if err != nil {
		return errors.Wrapf(err, "failed to create zip file %s", zipPath)
	}
	defer zipFile.Close()

	zipWriter := zip.NewWriter(zipFile)
	defer zipWriter.Close()

	zipWriter.RegisterCompressor(zip.Deflate, func(out io.Writer) (io.WriteCloser, error) {
		return flate.NewWriter(out, compressionLevel)
	})

	walkErr := filepath.WalkDir(sourceDir, func(path string, d fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}

		relPath, err := filepath.Rel(sourceDir, path)
		if err != nil {
			return errors.Wrap(err, "failed computing relative path")
		}

		if relPath == "." {
			return nil
		}

		zipName := filepath.ToSlash(relPath)

		info, err := d.Info()
		if err != nil {
			return errors.Wrapf(err, "failed to get info for %s", path)
		}

		header, err := zip.FileInfoHeader(info)
		if err != nil {
			return errors.Wrap(err, "failed to create zip header")
		}

		header.Name = zipName

		if d.IsDir() {
			header.Name += "/"
			_, err = zipWriter.CreateHeader(header)
			return err
		}

		header.Method = zip.Deflate

		writer, err := zipWriter.CreateHeader(header)
		if err != nil {
			return err
		}

		file, err := os.Open(path)
		if err != nil {
			return errors.Wrapf(err, "failed to open file %s", path)
		}
		defer file.Close()

		_, err = io.Copy(writer, file)
		return err
	})

	if walkErr != nil {
		return walkErr
	}

	return nil
}

// DeleteDirectory deletes the directory at the given path along with all of its contents.
func DeleteDirectory(dirPath string) error {
	// Check if the directory exists first
	info, err := os.Stat(dirPath)
	if err != nil {
		return errors.Wrapf(err, "failed to get directory info for %s", dirPath)
	}

	// Ensure the path is a directory
	if !info.IsDir() {
		return errors.New("The specified path is not a directory")
	}

	// Attempt to remove the directory and all its contents
	err = os.RemoveAll(dirPath)
	if err != nil {
		return errors.Wrapf(err, "failed to delete directory %s", dirPath)
	}

	return nil
}

// FileExists checks if a file exists at the given path.
func FileExists(filePath string) (bool, error) {
	// Check if the file exists
	_, err := os.Stat(filePath)
	if err != nil {
		// If the error is 'file not found', return false without an error
		if os.IsNotExist(err) {
			return false, nil // File does not exist
		}
		// Return any other error encountered
		return false, errors.Wrapf(err, "failed to check file existence at path %s", filePath)
	}

	// If no error, the file exists
	return true, nil
}
