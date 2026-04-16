package utils

import (
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gofrs/flock"
)

// ErrLocked is returned when a lock file is already held by another process.
var ErrLocked = errors.New("lock is held by another process")

// LockMeta holds diagnostic information written into lock files.
type LockMeta struct {
	PID     int
	Command string
	Project string
}

// FileLock wraps a flock.Flock with its path for cleanup.
type FileLock struct {
	flock *flock.Flock
	path  string
}

// Unlock releases the advisory lock and removes the lock file.
func (l *FileLock) Unlock() {
	_ = l.flock.Unlock()
	_ = os.Remove(l.path)
}

// TryLock attempts a non-blocking exclusive lock on the given path.
// On success it writes meta into the file and returns a FileLock.
// On failure because the lock is held, it returns ErrLocked.
func TryLock(lockPath string, meta LockMeta) (*FileLock, error) {
	if err := os.MkdirAll(filepath.Dir(lockPath), 0o755); err != nil {
		return nil, fmt.Errorf("failed to create lock directory: %w", err)
	}

	fl := flock.New(lockPath)
	locked, err := fl.TryLock()
	if err != nil {
		return nil, fmt.Errorf("failed to acquire lock: %w", err)
	}
	if !locked {
		return nil, ErrLocked
	}

	content := fmt.Sprintf("pid=%d\ncommand=%s\n", meta.PID, meta.Command)
	if meta.Project != "" {
		content += fmt.Sprintf("project=%s\n", meta.Project)
	}
	_ = os.WriteFile(lockPath, []byte(content), 0o644)

	return &FileLock{flock: fl, path: lockPath}, nil
}

// ReadLockMeta reads diagnostic metadata from a lock file.
func ReadLockMeta(lockPath string) (LockMeta, error) {
	data, err := os.ReadFile(lockPath)
	if err != nil {
		return LockMeta{}, err
	}
	var meta LockMeta
	for _, line := range strings.Split(string(data), "\n") {
		key, value, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		switch key {
		case "pid":
			meta.PID, _ = strconv.Atoi(value)
		case "command":
			meta.Command = value
		case "project":
			meta.Project = value
		}
	}
	return meta, nil
}

// PruneLockPath returns the path to the global prune lock: ~/.opentaint/.prune.lock
func PruneLockPath() (string, error) {
	home, err := GetOpenTaintHomePath()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".prune.lock"), nil
}

// CompileLockPath returns the path to a per-project compile lock:
// <projectCachePath>/.compile.lock
func CompileLockPath(projectCachePath string) string {
	return filepath.Join(projectCachePath, ".compile.lock")
}
