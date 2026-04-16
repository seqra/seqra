package utils

import (
	"os"
	"path/filepath"
	"testing"
)

func TestTryLock(t *testing.T) {
	t.Run("acquires lock on new file", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		lock, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "test"})
		if err != nil {
			t.Fatalf("TryLock() error = %v", err)
		}
		defer lock.Unlock()
	})

	t.Run("second lock on same file returns ErrLocked", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		lock1, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "first"})
		if err != nil {
			t.Fatalf("first TryLock() error = %v", err)
		}
		defer lock1.Unlock()

		_, err = TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "second"})
		if err != ErrLocked {
			t.Fatalf("expected ErrLocked, got %v", err)
		}
	})

	t.Run("lock released after Unlock allows re-acquisition", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		lock1, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "first"})
		if err != nil {
			t.Fatalf("first TryLock() error = %v", err)
		}
		lock1.Unlock()

		lock2, err := TryLock(lockPath, LockMeta{PID: os.Getpid(), Command: "second"})
		if err != nil {
			t.Fatalf("second TryLock() error = %v", err)
		}
		defer lock2.Unlock()
	})
}

func TestReadLockMeta(t *testing.T) {
	t.Run("reads PID and command from lock file", func(t *testing.T) {
		lockPath := filepath.Join(t.TempDir(), "test.lock")
		meta := LockMeta{PID: 12345, Command: "compile", Project: "/tmp/my-project"}
		lock, err := TryLock(lockPath, meta)
		if err != nil {
			t.Fatalf("TryLock() error = %v", err)
		}
		defer lock.Unlock()

		got, err := ReadLockMeta(lockPath)
		if err != nil {
			t.Fatalf("ReadLockMeta() error = %v", err)
		}
		if got.PID != 12345 {
			t.Errorf("PID = %d, want 12345", got.PID)
		}
		if got.Command != "compile" {
			t.Errorf("Command = %q, want %q", got.Command, "compile")
		}
		if got.Project != "/tmp/my-project" {
			t.Errorf("Project = %q, want %q", got.Project, "/tmp/my-project")
		}
	})

	t.Run("returns error for missing file", func(t *testing.T) {
		_, err := ReadLockMeta(filepath.Join(t.TempDir(), "missing.lock"))
		if err == nil {
			t.Fatal("expected error for missing file")
		}
	})
}

func TestPruneLockPath(t *testing.T) {
	home := t.TempDir()
	t.Setenv("HOME", home)

	lockPath, err := PruneLockPath()
	if err != nil {
		t.Fatalf("PruneLockPath() error = %v", err)
	}
	expected := filepath.Join(home, ".opentaint", ".prune.lock")
	if lockPath != expected {
		t.Errorf("got %q, want %q", lockPath, expected)
	}
}

func TestCompileLockPath(t *testing.T) {
	result := CompileLockPath("/home/user/.opentaint/cache/my-project-abc12345")
	expected := "/home/user/.opentaint/cache/my-project-abc12345/.compile.lock"
	if result != expected {
		t.Errorf("got %q, want %q", result, expected)
	}
}
