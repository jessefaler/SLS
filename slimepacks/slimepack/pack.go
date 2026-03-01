package slimepack

import (
	"compress/flate"
	"fmt"
	"path/filepath"
	"protoxon.com/sls/slimepacks/config"
	"protoxon.com/sls/slimepacks/converter"
	"protoxon.com/sls/slimepacks/mappings"
	"protoxon.com/sls/slimepacks/utils"

	"emperror.dev/errors"
)

var packFormat = -1

type Pack struct {
	id          string
	Source      string // The path to the original pack
	PackFormats *mappings.FormatsTable
}

func NewPack(id string, source string, packFormats *mappings.FormatsTable) *Pack {
	return &Pack{
		id:          id,
		Source:      source,
		PackFormats: packFormats,
	}
}

func (p *Pack) Id() string {
	return p.id
}

// GetVersion returns the path to the resource pack for the given version.
// If the requested version is different from the current version, it will convert the pack accordingly.
// The result is either a pre-compressed pack or a newly converted and compressed version.
func (p *Pack) GetVersion(version string) (string, error) {

	// If the provided version maps to a snapshot
	// Then convert the snapshot version to a release version
	// The mutex ensures snapshots aren't loaded from mojang while we are accessing them here
	Mutex.RLock()
	v, found := Snapshots.GetMajorVersion(version)
	if found {
		version = v
	}
	Mutex.RUnlock()

	// Get the base packs format
	format, err := p.GetPackFormat()
	if err != nil {
		return "", errors.Wrap(err, "Failed to get pack format")
	}

	// Get the resource packs root directory
	root, err := p.GetRootDir()
	if err != nil {
		return "", errors.Wrapf(err, "resource pack %s dose not exist at %s", p.id, root)
	}

	// Get the resource packs source directory
	source, err := p.GetSourceDir()
	if err != nil {
		return "", errors.Wrapf(err, "resource pack %s dose not exist at %s", p.id, source)
	}

	// Get from version
	from, err := p.PackFormats.GetVersionFromFormat(format)
	if err != nil {
		return "", errors.Wrapf(err, "Failed to convert resource pack: %s", p.id)
	}

	// Get to version
	usrVersion, err := p.PackFormats.GetPackFormatFromVersion(version)
	to, err := p.PackFormats.GetVersionFromFormat(usrVersion)
	if err != nil {
		return "", errors.Wrapf(err, "Failed to convert resource pack: %s", p.id)
	}

	pRange, err := p.PackFormats.GetVersionRangeFromPackFormat(usrVersion)
	if err != nil {
		return "", errors.Wrapf(err, "Failed to convert resource pack: %s", p.id)
	}

	name := fmt.Sprintf("%s=%s.zip", p.id, pRange)
	path := filepath.Join(config.Get().ConversionsFolder, p.id, name)

	if usrVersion == format {
		// The versions are the same so return the default pack no need for conversion
		// The pack still needs compressed though so check if it has already been compressed
		// In the conversions folder, if not compress it
		// check if "conversions/:id/:id-version.zip" exits
		exists, err := utils.FileExists(path)
		if err != nil || !exists {
			// Compressed file doesn't exist
			// Compress it then return the path
			err := utils.CompressContents(source, p.GetConversionsDir(), name, flate.DefaultCompression)
			if err != nil {
				return "", errors.Wrapf(err, "Failed to compress resource pack: %s", p.id)
			}
		}
		return path, nil
	}

	// check if "conversions/:id/:id-version.zip" exits
	exists, err := utils.FileExists(path)
	if err != nil || !exists {
		// Converted pack doesn't exist
		// Convert the resource pack
		err = converter.Convert(root, source, p.GetConversionsDir(), from, to, pRange)
		if err != nil {
			return "", errors.Wrapf(err, "Failed to convert resource pack %s", p.id)
		}
	}
	// Converted pack already exists return it
	return path, nil
}

// GetSourceDir will return the resource pack's directory (where the pack.mcmeta exists)
// If it doesn't exist it will return an error and the string with the directory it checked
func (p *Pack) GetSourceDir() (string, error) {
	sourcePack := filepath.Join(config.Get().ResourcePacksRoot, p.id, "pack")
	exists, err := utils.FileExists(sourcePack)
	if err != nil || !exists {
		return sourcePack, err
	}
	return sourcePack, nil
}

// GetRootDir will return the base resource pack's source directory
// A root directory for the resourcepack is needed for the conversion tool
// If it doesn't exist it will return an error and the string with the directory it checked
func (p *Pack) GetRootDir() (string, error) {
	sourcePack := filepath.Join(config.Get().ResourcePacksRoot, p.id)
	exists, err := utils.FileExists(sourcePack)
	if err != nil || !exists {
		return sourcePack, err
	}
	return sourcePack, nil
}

func (p *Pack) GetPackFormat() (int, error) {
	// If the pack format has been cached just return the value directly
	if packFormat != -1 {
		return packFormat, nil
	}
	// the pack format isn't cached so get it from the pack.mcmeta file
	// Get the resource packs source directory
	source, err := p.GetSourceDir()
	if err != nil {
		return -1, errors.Wrapf(err, "resource pack %s dose not exist at %s", p.id, source)
	}

	meta, err := mappings.LoadPackMeta(filepath.Join(source, "pack.mcmeta"))
	if err != nil {
		return -1, errors.Wrap(err, "Failed to read pack.mcmeta")
	}

	// return the closest version present in the mappings (pack_formats.json)
	format := p.PackFormats.FindClosestPackFormat(meta.Pack.PackFormat)
	packFormat = format.Format
	return packFormat, nil
}

func (p *Pack) GetConversionsDir() string {
	return filepath.Join(config.Get().ConversionsFolder, p.id)
}
