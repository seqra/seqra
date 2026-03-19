'use strict';

const { createTransform, TYPES } = require('../release-notes-transform.cjs');

module.exports = {
  tagFormat: 'rules/v${version}',
  branches: [
    'main',
    {
      name: 'release*',
      prerelease: true,
    },
  ],
  ci: false,
  plugins: [
    [
      '@semantic-release/commit-analyzer',
      {
        preset: 'conventionalcommits',
        releaseRules: [
          { scope: 'rules', breaking: true, release: 'major' },
          { scope: 'rules', type: 'feat', release: 'minor' },
          { scope: 'rules', type: 'fix', release: 'patch' },
          { scope: 'rules', type: 'refactor', release: 'patch' },
          { scope: 'rules', type: 'revert', release: 'patch' },
          { scope: '!(rules)', release: false },
        ],
      },
    ],
    [
      '@semantic-release/release-notes-generator',
      {
        preset: 'conventionalcommits',
        presetConfig: { types: TYPES },
        writerOpts: {
          transform: createTransform(['rules']),
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
        assets: [
          {
            path: '../opentaint-rules.tar.gz',
            label: 'opentaint-rules.tar.gz',
          },
        ],
      },
    ],
    [
      '@semantic-release/exec',
      {
        prepareCmd: 'echo v${nextRelease.version} > release_version.txt',
      },
    ],
  ],
};
