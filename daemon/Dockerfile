# Stage 1 (Build)
FROM golang:1.25.8-alpine AS builder

ARG VERSION
RUN apk add --update --no-cache git make mailcap
WORKDIR /app/
COPY go.mod go.sum /app/
RUN go mod download
COPY . /app/
RUN CGO_ENABLED=0 go build \
    -ldflags="-s -w -X github.com/jessefaler/SLS/tree/main/daemon/system.Version=$VERSION" \
    -v \
    -trimpath \
    -o daemon \
    sls.go
RUN echo "ID=\"distroless\"" > /etc/os-release

# Stage 2 (Final)
FROM gcr.io/distroless/static:latest
COPY --from=builder /etc/os-release /etc/os-release
COPY --from=builder /etc/mime.types /etc/mime.types

COPY --from=builder /app/daemon /usr/bin/

ENTRYPOINT ["/usr/bin/daemon"]
CMD ["--config", "/etc/sls/daemon/config.yml"]