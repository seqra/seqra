'use strict';

const { createTransform, TYPES } = require('../release-notes-transform.cjs');

module.exports = {
  tagFormat: 'gitlab/v${version}',
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
          { scope: 'gitlab', breaking: true, release: 'major' },
          { scope: 'gitlab', type: 'feat', release: 'minor' },
          { scope: 'gitlab', type: 'fix', release: 'patch' },
          { scope: 'gitlab', type: 'refactor', release: 'patch' },
          { scope: 'gitlab', type: 'revert', release: 'patch' },
          { scope: '!(gitlab)', release: false },
        ],
      },
    ],
    [
      '@semantic-release/release-notes-generator',
      {
        preset: 'conventionalcommits',
        presetConfig: { types: TYPES },
        writerOpts: {
          transform: createTransform(['gitlab']),
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
        draftRelease: true,
        assets: [],
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
