package compress

import "github.com/klauspost/compress/zstd"

type ZstdCodec struct{}

func (ZstdCodec) Name() Compression { return CompressionZstd }

func (ZstdCodec) MediaType() string { return MediaTypeZstd }

func (ZstdCodec) NewEncoder() (Encoder, error) {
	enc, err := zstd.NewWriter(nil,
		zstd.WithEncoderConcurrency(1),
		zstd.WithEncoderLevel(zstd.SpeedDefault),
	)
	if err != nil {
		return nil, err
	}
	return &zstdEncoder{enc: enc}, nil
}

func (ZstdCodec) NewDecoder() (Decoder, error) {
	dec, err := zstd.NewReader(nil, zstd.WithDecoderConcurrency(1))
	if err != nil {
		return nil, err
	}
	return &zstdDecoder{dec: dec}, nil
}

type zstdEncoder struct {
	enc *zstd.Encoder
}

func (e *zstdEncoder) Encode(src []byte) ([]byte, error) {
	return e.enc.EncodeAll(src, make([]byte, 0, len(src)/2)), nil
}

func (e *zstdEncoder) Close() error {
	return e.enc.Close()
}

type zstdDecoder struct {
	dec *zstd.Decoder
}

func (d *zstdDecoder) Decode(src []byte) ([]byte, error) {
	return d.dec.DecodeAll(src, nil)
}

func (d *zstdDecoder) Close() error {
	d.dec.Close()
	return nil
}
