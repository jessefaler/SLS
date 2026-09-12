package compress

import (
	"bytes"
	"testing"
)

func TestCodecRoundTrip(t *testing.T) {
	payload := bytes.Repeat([]byte("sls volume chunk payload"), 32)

	for _, name := range []Compression{CompressionZstd, CompressionGzip, CompressionNone} {
		t.Run(string(name), func(t *testing.T) {
			codec, err := GetCodec(name)
			if err != nil {
				t.Fatal(err)
			}
			if codec.Name() != name {
				t.Fatalf("Name() = %q, want %q", codec.Name(), name)
			}
			if codec.MediaType() == "" {
				t.Fatal("MediaType() is empty")
			}

			enc, err := codec.NewEncoder()
			if err != nil {
				t.Fatalf("encoder: %v", err)
			}
			defer enc.Close()

			compressed, err := enc.Encode(payload)
			if err != nil {
				t.Fatalf("encode: %v", err)
			}
			if name != CompressionNone && bytes.Equal(compressed, payload) {
				t.Fatal("compressed output matches input")
			}

			dec, err := codec.NewDecoder()
			if err != nil {
				t.Fatalf("decoder: %v", err)
			}
			defer dec.Close()

			got, err := dec.Decode(compressed)
			if err != nil {
				t.Fatalf("decode: %v", err)
			}
			if !bytes.Equal(got, payload) {
				t.Fatalf("round trip mismatch: got %d bytes, want %d", len(got), len(payload))
			}

			again, err := enc.Encode(payload)
			if err != nil {
				t.Fatalf("reuse encode: %v", err)
			}
			got, err = dec.Decode(again)
			if err != nil {
				t.Fatalf("reuse decode: %v", err)
			}
			if !bytes.Equal(got, payload) {
				t.Fatal("reused encoder/decoder round trip mismatch")
			}
		})
	}
}

func TestGetCodecUnknown(t *testing.T) {
	if _, err := GetCodec("lz4"); err == nil {
		t.Fatal("expected error")
	}
}
