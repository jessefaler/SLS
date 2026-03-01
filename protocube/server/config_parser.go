package server

import (
	"emperror.dev/errors"
	"protoxon.com/sls/protocube/blueprint"
	"protoxon.com/sls/protocube/parser"
	"protoxon.com/sls/protocube/software"
)

// mergedConfig holds merged Find map and Parser for a single file (last layer wins for Parser).
type mergedConfig struct {
	Parser string
	Find   map[string]interface{}
}

// GetConfigFiles builds one config file patch per unique filename by merging
// software, then blueprint, then optional request overrides.
func GetConfigFiles(sw *software.Software, bp *blueprint.Blueprint, overrideConfigs map[string]blueprint.ConfigFile) ([]parser.ConfigurationFile, error) {
	merged := make(map[string]*mergedConfig)
	order := make([]string, 0)

	addLayer := func(fileName string, parser string, find map[string]interface{}) {
		if _, ok := merged[fileName]; !ok {
			merged[fileName] = &mergedConfig{Parser: parser, Find: make(map[string]interface{})}
			order = append(order, fileName)
		}
		for k, v := range find {
			merged[fileName].Find[k] = v
		}
		merged[fileName].Parser = parser
	}

	if sw.Configs != nil {
		for fileName, configFile := range sw.Configs {
			addLayer(fileName, configFile.Parser, configFile.Find)
		}
	}
	if bp.Server != nil && bp.Server.Configs != nil {
		for fileName, configFile := range bp.Server.Configs {
			addLayer(fileName, configFile.Parser, configFile.Find)
		}
	}
	if overrideConfigs != nil {
		for fileName, configFile := range overrideConfigs {
			addLayer(fileName, configFile.Parser, configFile.Find)
		}
	}

	configFiles := make([]parser.ConfigurationFile, 0, len(order))
	for _, fileName := range order {
		m := merged[fileName]
		cf, err := convertConfigFile(fileName, m.Parser, m.Find, nil)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to convert config file %s", fileName)
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
