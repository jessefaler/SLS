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
		cf, err := convertConfigFile(fileName, m.Parser, m.Find)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to convert config file %s", fileName)
		}
		configFiles = append(configFiles, *cf)
	}

	return configFiles, nil
}

// flattenMap recursively flattens a nested map into dot-notated paths.
// For example, {"anticheat": {"anti-xray": {"enabled": false}}} becomes
// {"anticheat.anti-xray.enabled": false}
func flattenMap(prefix string, m map[string]interface{}) map[string]interface{} {
	result := make(map[string]interface{})

	for key, value := range m {
		fullKey := key
		if prefix != "" {
			fullKey = prefix + "." + key
		}

		// If the value is a nested map, recursively flatten it
		if nestedMap, ok := value.(map[string]interface{}); ok {
			for k, v := range flattenMap(fullKey, nestedMap) {
				result[k] = v
			}
		} else {
			// This is a leaf value, add it to the result
			result[fullKey] = value
		}
	}

	return result
}

// convertConfigFile converts a generic config file (from software or blueprint) to a parser.ConfigurationFile.
func convertConfigFile(fileName, parserType string, find map[string]interface{}) (*parser.ConfigurationFile, error) {
	configFile := &parser.ConfigurationFile{
		FileName: fileName,
		Parser:   parser.ConfigurationParser(parserType),
		Replace:  make([]parser.ConfigurationFileReplacement, 0, len(find)),
	}

	// Flatten the find map to handle nested structures
	flattenedFind := flattenMap("", find)

	// Convert each flattened find entry to a replacement
	for match, value := range flattenedFind {
		replaceValue, err := parser.NewReplaceValue(value)
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
