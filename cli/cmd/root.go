package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/seqra/opentaint/internal/globals"
	"github.com/seqra/opentaint/internal/output"
	"github.com/seqra/opentaint/internal/utils"
	"github.com/seqra/opentaint/internal/utils/log"
	"github.com/seqra/opentaint/internal/version"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var toolVersion bool

// out is the global output printer used by all commands for user-facing output.
// It is configured in PersistentPreRunE after logging is set up.
var out = output.New()

// updateHintCh receives an update hint message if a new version is available.
var updateHintCh = make(chan string, 1)

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Use:           "opentaint",
	Short:         "OpenTaint Analyzer",
	Long:          `OpenTaint is a CLI tool that analyzes Java and Kotlin projects to find vulnerabilities`,
	SilenceErrors: true,
	SilenceUsage:  true,

	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		verbosity, err := normalizeVerbosity(globals.Config.Log.Verbosity)
		if err != nil {
			return err
		}
		globals.Config.Log.Verbosity = verbosity

		// Set up logging to both console and file
		logFile, logPath, err := log.OpenLogFile()
		globals.LogPath = logPath
		cobra.CheckErr(err)

		if err := log.SetUpLogs(logFile, globals.Config.Log.Verbosity, globals.Config.Log.Color); err != nil {
			return fmt.Errorf("failed to set up logging: %w", err)
		}

		// Configure the output printer (color mode, quiet mode)
		out.Configure(globals.Config.Log.Color, globals.Config.Quiet)
		out.SetVerbosity(globals.Config.Log.Verbosity)
		out.SetLogWriter(logFile)

		// Start async update check (non-blocking, at most once per day)
		if !globals.Config.Quiet {
			go checkForUpdateAsync()
		}

		return nil
	},
	PersistentPostRun: func(cmd *cobra.Command, args []string) {
		// Print update hint if available
		select {
		case hint := <-updateHintCh:
			out.Blank()
			out.Print(hint)
		default:
		}
	},
	Run: func(cmd *cobra.Command, args []string) {
		if toolVersion {
			fmt.Printf("opentaint version %s\n", version.GetVersion())
		} else {
			_ = cmd.Help()
		}
	},
}

// Execute adds all child commands to the root command and sets flags appropriately.
// This is called by main.main(). It only needs to happen once to the rootCmd.
func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %s\n", err)
		fmt.Fprintln(os.Stderr, "Run 'opentaint --help' for usage.")
		os.Exit(1)
	}
}

