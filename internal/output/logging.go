package output

import "github.com/sirupsen/logrus"

// LogInfo writes an informational diagnostic message to the log stream.
func LogInfo(args ...any) {
	logrus.Info(args...)
}

// LogInfof writes a formatted informational diagnostic message to the log stream.
func LogInfof(format string, args ...any) {
	logrus.Infof(format, args...)
}

// LogDebug writes a debug diagnostic message to the log stream.
func LogDebug(args ...any) {
	logrus.Debug(args...)
}

// LogDebugf writes a formatted debug diagnostic message to the log stream.
func LogDebugf(format string, args ...any) {
	logrus.Debugf(format, args...)
}

// Fatal writes a fatal message and terminates the process with exit code 1.
func Fatal(args ...any) {
	logrus.Fatal(args...)
}

// Fatalf writes a formatted fatal message and terminates the process with exit code 1.
func Fatalf(format string, args ...any) {
	logrus.Fatalf(format, args...)
}
