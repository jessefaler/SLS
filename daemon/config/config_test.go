package config

import (
	"encoding/json"
	"fmt"
	"testing"
)

func TestConfig(t *testing.T) {
	InitConfig()
	out, err := json.MarshalIndent(Get(), "", "  ")
	if err != nil {
		t.Fatal(err)
	}

	fmt.Println(string(out))
}
