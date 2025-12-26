package container_run

import (
	"archive/tar"
	"bufio"
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	cliconfig "github.com/docker/cli/cli/config"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/image"
	"github.com/docker/docker/api/types/registry"
	"github.com/docker/docker/client"
	"github.com/docker/docker/pkg/stdcopy"
	"github.com/moby/go-archive"
	"github.com/moby/sys/user"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/sirupsen/logrus"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils"
	"github.com/seqra/seqra/v2/internal/utils/log"
)

// logWriter implements io.Writer and forwards all writes to a channel
// for processing by the logrus logger
type logWriter struct {
	ch chan<- string
}

func (w *logWriter) Write(p []byte) (n int, err error) {
	// Remove trailing newline if present
	line := string(p)
	if len(line) > 0 && line[len(line)-1] == '\n' {
		line = line[:len(line)-1]
	}
	w.ch <- line
	return len(p), nil
}

// Default username for ghcr.io
// https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry#authenticating-with-a-personal-access-token-classic
const ghcrUsername = "USERNAME"

func RunGhcrContainer(taskName, imageLink string, flags []string, envCont []string, hostConfig *container.HostConfig, copyToContainer map[string]string, copyFromContainer map[string]string) {

	// Container configuration (equivalent to the docker run command options)
	config := &container.Config{
		Image:        imageLink,
		Cmd:          flags,
		Env:          envCont,
		OpenStdin:    true, // -i: interactive
		AttachStdout: true, // attach stdout
		AttachStderr: true, // attach stderr
		// Tty can be set to true if you need a pseudo-TTY (not used in this example)
	}
	logrus.Debugf("Image: %v", imageLink)
	logrus.Debugf("Flags: %v", flags)
	logrus.Debugf("Env: %v", envCont)

	for _, copyTo := range copyFromContainer {
		if _, err := os.Stat(copyTo); err == nil {
			logrus.Fatalf("File already exist: %s", copyTo)
		}
	}

	ctx := context.Background()
	cli, err := client.NewClientWithOpts(client.FromEnv, client.WithAPIVersionNegotiation())
	if err != nil {
		logrus.Fatalf("Unexpected error occurred while trying to create docker client: %s", err)
	}
	defer func() {
		err = errors.Join(err, cli.Close())
	}()

	var options = image.PullOptions{}

	if strings.HasPrefix(imageLink, globals.GithubDockerHost) {
		var password = globals.Config.Github.Token

		if password == "" {
			cfg, err := cliconfig.Load("")
			if err != nil {
				logrus.Fatalf("Unexpected error occurred while trying to load Docker config: %s", err)
			}

			a, _ := cfg.GetAuthConfig(globals.GithubDockerHost)
			password = a.Password
		}

		if password != "" {
			authConfig := registry.AuthConfig{
				Username: ghcrUsername,
				Password: password,
			}
			encodedJSON, err := json.Marshal(authConfig)
			if err != nil {
				logrus.Fatalf("Error while encoding authConfig: %s", err)
			}

			authStr := base64.URLEncoding.EncodeToString(encodedJSON)

			options = image.PullOptions{
				RegistryAuth: authStr,
			}
		}
	}

	reader, imagePullErr := cli.ImagePull(ctx, imageLink, options)
	if imagePullErr == nil {
		defer func() {
			err = errors.Join(err, reader.Close())
		}()

		logrus.Debugf("Pulling docker image: %s", imageLink)
		// cli.ImagePull is asynchronous.
		// The reader needs to be read completely for the pull operation to complete.
		if globals.Config.Quiet {
			// If stdout is not required, consider using io.Discard instead of os.Stdout.
			_, _ = io.Copy(io.Discard, reader)
		} else {
			log.DisplayInteractiveProgress(reader)
		}
	}

	imageInspect, err := cli.ImageInspect(ctx, imageLink)
	if err != nil {
		if imagePullErr != nil {
			logrus.Fatalf("Unexpected error occurred while trying to use image %s: %s", imageLink, imagePullErr)
		} else {
			logrus.Fatalf("Unexpected error occurred while trying to use image %s: %s", imageLink, err)
		}
	} else {
		logrus.Debugf("Docker image: %s", imageLink)
		logrus.Debugf("Image os: %s", imageInspect.Os)
		logrus.Debugf("Image arch: %s", imageInspect.Architecture)
		if len(imageInspect.RepoTags) == 1 {
			logrus.Debugf("Docker tag: %s", imageInspect.RepoTags[0])
		} else if len(imageInspect.RepoTags) > 1 {
			logrus.Debugf("Docker tags:\n\t%s", strings.Join(imageInspect.RepoTags, "\n\t"))
		}
		if len(imageInspect.RepoDigests) == 1 {
			logrus.Debugf("Docker digest: %s", imageInspect.RepoDigests[0])
		} else if len(imageInspect.RepoDigests) > 1 {
			logrus.Debugf("Docker digests:\n\t%s", strings.Join(imageInspect.RepoDigests, "\n\t"))
		}
	}

	resp, err := cli.ContainerCreate(ctx, config, hostConfig, nil, nil, "")
	if err != nil {
		logrus.Fatalf("Unexpected error occurred while trying to create Docker container: %s", err)
	}

	logrus.Debugf("Container created ID: %s", resp.ID)

	logrus.Debugf("Start processing: %s", taskName)

	for copyFrom, copyTo := range copyToContainer {
		logrus.Debugf("Copy \"%v\" to container \"%v\"", copyFrom, copyTo)
		err = CopyToContainer(cli, ctx, resp.ID, copyFrom, copyTo)
		if err != nil {
			logrus.Errorf("Unexpected error occurred while trying to copy files to container: from %s to %s", copyFrom, copyTo)
			logrus.Fatal(err)
		}
	}
	logrus.Debugf("Files copied to container: %v", len(copyToContainer))

	// Attach to container output streams before starting
	out, err := cli.ContainerAttach(ctx, resp.ID, container.AttachOptions{
		Stream: true,
		Stdout: true,
		Stderr: true,
	})
	if err != nil {
		logrus.Fatalf("Failed to attach to container: %v", err)
	}
	defer out.Close()

	// Start a goroutine to stream logs in real-time
	logStream := make(chan string, 100)
	go func() {
		// Use stdcopy to properly demultiplex stdout and stderr
		_, _ = stdcopy.StdCopy(
			&logWriter{logStream}, // stdout
			&logWriter{logStream}, // stderr - same channel for both streams
			out.Reader,
		)
		close(logStream)
	}()

	if err := cli.ContainerStart(ctx, resp.ID, container.StartOptions{}); err != nil {
		logrus.Fatalf("Unexpected error occurred while trying to start container: %s", err)
	}

	defer func() {
		_ = cli.ContainerKill(ctx, resp.ID, "SIGKILL")
	}()

	// Print logs in real-time at Debug level
	for line := range logStream {
		logrus.Debug(line)
	}

	statusCh, errCh := cli.ContainerWait(ctx, resp.ID, container.WaitConditionNotRunning)
	select {
	case err := <-errCh:
		if err != nil {
			logrus.Fatalf("Unexpected error occurred while running container: %s", err)
		}
	case statusBody := <-statusCh:
		inspect, err := cli.ContainerInspect(ctx, resp.ID)
		if err != nil {
			logrus.Fatalf("Unexpected error occurred while inspect container, after run: %s", err)
		}

		startTime, err := time.Parse(time.RFC3339Nano, inspect.State.StartedAt)
		if err != nil {
			logrus.Fatalf("Unexpected error occurred while inspect calculate container start time: %s", err)
		}

		endTime, err := time.Parse(time.RFC3339Nano, inspect.State.FinishedAt)
		if err != nil {
			logrus.Fatalf("Unexpected error occurred while inspect calculate container end time: %s", err)
		}

		duration := endTime.Sub(startTime)

		if globals.Config.Quiet {
			logrus.Debugf("End processing")
			logrus.Debugf("Processing time: %s", formatters.FormatDuration(duration))
		}

		// Get container logs and log them line by line at debug level
		out, err := cli.ContainerLogs(ctx, resp.ID, container.LogsOptions{
			ShowStdout: true,
			ShowStderr: true,
			Details:    false,
		})
		defer func() {
			err = out.Close()
		}()
		if err != nil {
			logrus.Warnf("Failed to get container logs: %v", err)
			return
		} else {
			var sourceBuffer bytes.Buffer
			_, err := stdcopy.StdCopy(&sourceBuffer, &sourceBuffer, out)
			if err != nil {
				logrus.Fatalf("Unexpected error occurred while trying get logs from container: %s", err)
			}
			scanner := bufio.NewScanner(&sourceBuffer)

			var allLogs string
			for scanner.Scan() {
				allLogs += scanner.Text() + "\n"
			}
			if err := scanner.Err(); err != nil {
				logrus.Debugf("Error reading container logs: %v", err)
			}
			logrus.Debugf("Container log:\n%s", allLogs)
		}

		if statusBody.StatusCode != 0 {
			logrus.Fatalf("Container exited with non-zero exit code: %d", statusBody.StatusCode)
		}
	}

	for copyFrom, copyTo := range copyFromContainer {
		logrus.Debugf("Copy \"%v\" from container to \"%v\"", copyFrom, copyTo)
		err = CopyFileFromContainer(cli, ctx, resp.ID, copyFrom, copyTo)
		if err != nil {
			logrus.Error(err)
			if taskName == "Compile" {
				logrus.Error("Try compile with flag --native")
			}
			logrus.Fatalf("There was a problem during the %s step, check the full logs: %s", taskName, globals.LogPath)
		}
	}
	logrus.Debugf("Files copied from container: %v", len(copyFromContainer))

	err = cli.ContainerStop(ctx, resp.ID, container.StopOptions{})
	if err != nil {
		logrus.Fatalf("Unexpected error occurred while stopping container: %s", err)
	}

	// TODO add some logs if container exists due to some error
	err = cli.ContainerRemove(ctx, resp.ID, container.RemoveOptions{})
	if err != nil {
		logrus.Fatalf("Unexpected error occurred while removing container: %s", err)
	}
}

