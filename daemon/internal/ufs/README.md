
## Info

Package ufs provides an abstraction layer for performing I/O on filesystems.
This package is designed to be used in-place of standard `os` package I/O
calls, and is not designed to be used as a generic filesystem abstraction
like the `io/fs` package.

The primary use-case of this package was to provide a "chroot-like" `os`
wrapper, so we can safely sandbox I/O operations within a directory and
use untrusted arbitrary paths.

## Licensing

Most code in this package is licensed under `MIT` with some exceptions.

The following files are licensed under `BSD-3-Clause` due to them being copied
verbatim or derived from [Go](https://go.dev)'s source code.

- [`file_posix.go`](./file_posix.go)
- [`mkdir_unix.go`](./mkdir_unix.go)
- [`path_unix.go`](./path_unix.go)
- [`removeall_unix.go`](./removeall_unix.go)
- [`stat_unix.go`](./stat_unix.go)
- [`walk.go`](./walk.go)

These changes are not associated with nor endorsed by The Go Authors.
