package utils

import (
	"runtime"
)

func GetArch() string {
	return runtime.GOARCH
}

func IsSupportedArch() bool {
	arch := GetArch()
	return arch == "arm64" || arch == "amd64"
}
