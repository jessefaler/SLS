package compress

type NoneCodec struct{}

func (NoneCodec) Name() Compression { return CompressionNone }

func (NoneCodec) MediaType() string { return MediaTypeNone }

func (NoneCodec) NewEncoder() (Encoder, error) { return noneCoder{}, nil }

func (NoneCodec) NewDecoder() (Decoder, error) { return noneCoder{}, nil }

type noneCoder struct{}

func (noneCoder) Encode(src []byte) ([]byte, error) { return append([]byte(nil), src...), nil }

func (noneCoder) Decode(src []byte) ([]byte, error) { return append([]byte(nil), src...), nil }

func (noneCoder) Close() error { return nil }
