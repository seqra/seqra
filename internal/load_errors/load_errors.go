package load_errors

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/seqra/seqra/internal/utils/semgrep"
)

// ----- Shared enums -----

type Level string

const (
	LevelTrace Level = "TRACE"
	LevelDebug Level = "DEBUG"
	LevelInfo  Level = "INFO"
	LevelWarn  Level = "WARN"
	LevelError Level = "ERROR"
)

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

// ----- Polymorphic base -----

type AbstractSemgrepError interface {
	isAbstractSemgrepError()
}

type discriminator struct {
	Type *string `json:"type"`
}

type ErrorsList []*AbstractSemgrepErrorWrapper

type AbstractSemgrepErrorWrapper struct {
	AbstractSemgrepError
}

func (el *ErrorsList) UnmarshalJSON(b []byte) error {
	var raws []json.RawMessage
	if err := json.Unmarshal(b, &raws); err != nil {
		return err
	}
	out := make([]*AbstractSemgrepErrorWrapper, 0, len(raws))
	for _, rm := range raws {
		ae, err := unmarshalAbstractSemgrepError(rm)
		if err != nil {
			return err
		}
		out = append(out, &AbstractSemgrepErrorWrapper{ae})
	}
	*el = out
	return nil
}

func unmarshalAbstractSemgrepError(b []byte) (AbstractSemgrepError, error) {
	var d discriminator
	if err := json.Unmarshal(b, &d); err != nil {
		return nil, err
	}
	if d.Type == nil {
		return nil, fmt.Errorf("missing type discriminator")
	}
	switch *d.Type {
	case "SemgrepError":
		var v SemgrepError
		if err := json.Unmarshal(b, &v); err != nil {
			return nil, err
		}
		return &v, nil
	case "SemgrepRule":
		var v SemgrepRuleErrors
		if err := json.Unmarshal(b, &v); err != nil {
			return nil, err
		}
		return &v, nil
	case "SemgrepFile":
		var v SemgrepFileErrors
		if err := json.Unmarshal(b, &v); err != nil {
			return nil, err
		}
		return &v, nil
	default:
		return nil, fmt.Errorf("unknown type: %q", *d.Type)
	}
}

// ----- Concrete structs -----

type SemgrepError struct {
	Type    *string     `json:"type"` // "SemgrepError"
	Step    *Step       `json:"step"`
	Message *string     `json:"message"`
	Level   *Level      `json:"level"`
	Reason  *Reason     `json:"reason"`
	Errors  *ErrorsList `json:"errors"`
}

func (*SemgrepError) isAbstractSemgrepError() {}

type SemgrepRuleErrors struct {
	Type         *string     `json:"type"` // "SemgrepRule"
	RuleID       *string     `json:"ruleId"`
	RuleIDInFile *string     `json:"ruleIdInFile"`
	Errors       *ErrorsList `json:"errors"`
}

func (*SemgrepRuleErrors) isAbstractSemgrepError() {}

type SemgrepFileErrors struct {
	Type   *string     `json:"type"` // "SemgrepFile"
	Path   *string     `json:"path"`
	Errors *ErrorsList `json:"errors"`
}

func (*SemgrepFileErrors) isAbstractSemgrepError() {}

// ----- Helper functions -----

func UnmarshalRootError(b []byte) (AbstractSemgrepError, error) {
	return unmarshalAbstractSemgrepError(b)
}

func UnmarshalErrorArray(b []byte) (*ErrorsList, error) {
	var list ErrorsList
	err := json.Unmarshal(b, &list)
	return &list, err
}

func (semgrepLoadErrors ErrorsList) UpdateRuleId(absRulesPath, userRulesPath string) {
	ruleStart := semgrep.GetRuleIdPathStart(userRulesPath)
	for _, loadError := range semgrepLoadErrors {
		switch v := loadError.AbstractSemgrepError.(type) {
		case *SemgrepRuleErrors:
			*v.RuleID = semgrep.GetSemgrepRuleId(*v.RuleID, absRulesPath, ruleStart)
		case *SemgrepError:
			if v.Errors != nil {
				v.Errors.UpdateRuleId(absRulesPath, userRulesPath)
			}
		case *SemgrepFileErrors:
			if v.Errors != nil {
				v.Errors.UpdateRuleId(absRulesPath, userRulesPath)
			}
		}
	}
}

// SaveErrorsListToFile writes a list of AbstractSemgrepError nodes to a JSON file.
func SaveErrorsListToFile(errs ErrorsList, filename string) error {
	file, err := os.Create(filename)
	if err != nil {
		return fmt.Errorf("failed to create file: %w", err)
	}
	defer func() {
		_ = file.Close()
	}()

	enc := json.NewEncoder(file)
	enc.SetIndent("", "  ")
	enc.SetEscapeHTML(false)

	if err := enc.Encode(errs); err != nil {
		return fmt.Errorf("failed to encode errors list: %w", err)
	}
	return nil
}
