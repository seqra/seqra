MAKE ?= make
GRADLEW := $(CURDIR)/core/gradlew
INSTALL ?= install

PREFIX ?= /usr/local
BINDIR ?= $(PREFIX)/bin
LIBDIR ?= $(PREFIX)/lib

CORE_DIR := core
CLI_DIR := cli

CLI_BINARY_NAME := opentaint
CLI_DEV_BINARY_NAME := opentaint-dev
ANALYZER_TASK := :projectAnalyzerJar
AUTOBUILDER_TASK := opentaint-jvm-autobuilder:projectAutoBuilderJar
GO_SERVER_TASK := :opentaint-ir:go:buildGoServer

ANALYZER_JAR := $(CORE_DIR)/build/libs/opentaint-project-analyzer.jar
AUTOBUILDER_JAR := $(CORE_DIR)/opentaint-jvm-autobuilder/build/libs/opentaint-project-auto-builder.jar
RULES_SRC := rules/ruleset
GO_SERVER_BINARY := $(CORE_DIR)/opentaint-ir/go/go-ssa-server/go-ssa-server
INSTALLED_ANALYZER_JAR := $(LIBDIR)/$(notdir $(ANALYZER_JAR))
INSTALLED_AUTOBUILDER_JAR := $(LIBDIR)/$(notdir $(AUTOBUILDER_JAR))
INSTALLED_RULES_DIR := $(LIBDIR)/rules
INSTALLED_GO_SERVER_BINARY := $(LIBDIR)/go-ssa-server
INSTALLED_CLI_BINARY := $(BINDIR)/$(CLI_BINARY_NAME)
INSTALLED_DEV_BINARY := $(BINDIR)/$(CLI_DEV_BINARY_NAME)

.PHONY: all core projectAnalyzerJar core/autobuilder cli install clean

all: core cli

core:
	cd $(CORE_DIR) && $(GRADLEW) $(ANALYZER_TASK) $(AUTOBUILDER_TASK) $(GO_SERVER_TASK)

projectAnalyzerJar:
	cd $(CORE_DIR) && $(GRADLEW) $(ANALYZER_TASK)

core/autobuilder:
	cd $(CORE_DIR) && $(GRADLEW) $(AUTOBUILDER_TASK)

cli:
	$(MAKE) -C $(CLI_DIR) build

install: core
	mkdir -p $(BINDIR) $(LIBDIR)
	$(MAKE) -C $(CLI_DIR) install PREFIX=$(PREFIX) BINDIR=$(abspath $(BINDIR))
	$(INSTALL) -m 0644 $(ANALYZER_JAR) $(INSTALLED_ANALYZER_JAR)
	$(INSTALL) -m 0644 $(AUTOBUILDER_JAR) $(INSTALLED_AUTOBUILDER_JAR)
	$(INSTALL) -m 0755 $(GO_SERVER_BINARY) $(INSTALLED_GO_SERVER_BINARY)
	rm -rf $(INSTALLED_RULES_DIR)
	mkdir -p $(INSTALLED_RULES_DIR)
	cp -R $(RULES_SRC)/. $(INSTALLED_RULES_DIR)/
	printf '%s\n' \
		'#!/bin/sh' \
		'set -eu' \
		'if command -v realpath >/dev/null 2>&1; then SELF=$$(realpath "$$0"); else SELF=$$0; fi' \
		'BIN_DIR=$$(CDPATH= cd -- "$$(dirname -- "$$SELF")" && pwd -P)' \
		'PREFIX_DIR=$$(CDPATH= cd -- "$$BIN_DIR/.." && pwd)' \
		'LIB_DIR="$$PREFIX_DIR/lib"' \
		'export GOIR_SERVER_BINARY="$$LIB_DIR/go-ssa-server"' \
		'exec "$$BIN_DIR/$(CLI_BINARY_NAME)" --experimental --analyzer-jar "$$LIB_DIR/$(notdir $(ANALYZER_JAR))" --autobuilder-jar "$$LIB_DIR/$(notdir $(AUTOBUILDER_JAR))" "$$@"' \
		> $(INSTALLED_DEV_BINARY)
	chmod 0755 $(INSTALLED_DEV_BINARY)
	$(INSTALLED_CLI_BINARY) pull

clean:
	$(MAKE) -C $(CLI_DIR) clean
	cd $(CORE_DIR) && $(GRADLEW) clean