func init() {
	cobra.OnInitialize(initConfig)

	// Here you will define your flags and configuration settings.
	// Cobra supports persistent flags, which, if defined here,
	// will be global for your application.

	rootCmd.PersistentFlags().StringVar(&globals.ConfigFile, "config", "", "Path to a config file")

	rootCmd.Flags().BoolVarP(&toolVersion, "version", "v", false, "Print the version information")

	rootCmd.PersistentFlags().StringVar(&globals.Config.Log.Verbosity, "verbosity", "info", "Verbosity level (info, debug)")
	_ = viper.BindPFlag("log.verbosity", rootCmd.PersistentFlags().Lookup("verbosity"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Log.Color, "color", "auto", "Color mode (auto, always, never)")
	_ = viper.BindPFlag("log.color", rootCmd.PersistentFlags().Lookup("color"))

	rootCmd.PersistentFlags().BoolVarP(&globals.Config.Quiet, "quiet", "q", false, "Suppress interactive UI elements (spinners, progress bars)")
	_ = viper.BindPFlag("quiet", rootCmd.PersistentFlags().Lookup("quiet"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Analyzer.Version, "analyzer-version", globals.AnalyzerBindVersion, "Version of opentaint analyzer")
	_ = rootCmd.PersistentFlags().MarkHidden("analyzer-version")
	_ = viper.BindPFlag("analyzer.version", rootCmd.PersistentFlags().Lookup("analyzer-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Autobuilder.Version, "autobuilder-version", globals.AutobuilderBindVersion, "Version of opentaint autobuilder")
	_ = rootCmd.PersistentFlags().MarkHidden("autobuilder-version")
	_ = viper.BindPFlag("autobuilder.version", rootCmd.PersistentFlags().Lookup("autobuilder-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Rules.Version, "rules-version", globals.RulesBindVersion, "Version of opentaint rules")
	_ = rootCmd.PersistentFlags().MarkHidden("rules-version")
	_ = viper.BindPFlag("rules.version", rootCmd.PersistentFlags().Lookup("rules-version"))

	rootCmd.PersistentFlags().IntVar(&globals.Config.Java.Version, "java-version", globals.DefaultJavaVersion, "Java version to use for running analyzer")
	_ = viper.BindPFlag("java.version", rootCmd.PersistentFlags().Lookup("java-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Github.Token, "github-token", "", "Token for docker image pull from ghcr.io")
	_ = rootCmd.PersistentFlags().MarkHidden("github-token")
	_ = viper.BindPFlag("github.token", rootCmd.PersistentFlags().Lookup("github-token"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Owner, "owner", globals.RepoOwner, "Organization owner for GitHub repositories")
	_ = rootCmd.PersistentFlags().MarkHidden("owner")
	_ = viper.BindPFlag("owner", rootCmd.PersistentFlags().Lookup("owner"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Repo, "repo", globals.RepoName, "GitHub repository name")
	_ = rootCmd.PersistentFlags().MarkHidden("repo")
	_ = viper.BindPFlag("repo", rootCmd.PersistentFlags().Lookup("repo"))

	rootCmd.PersistentFlags().BoolVar(&globals.Config.SkipVerify, "skip-verify", false, "Skip SHA256 checksum verification of downloaded artifacts")
	_ = viper.BindPFlag("skip-verify", rootCmd.PersistentFlags().Lookup("skip-verify"))
}

// initConfig reads in config file and ENV variables if set.
func initConfig() {
	if globals.ConfigFile != "" {
		// Use config file from the flag.
		viper.SetConfigFile(globals.ConfigFile)
	}

	viper.SetEnvPrefix("opentaint")
	viper.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	viper.AutomaticEnv() // read in environment variables that match

	_ = viper.ReadInConfig()
	_ = viper.Unmarshal(&globals.Config)
}

func normalizeVerbosity(level string) (string, error) {
	value := strings.ToLower(strings.TrimSpace(level))
	switch value {
	case "", "info":
		return "info", nil
	case "debug":
		return "debug", nil
	default:
		return "info", nil
	}
}

// addConfigFields appends config fields to a SectionBuilder if PrintConfig annotation is set.
func addConfigFields(cmd *cobra.Command, sb *output.SectionBuilder) {
	if cmd.Annotations != nil && cmd.Annotations["PrintConfig"] == "true" {
		if globals.Config.Log.Verbosity == "debug" {
			sb.Field("Log level", globals.Config.Log.Verbosity)
			if viper.ConfigFileUsed() != "" {
				sb.Field("Config file", viper.ConfigFileUsed())
			}
			sb.Field("Log file", globals.LogPath)
		}
	}
}

// checkForUpdateAsync checks for a newer version in the background, throttled to once per day.
func checkForUpdateAsync() {
	currentVersion := version.GetVersion()
	if currentVersion == "dev" || strings.HasPrefix(currentVersion, "dev-") {
		return
	}

	opentaintHome, err := utils.GetOpentaintHome()
	if err != nil {
		return
	}

	cacheFile := filepath.Join(opentaintHome, ".last-update-check")

	// Check if we've already checked today
	if info, err := os.Stat(cacheFile); err == nil {
		if time.Since(info.ModTime()) < 24*time.Hour {
			// Read cached version
			data, err := os.ReadFile(cacheFile)
			if err == nil {
				latestVersion := strings.TrimSpace(string(data))
				if latestVersion != "" {
					cmp, err := version.CompareVersions(currentVersion, latestVersion)
					if err == nil && cmp < 0 {
						updateHintCh <- utils.UpdateHint(latestVersion)
					}
				}
			}
			return
		}
	}

	// Fetch latest release
	latestVersion, _, err := utils.GetLatestRelease(globals.Config.Owner, globals.Config.Repo, globals.Config.Github.Token)
	if err != nil {
		return
	}

	// Cache the result
	_ = os.WriteFile(cacheFile, []byte(latestVersion), 0o644)

	cmp, err := version.CompareVersions(currentVersion, latestVersion)
	if err == nil && cmp < 0 {
		updateHintCh <- utils.UpdateHint(latestVersion)
	}
}
