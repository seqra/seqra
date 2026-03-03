package load_trace

// Shared enums and types

type Category string

const (
	CategoryRuleIssue       Category = "RULE_ISSUE"
	CategoryUnsupported     Category = "UNSUPPORTED_FEATURE"
	CategoryInternalWarning Category = "INTERNAL_WARNING"
)

type Severity string

const (
	SeverityBlocking    Severity = "BLOCKING"
	SeverityNonBlocking Severity = "NON_BLOCKING"
)

type Step string

const (
	StepLoadRuleset               Step = "LOAD_RULESET"
	StepBuildConvertToRawRule     Step = "BUILD_CONVERT_TO_RAW_RULE"
	StepBuildParseSemgrepRule     Step = "BUILD_PARSE_SEMGREP_RULE"
	StepBuildMetaVarResolving     Step = "BUILD_META_VAR_RESOLVING"
	StepBuildActionListConversion Step = "BUILD_ACTION_LIST_CONVERSION"
	StepBuildTransformToAutomata  Step = "BUILD_TRANSFORM_TO_AUTOMATA"
	StepAutomataToTaintRule       Step = "AUTOMATA_TO_TAINT_RULE"
)

// SemgrepLoadTrace represents the complete trace of rule loading
type SemgrepLoadTrace struct {
	FileTraces []SemgrepFileLoadTrace `json:"fileTraces"`
}

// SemgrepFileLoadTrace represents trace information for a single file
type SemgrepFileLoadTrace struct {
	Path       string                  `json:"path"`
	RuleTraces []*SemgrepRuleLoadTrace `json:"ruleTraces"`
	Entries    []TraceEntry            `json:"entries"`
}

// SemgrepRuleLoadTrace represents trace information for a single rule
type SemgrepRuleLoadTrace struct {
	RuleId       string                      `json:"ruleId"`
	RuleIdInFile string                      `json:"ruleIdInFile"`
	Steps        []*SemgrepRuleLoadStepTrace `json:"steps"`
	Entries      []TraceEntry                `json:"entries"`
}

// SemgrepRuleLoadStepTrace represents trace information for a processing step
type SemgrepRuleLoadStepTrace struct {
	Step    Step         `json:"step"`
	Entries []TraceEntry `json:"entries"`
}

// TraceEntry represents a single trace entry (info or error)
type TraceEntry struct {
	Type     string   `json:"type"`
	Message  string   `json:"message"`
	Step     Step     `json:"step,omitempty"`
	Category Category `json:"category,omitempty"`
	Severity Severity `json:"severity,omitempty"`
}
