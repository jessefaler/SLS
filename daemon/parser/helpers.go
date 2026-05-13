package parser

import (
	"bytes"
	"encoding/json"
	"regexp"
	"strconv"
	"strings"

	"emperror.dev/errors"
	"github.com/Jeffail/gabs/v2"
	"github.com/apex/log"
	"github.com/buger/jsonparser"
	"github.com/iancoleman/strcase"
)

// Regex to match anything that has a value matching the format of {{ config.$1 }} which
// will cause the program to lookup that configuration value from itself and set that
// value to the configuration one.
//
// This allows configurations to reference values that are node dependent, such as the
// internal IP address used by the daemon, useful in Bungeecord setups for example, where
// it is common to see variables such as "{{config.docker.interface}}"
var configMatchRegex = regexp.MustCompile(`{{\s?config\.([\w.-]+)\s?}}`)

// Regex for {{server.build.default.port}} and other keys under server.* — resolved from
// serverData JSON (see server.Server.buildServerDataJSON), not daemon config.
var serverMatchRegex = regexp.MustCompile(`{{\s?server\.([\w.-]+)\s?}}`)

// Regex to support modifying XML inline variable data using the config tools. This means
// you can pass a replacement of Root.Property='[value="testing"]' to get an XML node
// matching:
//
// <Root>
//
//	<Property value="testing"/>
//
// </Root>
//
// noinspection RegExpRedundantEscape
var xmlValueMatchRegex = regexp.MustCompile(`^\[([\w]+)='(.*)'\]$`)

// Iterate over an unstructured JSON/YAML/etc. interface and set all of the required
// key/value pairs for the configuration file.
//
// We need to support wildcard characters in key searches, this allows you to make
// modifications to multiple keys at once, especially useful for games with multiple
// configurations per-world (such as Spigot and Bungeecord) where we'll need to make
// adjustments to the bind address for the user.
//
// This does not currently support nested wildcard matches. For example, foo.*.bar
// will work, however foo.*.bar.*.baz will not, since we'll only be splitting at the
// first wildcard, and not subsequent ones.
func (f *ConfigurationFile) IterateOverJson(data []byte) (*gabs.Container, error) {
	parsed, err := gabs.ParseJSON(data)
	if err != nil {
		return nil, err
	}

	for _, v := range f.Replace {
		value, err := f.LookupConfigurationValueAny(v)
		if err != nil {
			return nil, err
		}

		// Check for a wildcard character, and if found split the key on that value to
		// begin doing a search and replace in the data.
		if strings.Contains(v.Match, ".*") {
			parts := strings.SplitN(v.Match, ".*", 2)

			// Iterate over each matched child and set the remaining path to the value
			// that is passed through in the loop.
			//
			// If the child is a null value, nothing will happen. Seems reasonable as of the
			// time this code is being written.
			for _, child := range parsed.Path(strings.Trim(parts[0], ".")).Children() {
				if err := v.SetAtPathway(child, strings.Trim(parts[1], "."), value); err != nil {
					if errors.Is(err, gabs.ErrNotFound) {
						continue
					}
					return nil, errors.WithMessage(err, "failed to set config value of array child")
				}
			}
			continue
		}

		if err := v.SetAtPathway(parsed, v.Match, value); err != nil {
			if errors.Is(err, gabs.ErrNotFound) {
				continue
			}
			return nil, errors.WithMessage(err, "unable to set config value at pathway: "+v.Match)
		}
	}

	return parsed, nil
}

// Regex used to check if there is an array element present in the given pathway by looking for something
// along the lines of "something[1]" or "something[1].nestedvalue" as the path.
var checkForArrayElement = regexp.MustCompile(`^([^\[\]]+)\[([\d]+)](\..+)?$`)

// Attempt to set the value of the path depending on if it is an array or not. Gabs cannot handle array
// values as "something[1]" but can parse them just fine. This is basically just overly complex code
// to handle that edge case and ensure the value gets set correctly.
//
// Bless thee who has to touch these most unholy waters.
func setValueAtPath(c *gabs.Container, path string, value interface{}) error {
	var err error

	matches := checkForArrayElement.FindStringSubmatch(path)

	// Check if we are **NOT** updating an array element.
	if len(matches) < 3 {
		_, err = c.SetP(value, path)
		return err
	}

	i, _ := strconv.Atoi(matches[2])
	// Find the array element "i" or try to create it if "i" is equal to 0 and is not found
	// at the given path.
	ct, err := c.ArrayElementP(i, matches[1])
	if err != nil {
		if i != 0 || (!errors.Is(err, gabs.ErrNotArray) && !errors.Is(err, gabs.ErrNotFound)) {
			return errors.WithMessage(err, "error while parsing array element at path")
		}

		t := make([]interface{}, 1)
		// If the length of matches is 4 it means we're trying to access an object down in this array
		// key, so make sure we generate the array as an array of objects, and not just a generic nil
		// array.
		if len(matches) == 4 {
			t = []interface{}{map[string]interface{}{}}
		}

		// If the error is because this isn't an array or isn't found go ahead and create the array with
		// an empty object if we have additional things to set on the array, or just an empty array type
		// if there is not an object structure detected (no matches[3] available).
		if _, err = c.SetP(t, matches[1]); err != nil {
			return errors.WithMessage(err, "failed to create empty array for missing element")
		}

		// Set our cursor to be the array element we expect, which in this case is just the first element
		// since we won't run this code unless the array element is 0. There is too much complexity in trying
		// to match additional elements. In those cases the server will just have to be rebooted or something.
		ct, err = c.ArrayElementP(0, matches[1])
		if err != nil {
			return errors.WithMessage(err, "failed to find array element at path")
		}
	}

	// Try to set the value. If the path does not exist an error will be raised to the caller which will
	// then check if the error is because the path is missing. In those cases we just ignore the error since
	// we don't want to do anything specifically when that happens.
	//
	// If there are four matches in the regex it means that we managed to also match a trailing pathway
	// for the key, which should be found in the given array key item and modified further.
	if len(matches) == 4 {
		_, err = ct.SetP(value, strings.TrimPrefix(matches[3], "."))
	} else {
		_, err = ct.Set(value)
	}

	if err != nil {
		return errors.WithMessage(err, "failed to set value at config path: "+path)
	}

	return nil
}

