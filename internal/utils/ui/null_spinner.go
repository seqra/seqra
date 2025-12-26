package ui

type NullSpinner struct{}

func (s *NullSpinner) Start(message string) {}

func (s *NullSpinner) Stop(finalMessage string) {}

func (s *NullSpinner) StopError(finalMessage string) {}
