package compress

import (
	"bytes"
	"io"

	"github.com/klauspost/compress/gzip"
)

type GzipCodec struct{}

func (GzipCodec) Name() Compression { return CompressionGzip }

func (GzipCodec) MediaType() string { return MediaTypeGzip }

func (GzipCodec) NewEncoder() (Encoder, error) {
	return &gzipEncoder{w: gzip.NewWriter(io.Discard)}, nil
}

func (GzipCodec) NewDecoder() (Decoder, error) {
	return &gzipDecoder{}, nil
}

type gzipEncoder struct {
	buf bytes.Buffer
	w   *gzip.Writer
}

func (e *gzipEncoder) Encode(src []byte) ([]byte, error) {
	e.buf.Reset()
	e.w.Reset(&e.buf)
	if _, err := e.w.Write(src); err != nil {
		return nil, err
	}
	if err := e.w.Close(); err != nil {
		return nil, err
	}
	return bytes.Clone(e.buf.Bytes()), nil
}

func (e *gzipEncoder) Close() error { return nil }

type gzipDecoder struct {
	r *gzip.Reader
}

func (d *gzipDecoder) Decode(src []byte) ([]byte, error) {
	br := bytes.NewReader(src)
	if d.r == nil {
		r, err := gzip.NewReader(br)
		if err != nil {
			return nil, err
		}
		d.r = r
	} else if err := d.r.Reset(br); err != nil {
		return nil, err
	}
	return io.ReadAll(d.r)
}

func (d *gzipDecoder) Close() error {
	if d.r == nil {
		return nil
	}
	return d.r.Close()
}
