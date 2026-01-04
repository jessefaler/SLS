package server

import (
	"emperror.dev/errors"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/parser"
	"protoxon.com/sls/protocube/software"
)

func GetConfigFiles(sw *software.Software, blueprint *blueprint.Blueprint) ([]parser.ConfigurationFile, error) {
	var configFiles []parser.ConfigurationFile

	// Add software configs first
	if sw.Configs != nil {
		for fileName, configFile := range sw.Configs {
			cf, err := convertConfigFile(fileName, configFile.Parser, configFile.Find, nil)
			if err != nil {
				return nil, errors.Wrapf(err, "failed to convert software config file %s", fileName)
			}
			configFiles = append(configFiles, *cf)
		}
	}

	// Add blueprint configs
	if blueprint.Server.Configs != nil {
		for fileName, configFile := range blueprint.Server.Configs {
			cf, err := convertConfigFile(fileName, configFile.Parser, configFile.Find, nil)
			if err != nil {
				return nil, errors.Wrapf(err, "failed to convert blueprint config file %s", fileName)
			}
			// When both software and blueprint modify the same key in a file,
			// blueprint's modification should overwrite software's. This can be
			// handled in mergeReplacements if needed when applying.
			configFiles = append(configFiles, *cf)
		}
	}

	return configFiles, nil
}

// ConvertConfigFile converts a generic config file (from software or blueprint) to a parser.ConfigurationFile
func convertConfigFile(fileName, parserType string, find map[string]interface{}, serverData *parser.ServerPlaceholderData) (*parser.ConfigurationFile, error) {
	configFile := &parser.ConfigurationFile{
		FileName: fileName,
		Parser:   parser.ConfigurationParser(parserType),
		Replace:  make([]parser.ConfigurationFileReplacement, 0, len(find)),
	}

	// Convert each find entry to a replacement
	for match, value := range find {
		// Replace placeholders in the value if serverData is available
		processedValue := parser.ReplacePlaceholders(value, serverData)

		replaceValue, err := parser.NewReplaceValue(processedValue)
		if err != nil {
			return nil, err
		}

		configFile.Replace = append(configFile.Replace, parser.ConfigurationFileReplacement{
			Match:       match,
			ReplaceWith: *replaceValue,
		})
	}

	return configFile, nil
}