// Sets the value at a specific pathway, but checks if we were looking for a specific
// value or not before doing it.
func (cfr *ConfigurationFileReplacement) SetAtPathway(c *gabs.Container, path string, value interface{}) error {
	if cfr.IfValue == "" {
		return setValueAtPath(c, path, value)
	}

	// Check if we are replacing instead of overwriting.
	if strings.HasPrefix(cfr.IfValue, "regex:") {
		valueStr, ok := value.(string)
		if !ok {
			log.WithFields(log.Fields{"path": path, "if_value": cfr.IfValue}).
				Warn("configuration replacement uses regex if_value but replacement value is not a string; skipping")
			return nil
		}

		// Doing a regex replacement requires an existing value.
		// TODO: Do we try passing an empty string to the regex?
		if c.ExistsP(path) {
			return gabs.ErrNotFound
		}

		r, err := regexp.Compile(strings.TrimPrefix(cfr.IfValue, "regex:"))
		if err != nil {
			log.WithFields(log.Fields{"if_value": strings.TrimPrefix(cfr.IfValue, "regex:"), "error": err}).
				Warn("configuration if_value using invalid regexp, cannot perform replacement")
			return nil
		}

		v := strings.Trim(c.Path(path).String(), "\"")
		if r.Match([]byte(v)) {
			return setValueAtPath(c, path, r.ReplaceAllString(v, valueStr))
		}
		return nil
	}

	if c.ExistsP(path) && !bytes.Equal(c.Bytes(), []byte(cfr.IfValue)) {
		return nil
	}

	return setValueAtPath(c, path, value)
}

// Looks up a configuration value on the Daemon given a dot-notated syntax.
func (f *ConfigurationFile) LookupConfigurationValue(cfr ConfigurationFileReplacement) (string, error) {
	if cfr.ReplaceWith.Type() != jsonparser.String {
		return cfr.ReplaceWith.String(), nil
	}

	raw := cfr.ReplaceWith.Value()
	s := cfr.ReplaceWith.String()

	if configMatchRegex.Match(raw) {
		return f.lookupPlaceholderJSON(s, raw, configMatchRegex, f.configuration, "configuration value")
	}

	if f.serverData != nil && serverMatchRegex.Match(raw) {
		return f.lookupPlaceholderJSON(s, raw, serverMatchRegex, f.serverData, "server data value")
	}

	return s, nil
}

// LookupConfigurationValueAny returns the replacement as a native Go type for JSON/YAML patching.
//
// - For strings, it resolves {{config.*}} / {{server.*}} placeholders (same behavior as LookupConfigurationValue).
// - For non-strings (number/bool/null/array/object), it unmarshals the raw JSON bytes into an interface{}.
func (f *ConfigurationFile) LookupConfigurationValueAny(cfr ConfigurationFileReplacement) (interface{}, error) {
	if cfr.ReplaceWith.Type() == jsonparser.String {
		return f.LookupConfigurationValue(cfr)
	}

	var v interface{}
	if err := json.Unmarshal(cfr.ReplaceWith.Value(), &v); err != nil {
		return nil, errors.Wrap(err, "parser: failed to unmarshal replacement JSON value")
	}
	return v, nil
}

func (f *ConfigurationFile) lookupPlaceholderJSON(
	s string,
	raw []byte,
	rx *regexp.Regexp,
	doc []byte,
	kind string,
) (string, error) {
	huntPath := rx.ReplaceAllString(rx.FindString(s), "$1")

	var path []string
	for _, value := range strings.Split(huntPath, ".") {
		path = append(path, strcase.ToSnake(value))
	}

	match, typ, _, err := jsonparser.Get(doc, path...)
	if err != nil {
		if err != jsonparser.KeyPathNotFoundError {
			return string(match), err
		}

		log.WithFields(log.Fields{"path": path, "filename": f.FileName}).Debug("attempted to load a " + kind + " that does not exist")

		return s, nil
	}

	valStr, err := jsonValueToString(match, typ)
	if err != nil {
		return "", err
	}

	return rx.ReplaceAllString(s, valStr), nil
}

func jsonValueToString(match []byte, typ jsonparser.ValueType) (string, error) {
	switch typ {
	case jsonparser.String:
		return jsonparser.ParseString(match)
	case jsonparser.Number, jsonparser.Boolean, jsonparser.Null:
		return string(match), nil
	default:
		return string(match), nil
	}
}
