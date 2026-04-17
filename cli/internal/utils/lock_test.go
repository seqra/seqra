package utils

import (
	"os"
	"path/filepath"
	"testing"
)

func TestTryLockExclusive(t *testing.T) {
	t.Run("acquires on new file", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "x.lock")
		lock, err := TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "test"})
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		defer lock.Unlock()
	})

	t.Run("second exclusive returns ErrLocked", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "x.lock")
		l1, err := TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "first"})
		if err != nil {
			t.Fatalf("first TryLockExclusive() error = %v", err)
		}
		defer l1.Unlock()

		_, err = TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "second"})
		if err != ErrLocked {
			t.Fatalf("expected ErrLocked, got %v", err)
		}
	})

	t.Run("exclusive held blocks shared", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "x.lock")
		l1, err := TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "w"})
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		defer l1.Unlock()

		if _, err := TryLockShared(path); err != ErrLocked {
			t.Fatalf("expected ErrLocked from TryLockShared, got %v", err)
		}
	})

	t.Run("unlock clears stamped meta", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "x.lock")
		l, err := TryLockExclusive(path, LockMeta{PID: 42, Command: "compile"})
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		l.Unlock()

		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("lock file should still exist after Unlock: %v", err)
		}
		if len(data) != 0 {
			t.Errorf("expected empty file after writer Unlock, got %q", string(data))
		}
	})

	t.Run("unlock does not remove the file", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "x.lock")
		l, err := TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "test"})
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		l.Unlock()

		if _, err := os.Stat(path); err != nil {
			t.Errorf("expected lock file to persist after Unlock, got %v", err)
		}
	})
}

func TestTryLockShared(t *testing.T) {
	t.Run("acquires on new file", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "s.lock")
		l, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("TryLockShared() error = %v", err)
		}
		defer l.Unlock()
	})

	t.Run("two concurrent shared holders succeed", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "s.lock")
		l1, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("first TryLockShared() error = %v", err)
		}
		defer l1.Unlock()

		l2, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("second TryLockShared() error = %v", err)
		}
		defer l2.Unlock()
	})

	t.Run("shared blocks exclusive", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "s.lock")
		l, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("TryLockShared() error = %v", err)
		}
		defer l.Unlock()

		_, err = TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "compile"})
		if err != ErrLocked {
			t.Fatalf("expected ErrLocked, got %v", err)
		}
	})

	t.Run("shared does not touch file contents", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "s.lock")
		if err := os.WriteFile(path, []byte("pre-existing"), 0o644); err != nil {
			t.Fatal(err)
		}

		l, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("TryLockShared() error = %v", err)
		}
		defer l.Unlock()

		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		if string(data) != "pre-existing" {
			t.Errorf("shared acquire should not modify file, got %q", string(data))
		}
	})
}

func TestDowngrade(t *testing.T) {
	t.Run("downgrade allows new shared acquire", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "d.lock")
		w, err := TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "compile"})
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		defer w.Unlock()

		// Before downgrade, shared is blocked.
		if _, err := TryLockShared(path); err != ErrLocked {
			t.Fatalf("expected ErrLocked before downgrade, got %v", err)
		}

		if err := w.Downgrade(); err != nil {
			t.Fatalf("Downgrade() error = %v", err)
		}

		r, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("TryLockShared() after downgrade error = %v", err)
		}
		defer r.Unlock()
	})

	t.Run("downgrade blocks new exclusive", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "d.lock")
		w, err := TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "compile"})
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		defer w.Unlock()

		if err := w.Downgrade(); err != nil {
			t.Fatalf("Downgrade() error = %v", err)
		}

		_, err = TryLockExclusive(path, LockMeta{PID: os.Getpid(), Command: "another"})
		if err != ErrLocked {
			t.Fatalf("expected ErrLocked from exclusive-after-downgrade, got %v", err)
		}
	})

	t.Run("downgrade clears stamped meta", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "d.lock")
		w, err := TryLockExclusive(path, LockMeta{PID: 77, Command: "compile"})
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		defer w.Unlock()

		if err := w.Downgrade(); err != nil {
			t.Fatalf("Downgrade() error = %v", err)
		}

		meta, err := ReadLockMeta(path)
		if err != nil {
			t.Fatalf("ReadLockMeta() error = %v", err)
		}
		if meta.PID != 0 || meta.Command != "" {
			t.Errorf("expected empty meta after Downgrade, got %+v", meta)
		}
	})

	t.Run("downgrade on shared lock errors", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "d.lock")
		r, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("TryLockShared() error = %v", err)
		}
		defer r.Unlock()

		if err := r.Downgrade(); err == nil {
			t.Fatal("expected error from Downgrade on shared lock")
		}
	})
}

func TestReadLockMeta(t *testing.T) {
	t.Run("reads stamped meta while writer holds", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "m.lock")
		meta := LockMeta{PID: 12345, Command: "compile", Project: "/tmp/p"}
		l, err := TryLockExclusive(path, meta)
		if err != nil {
			t.Fatalf("TryLockExclusive() error = %v", err)
		}
		defer l.Unlock()

		got, err := ReadLockMeta(path)
		if err != nil {
			t.Fatalf("ReadLockMeta() error = %v", err)
		}
		if got.PID != 12345 || got.Command != "compile" || got.Project != "/tmp/p" {
			t.Errorf("unexpected meta: %+v", got)
		}
	})

	t.Run("returns empty LockMeta when only readers hold", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "m.lock")
		r, err := TryLockShared(path)
		if err != nil {
			t.Fatalf("TryLockShared() error = %v", err)
		}
		defer r.Unlock()

		got, err := ReadLockMeta(path)
		if err != nil {
			t.Fatalf("ReadLockMeta() error = %v", err)
		}
		if got.PID != 0 || got.Command != "" {
			t.Errorf("expected empty meta, got %+v", got)
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

	got, err := PruneLockPath()
	if err != nil {
		t.Fatalf("PruneLockPath() error = %v", err)
	}
	expected := filepath.Join(home, ".opentaint", ".prune.lock")
	if got != expected {
		t.Errorf("got %q, want %q", got, expected)
	}
}

func TestCacheLockPath(t *testing.T) {
	got := CacheLockPath("/home/user/.opentaint/cache/my-project-abc12345")
	expected := "/home/user/.opentaint/cache/my-project-abc12345/.cache.lock"
	if got != expected {
		t.Errorf("got %q, want %q", got, expected)
	}
}
