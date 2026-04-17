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

// ErrLocked is returned when a lock file is already held by another process
// in an incompatible mode.
var ErrLocked = errors.New("lock is held by another process")

// LockMeta holds diagnostic information written into lock files by exclusive
// (writer) holders. It is zero for shared (reader) holders.
type LockMeta struct {
	PID     int
	Command string
	Project string
}

// FileLock wraps a flock.Flock with its path and the mode it was acquired in.
type FileLock struct {
	flock     *flock.Flock
	path      string
	exclusive bool
}

// TryLockExclusive attempts a non-blocking LOCK_EX on the given path. On
// success it stamps meta into the file and returns a FileLock whose Unlock
// will clear the file content before releasing the kernel lock. On failure
// because another exclusive or shared holder exists, returns ErrLocked.
func TryLockExclusive(lockPath string, meta LockMeta) (*FileLock, error) {
	if err := os.MkdirAll(filepath.Dir(lockPath), 0o755); err != nil {
		return nil, fmt.Errorf("failed to create lock directory: %w", err)
	}

	fl := flock.New(lockPath)
	locked, err := fl.TryLock()
	if err != nil {
		return nil, fmt.Errorf("failed to acquire exclusive lock: %w", err)
	}
	if !locked {
		return nil, ErrLocked
	}

	content := fmt.Sprintf("pid=%d\ncommand=%s\n", meta.PID, meta.Command)
	if meta.Project != "" {
		content += fmt.Sprintf("project=%s\n", meta.Project)
	}
	_ = os.WriteFile(lockPath, []byte(content), 0o644)

	return &FileLock{flock: fl, path: lockPath, exclusive: true}, nil
}

// TryLockShared attempts a non-blocking LOCK_SH on the given path. Multiple
// shared holders can coexist. Returns ErrLocked if an exclusive holder is
// present. The file content is not modified.
func TryLockShared(lockPath string) (*FileLock, error) {
	if err := os.MkdirAll(filepath.Dir(lockPath), 0o755); err != nil {
		return nil, fmt.Errorf("failed to create lock directory: %w", err)
	}

	fl := flock.New(lockPath)
	locked, err := fl.TryRLock()
	if err != nil {
		return nil, fmt.Errorf("failed to acquire shared lock: %w", err)
	}
	if !locked {
		return nil, ErrLocked
	}

	return &FileLock{flock: fl, path: lockPath, exclusive: false}, nil
}

// Downgrade atomically converts a held LOCK_EX to LOCK_SH on the same fd and
// truncates the metadata file. After Downgrade the handle may still be
// released by Unlock. Calling Downgrade on a shared handle returns an error.
//
// Metadata is truncated *before* the kernel mode transition so that any
// concurrent prune that fails to acquire exclusive can never see stale
// writer PID under a shared lock. If the truncate fails, the kernel mode
// is not touched and the handle remains exclusively held — the caller can
// continue as if Downgrade were never called.
func (l *FileLock) Downgrade() error {
	if !l.exclusive {
		return errors.New("Downgrade called on non-exclusive lock")
	}
	if err := os.Truncate(l.path, 0); err != nil {
		return fmt.Errorf("failed to clear lock metadata on downgrade: %w", err)
	}
	// gofrs/flock.RLock on a held exclusive fd issues unix.Flock(fd, LOCK_SH)
	// on the same descriptor, which is an atomic downgrade on POSIX. After
	// this call, gofrs internally has both its f.l and f.r fields set to
	// true; that is harmless because its Unlock path issues LOCK_UN whenever
	// either flag is set.
	if err := l.flock.RLock(); err != nil {
		return fmt.Errorf("failed to downgrade lock: %w", err)
	}
	l.exclusive = false
	return nil
}

// Unlock releases the advisory lock. For exclusive holders it first truncates
// the metadata so prune's diagnostic output does not show a stale writer PID.
// The lock file itself is not removed — removing it while other shared
// holders still have it open would let a subsequent acquirer create a new
// inode and silently bypass mutual exclusion.
func (l *FileLock) Unlock() {
	if l.exclusive {
		_ = os.Truncate(l.path, 0)
		l.exclusive = false
	}
	_ = l.flock.Unlock()
}

// ReadLockMeta reads diagnostic metadata from a lock file. Returns an empty
// LockMeta when the file exists but has no content (shared holder, or after
// an exclusive holder's Unlock/Downgrade).
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

// PruneLockPath returns ~/.opentaint/.prune.lock.
func PruneLockPath() (string, error) {
	home, err := GetOpenTaintHomePath()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".prune.lock"), nil
}

// CacheLockPath returns <projectCachePath>/.cache.lock — the reader/writer
// lock gating access to the compiled project model.
func CacheLockPath(projectCachePath string) string {
	return filepath.Join(projectCachePath, ".cache.lock")
}