func CopyFileFromContainer(cli *client.Client, ctx context.Context, containerID, containerPath, hostPath string) error {
	if _, err := os.Stat(hostPath); err == nil {
		return fmt.Errorf("file already exists: %s", hostPath)
	}

	reader, stat, err := cli.CopyFromContainer(ctx, containerID, containerPath)
	if err != nil {
		return fmt.Errorf("failed to copy from container: %w", err)
	}

	defer func() {
		err = reader.Close()
		if err != nil {
			logrus.Fatalf("Unexpected error occurred while trying to close file: %s", err)
		}
	}()

	tr := tar.NewReader(reader)

	// Extract the tar contents
	if err := utils.ExtractTar(tr, stat.Name, hostPath, stat.Mode.IsDir()); err != nil {
		return fmt.Errorf("failed to extract tar archive: %w", err)
	}

	return nil
}

func CopyToContainer(cli *client.Client, ctx context.Context, containerID string, localDir string, containerDestPath string) error {
	_, err := os.Stat(localDir)
	if err != nil {
		return fmt.Errorf("cannot stat local path: %w", err)
	}

	baseName := filepath.Base(localDir)
	parentDir := filepath.Dir(localDir)

	// If we don't follow the symlinks, the next tar step will fail on symlink parent dir
	parentDir, err = filepath.EvalSymlinks(parentDir)
	if err != nil {
		return fmt.Errorf("cannot eval symlinks: %w", err)
	}

	// Setup minimal identity map (no remapping)
	idMap := user.IdentityMapping{}

	// Rebase: this tells Docker to unpack your files into /app/data instead of /local
	rebase := map[string]string{
		baseName: containerDestPath,
	}

	tarOpts := &archive.TarOptions{
		IncludeFiles:     []string{baseName},
		RebaseNames:      rebase,
		IDMap:            idMap,
		IncludeSourceDir: true,
	}

	tarStream, err := archive.TarWithOptions(parentDir, tarOpts)
	if err != nil {
		return fmt.Errorf("failed to create tar archive: %w", err)
	}
	defer func() {
		err = tarStream.Close()
		if err != nil {
			logrus.Fatalf("Unexpected error occurred while trying to close tarStream: %s", err)
		}
	}()

	// Copy to root, since tar contains full path structure
	err = cli.CopyToContainer(ctx, containerID, "/", tarStream, container.CopyToContainerOptions{
		AllowOverwriteDirWithFile: true,
	})
	if err != nil {
		return fmt.Errorf("failed to copy to container: %w", err)
	}

	return nil
}
