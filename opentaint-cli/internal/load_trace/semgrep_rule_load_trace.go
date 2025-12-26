package load_trace

import (
	"fmt"
)

// Shared enums and types

type Reason string

const (
	ReasonError          Reason = "ERROR"
	ReasonWarning        Reason = "WARNING"
	ReasonNotImplemented Reason = "NOT_IMPLEMENTED"
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
	Path       string                   `json:"path"`
	RuleTraces []*SemgrepRuleLoadTrace `json:"ruleTraces"`
	Entries    []TraceEntry             `json:"entries"`
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
	Type    string `json:"type"`
	Message string `json:"message"`
	Step    Step   `json:"step,omitempty"`
	Reason  Reason `json:"reason,omitempty"`
}

// NewInfoEntry creates a new info trace entry
func NewInfoEntry(message string) TraceEntry {
	return TraceEntry{
		Type:    "Info",
		Message: message,
	}
}

// NewErrorEntry creates a new error trace entry
func NewErrorEntry(step Step, reason Reason, message string) TraceEntry {
	return TraceEntry{
		Type:    "Error",
		Message: message,
		Step:    step,
		Reason:  reason,
	}
}

// IsError returns true if this is an error entry
func (te TraceEntry) IsError() bool {
	return te.Type == "Error"
}

// IsInfo returns true if this is an info entry
func (te TraceEntry) IsInfo() bool {
	return te.Type == "Info"
}

// ValidateTraceEntry validates that a trace entry has the correct type
func ValidateTraceEntry(entry TraceEntry) error {
	switch entry.Type {
	case "Error", "Info":
		return nil
	default:
		return fmt.Errorf("unknown trace entry type: %q", entry.Type)
	}
}
