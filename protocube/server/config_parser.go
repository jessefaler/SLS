package server

import (
	"emperror.dev/errors"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/parser"
	"protoxon.com/sls/protocube/software"
)

// GetConfigFiles builds the ordered list of config file patches: software, then
// blueprint, then optional request overrides. Later entries for the same file
// override earlier ones when applying.
func GetConfigFiles(sw *software.Software, blueprint *blueprint.Blueprint, overrideConfigs map[string]blueprint.ConfigFile) ([]parser.ConfigurationFile, error) {
	configFiles := make([]parser.ConfigurationFile, 0)

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
			configFiles = append(configFiles, *cf)
		}
	}

	// Add request overrides last so they merge/override software and blueprint
	for fileName, configFile := range overrideConfigs {
		cf, err := convertConfigFile(fileName, configFile.Parser, configFile.Find, nil)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to convert override config file %s", fileName)
		}
		configFiles = append(configFiles, *cf)
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
