#!/bin/sh

gpg --quiet --batch --yes --decrypt --passphrase="$CONFIG_PASSWORD" --output config.yaml config.yaml.gpg
