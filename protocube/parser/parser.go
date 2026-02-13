package parser

import (
	"encoding/json"

	"emperror.dev/errors"
	"github.com/apex/log"
	"github.com/buger/jsonparser"
)

type ReplaceValue struct {
	value     []byte
	valueType jsonparser.ValueType
}

// NewReplaceValue creates a new ReplaceValue from an interface{} value.
// It marshals the value to JSON and determines its type by inspecting the JSON bytes.
func NewReplaceValue(value interface{}) (*ReplaceValue, error) {
	// Marshal the value to JSON to get the proper representation
	jsonBytes, err := json.Marshal(value)
	if err != nil {
		return nil, err
	}

	// Determine the value type by checking the first byte of the JSON
	// jsonparser.Get requires a path, so for raw JSON values we need to determine type manually
	var valueType jsonparser.ValueType
	if len(jsonBytes) == 0 {
		return nil, errors.New("empty JSON value")
	}

	firstByte := jsonBytes[0]
	switch firstByte {
	case '"':
		valueType = jsonparser.String
	case 't', 'f': // true or false
		valueType = jsonparser.Boolean
	case 'n': // null
		valueType = jsonparser.Null
	case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9':
		valueType = jsonparser.Number
	default:
		return nil, errors.Errorf("unexpected JSON value type starting with byte: %c", firstByte)
	}

	return &ReplaceValue{
		value:     jsonBytes,
		valueType: valueType,
	}, nil
}

// Value returns the underlying value of the replacement. Be aware that this
// can include escaped UTF-8 sequences that will need to be handled by the caller
// in order to avoid accidentally injecting invalid sequences into the running
// process.
//
// For example the expected value may be "§Foo" but you'll be working directly
// with "\u00a7FOo" for this value. This will cause user pain if not solved since
// that is clearly not the value they were expecting to be using.
func (cv *ReplaceValue) Value() []byte {
	return cv.value
}

// Type returns the underlying data type for the Value field.
func (cv *ReplaceValue) Type() jsonparser.ValueType {
	return cv.valueType
}

// String returns the value as a string representation. This will automatically
// handle casting the UTF-8 sequence into the expected value, switching something
// like "\u00a7Foo" into "§Foo".
func (cv *ReplaceValue) String() string {
	switch cv.Type() {
	case jsonparser.String:
		str, err := jsonparser.ParseString(cv.value)
		if err != nil {
			panic(errors.Wrap(err, "parser: could not parse value"))
		}
		return str
	case jsonparser.Null:
		return "<nil>"
	case jsonparser.Boolean:
		return string(cv.value)
	case jsonparser.Number:
		return string(cv.value)
	default:
		return "<invalid>"
	}
}

func (cv *ReplaceValue) Bytes() []byte {
	switch cv.Type() {
	case jsonparser.String:
		var stackbuf [64]byte
		bU, err := jsonparser.Unescape(cv.value, stackbuf[:])
		if err != nil {
			panic(errors.Wrap(err, "parser: could not parse value"))
		}
		return bU
	case jsonparser.Null:
		return []byte("<nil>")
	case jsonparser.Boolean:
		return cv.value
	case jsonparser.Number:
		return cv.value
	default:
		return []byte("<invalid>")
	}
}

// MarshalJSON returns the raw JSON bytes for the value.
// This allows the value to be properly serialized when sending to the daemon,
// matching how Pterodactyl Panel sends configuration values.
func (cv *ReplaceValue) MarshalJSON() ([]byte, error) {
	return cv.value, nil
}

type ConfigurationParser string

func (cp ConfigurationParser) String() string {
	return string(cp)
}

// ConfigurationFile defines a configuration file for the server startup. These
// will be looped over and modified before the server finishes booting.
type ConfigurationFile struct {
	FileName string                         `json:"file"`
	Parser   ConfigurationParser            `json:"parser"`
	Replace  []ConfigurationFileReplacement `json:"replace"`
}

// UnmarshalJSON is a custom unmarshaler for configuration files. If there is an
// error while parsing out the replacements, don't fail the entire operation,
// just log a global warning so someone can find the issue, and return an empty
// array of replacements.
func (f *ConfigurationFile) UnmarshalJSON(data []byte) error {
	var m map[string]*json.RawMessage
	if err := json.Unmarshal(data, &m); err != nil {
		return err
	}

	if err := json.Unmarshal(*m["file"], &f.FileName); err != nil {
		return err
	}

	if err := json.Unmarshal(*m["parser"], &f.Parser); err != nil {
		return err
	}

	if err := json.Unmarshal(*m["replace"], &f.Replace); err != nil {
		log.WithField("file", f.FileName).WithField("error", err).Warn("failed to unmarshal configuration file replacement")

		f.Replace = []ConfigurationFileReplacement{}
	}

	return nil
}

// ConfigurationFileReplacement defines a single find/replace instance for a
// given server configuration file.
type ConfigurationFileReplacement struct {
	Match       string       `json:"match"`
	IfValue     string       `json:"if_value"`
	ReplaceWith ReplaceValue `json:"replace_with"`
}

// UnmarshalJSON handles unmarshaling the JSON representation into a struct that
// provides more useful data to this functionality.
func (cfr *ConfigurationFileReplacement) UnmarshalJSON(data []byte) error {
	m, err := jsonparser.GetString(data, "match")
	if err != nil {
		return err
	}

	cfr.Match = m

	iv, err := jsonparser.GetString(data, "if_value")
	// We only check keypath here since match & replace_with should be present on all of
	// them, however if_value is optional.
	if err != nil && err != jsonparser.KeyPathNotFoundError {
		return err
	}
	cfr.IfValue = iv

	rw, dt, _, err := jsonparser.Get(data, "replace_with")
	if err != nil {
		if err != jsonparser.KeyPathNotFoundError {
			return err
		}

		// Okay, likely dealing with someone who forgot to upgrade their eggs, so in
		// that case, fallback to using the old key which was "value".
		rw, dt, _, err = jsonparser.Get(data, "value")
		if err != nil {
			return err
		}
	}

	cfr.ReplaceWith = ReplaceValue{
		value:     rw,
		valueType: dt,
	}

	return nil
}
