package globals

import (
	_ "embed"
	"time"

	"github.com/sirupsen/logrus"
	"gopkg.in/yaml.v2"
)

//go:embed versions.yaml
var versionsYAML []byte

type versions struct {
	Analyzer    string `yaml:"analyzer"`
	Autobuilder string `yaml:"autobuilder"`
	Rules       string `yaml:"rules"`
	Java        int    `yaml:"java"`
}

var BindVersions = func() versions {
	var v versions
	if err := yaml.Unmarshal(versionsYAML, &v); err != nil {
		logrus.Fatalf("Failed to parse embedded versions.yaml: %v", err)
	}
	return v
}()

var (
	AnalyzerBindVersion    = BindVersions.Analyzer
	AutobuilderBindVersion = BindVersions.Autobuilder
	RulesBindVersion       = BindVersions.Rules
	DefaultJavaVersion     = BindVersions.Java
)

const GithubDockerHost = "ghcr.io"

const RepoOwner = "seqra"

const AnalyzerDocker = GithubDockerHost + "/" + RepoOwner + "/seqra-jvm-sast/sast-analyzer"

const AutobuilderRepoName = "seqra-jvm-autobuilder"
const AutobuilderDocker = GithubDockerHost + "/" + RepoOwner + "/" + AutobuilderRepoName + "/sast-autobuilder"
const AutobuilderAssetName = "seqra-project-auto-builder.jar"

const AnalyzerRepoName = "seqra-jvm-sast"
const AnalyzerAssetName = "seqra-project-analyzer.jar"

const RulesRepoName = "seqra-rules"
const RulesAssetName = "seqra-rules.tar.gz"

type Scan struct {
	Timeout       time.Duration `mapstructure:"timeout"`
	MaxMemory     string        `mapstructure:"max_memory"`
	CodeFlowLimit int64         `mapstructure:"code_flow_limit"`
}

type Log struct {
	Verbosity string `mapstructure:"verbosity"`
	Color     string `mapstructure:"color"`
}

type Github struct {
	Token string `mapstructure:"token"`
}

type Analyzer struct {
	Version string `mapstructure:"version"`
}

type Autobuilder struct {
	Version string `mapstructure:"version"`
}

type Rules struct {
	Version string `mapstructure:"version"`
}

type Java struct {
	Version int `mapstructure:"version"`
}

type ConfigType struct {
	Scan Scan `mapstructure:"scan"`
	Log  Log  `mapstructure:"log"`

	Github      Github      `mapstructure:"github"`
	Analyzer    Analyzer    `mapstructure:"analyzer"`
	Autobuilder Autobuilder `mapstructure:"autobuilder"`
	Rules       Rules       `mapstructure:"rules"`
	Java        Java        `mapstructure:"java"`
	Owner       string      `mapstructure:"owner"`
	Quiet       bool        `mapstructure:"quiet"`
	SkipVerify  bool        `mapstructure:"skip-verify"`
}

var Config ConfigType

var LogPath string

var ConfigFile string

// GetVersionsYAML returns the raw embedded versions.yaml content.
func GetVersionsYAML() []byte {
	return versionsYAML
}
