'use strict';

const { createTransform, TYPES } = require('../release-notes-transform.cjs');

module.exports = {
  branches: [
    'main',
    {
      name: 'release/**',
      prerelease: "${name.split('/').slice(0, 2).join('-').toLowerCase()}",
    },
  ],
  ci: false,
  plugins: [
    [
      '@semantic-release/commit-analyzer',
      {
        preset: 'conventionalcommits',
        releaseRules: [
          { scope: 'cli', breaking: true, release: 'major' },
          { scope: 'cli', type: 'feat', release: 'minor' },
          { scope: 'cli', type: 'fix', release: 'patch' },
          { scope: 'cli', type: 'refactor', release: 'patch' },
          { scope: 'cli', type: 'revert', release: 'patch' },
          { scope: '!(cli)', release: false },
        ],
      },
    ],
    [
      '@semantic-release/release-notes-generator',
      {
        preset: 'conventionalcommits',
        presetConfig: { types: TYPES },
        writerOpts: {
          transform: createTransform(['cli', 'rules', 'analyzer', 'autobuilder', 'core']),
        },
      },
    ],
    [
      '@semantic-release/github',
      {
        successComment: false,
        failTitle: false,
        labels: false,
        releasedLabels: false,
        assets: [],
      },
    ],
    [
      '@semantic-release/exec',
      {
        prepareCmd: 'echo ${nextRelease.version} > release_version.txt',
      },
    ],
  ],
};
