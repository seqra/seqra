package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/seqra/seqra/v2/internal/globals"
	"github.com/seqra/seqra/v2/internal/utils"
	"github.com/seqra/seqra/v2/internal/utils/formatters"
	"github.com/seqra/seqra/v2/internal/utils/log"
	"github.com/seqra/seqra/v2/internal/version"
	"github.com/sirupsen/logrus"
	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var toolVersion bool

// updateHintCh receives an update hint message if a new version is available.
var updateHintCh = make(chan string, 1)

// rootCmd represents the base command when called without any subcommands
var rootCmd = &cobra.Command{
	Use:           "seqra",
	Short:         "Seqra Analyzer",
	Long:          `Seqra is a CLI tool that analyzes Java and Kotlin projects to find vulnerabilities`,
	SilenceErrors: true,
	SilenceUsage:  true,

	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		// Set up logging to both console and file
		logFile, logPath, err := log.OpenLogFile()
		globals.LogPath = logPath
		cobra.CheckErr(err)

		if err := log.SetUpLogs(logFile, globals.Config.Log.Verbosity, globals.Config.Log.Color); err != nil {
			return fmt.Errorf("failed to set up logging: %w", err)
		}

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
			logrus.Info("")
			logrus.Info(hint)
		default:
		}
	},
	Run: func(cmd *cobra.Command, args []string) {
		if toolVersion {
			fmt.Printf("seqra version %s\n", version.GetVersion())
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
		fmt.Fprintln(os.Stderr, "Run 'seqra --help' for usage.")
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

	rootCmd.PersistentFlags().StringVar(&globals.Config.Log.Verbosity, "verbosity", logrus.InfoLevel.String(), "Log level (debug, info, warn, error, fatal, panic)")
	_ = viper.BindPFlag("log.verbosity", rootCmd.PersistentFlags().Lookup("verbosity"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Log.Color, "color", "auto", "Color mode (auto, always, never)")
	_ = viper.BindPFlag("log.color", rootCmd.PersistentFlags().Lookup("color"))

	rootCmd.PersistentFlags().BoolVarP(&globals.Config.Quiet, "quiet", "q", false, "Suppress interactive console output")
	_ = viper.BindPFlag("quiet", rootCmd.PersistentFlags().Lookup("quiet"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Analyzer.Version, "analyzer-version", globals.AnalyzerBindVersion, "Version of seqra analyzer")
	_ = rootCmd.PersistentFlags().MarkHidden("analyzer-version")
	_ = viper.BindPFlag("analyzer.version", rootCmd.PersistentFlags().Lookup("analyzer-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Autobuilder.Version, "autobuilder-version", globals.AutobuilderBindVersion, "Version of seqra autobuilder")
	_ = rootCmd.PersistentFlags().MarkHidden("autobuilder-version")
	_ = viper.BindPFlag("autobuilder.version", rootCmd.PersistentFlags().Lookup("autobuilder-version"))

	rootCmd.PersistentFlags().StringVar(&globals.Config.Rules.Version, "rules-version", globals.RulesBindVersion, "Version of seqra rules")
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

	rootCmd.PersistentFlags().BoolVar(&globals.Config.SkipVerify, "skip-verify", false, "Skip SHA256 checksum verification of downloaded artifacts")
	_ = viper.BindPFlag("skip-verify", rootCmd.PersistentFlags().Lookup("skip-verify"))
}

// initConfig reads in config file and ENV variables if set.
func initConfig() {
	if globals.ConfigFile != "" {
		// Use config file from the flag.
		viper.SetConfigFile(globals.ConfigFile)
	}

	viper.SetEnvPrefix("seqra")
	viper.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	viper.AutomaticEnv() // read in environment variables that match

	_ = viper.ReadInConfig()
	_ = viper.Unmarshal(&globals.Config)
}

func bindCompileTypeFlag(cmd *cobra.Command) {
	_ = viper.BindPFlag("compile.type", cmd.Flags().Lookup("compile-type"))
}

func bindScanTypeFlag(cmd *cobra.Command) {
	_ = viper.BindPFlag("scan.type", cmd.Flags().Lookup("scan-type"))
}

func printConfig(cmd *cobra.Command, printer *formatters.TreePrinter) {
	if cmd.Annotations != nil && cmd.Annotations["PrintConfig"] == "true" {
		printer.AddNode("Log level: " + globals.Config.Log.Verbosity)
		if viper.ConfigFileUsed() != "" {
			printer.AddNode("Using config file: " + viper.ConfigFileUsed())
		}
		printer.AddNode("Log file: " + globals.LogPath)
	}
}

// checkForUpdateAsync checks for a newer version in the background, throttled to once per day.
func checkForUpdateAsync() {
	currentVersion := version.GetVersion()
	if currentVersion == "dev" || strings.HasPrefix(currentVersion, "dev-") {
		return
	}

	seqraHome, err := utils.GetSeqraHome()
	if err != nil {
		return
	}

	cacheFile := filepath.Join(seqraHome, ".last-update-check")

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
	latestVersion, _, err := utils.GetLatestRelease(globals.RepoOwner, "seqra", globals.Config.Github.Token)
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
