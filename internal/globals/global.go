package globals

import (
	"time"
)

const GithubDockerHost = "ghcr.io"

const RepoOwner = "seqra"

const AnalyzerDocker = GithubDockerHost + "/" + RepoOwner + "/seqra-jvm-sast/sast-analyzer"
const AnalyzerBindVersion = "2026.02.01.b93888b"

const AutobuilderRepoName = "seqra-jvm-autobuilder"
const AutobuilderDocker = GithubDockerHost + "/" + RepoOwner + "/" + AutobuilderRepoName + "/sast-autobuilder"
const AutobuilderBindVersion = "2026.02.01.88f8c5e"
const AutobuilderAssetName = "seqra-project-auto-builder.jar"

const AnalyzerRepoName = "seqra-jvm-sast"
const AnalyzerAssetName = "seqra-project-analyzer.jar"

const RulesRepoName = "seqra-rules"
const RulesAssetName = "seqra-rules.tar.gz"
const RulesBindVersion = "v2.1.1"

type Scan struct {
	Timeout   time.Duration `mapstructure:"timeout"`
	MaxMemory string        `mapstructure:"max_memory"`
}

type Log struct {
	Verbosity string `mapstructure:"verbosity"`
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
	Scan        Scan        `mapstructure:"scan"`
	Log         Log         `mapstructure:"log"`
	Github      Github      `mapstructure:"github"`
	Analyzer    Analyzer    `mapstructure:"analyzer"`
	Autobuilder Autobuilder `mapstructure:"autobuilder"`
	Rules       Rules       `mapstructure:"rules"`
	Java        Java        `mapstructure:"java"`
	Owner       string      `mapstructure:"owner"`
	Quiet       bool        `mapstructure:"quiet"`
}

var Config ConfigType

var LogPath string

var ConfigFile string
