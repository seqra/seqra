'use strict';

const { createTransform, TYPES } = require('../release-notes-transform.cjs');

module.exports = {
  tagFormat: 'github/v${version}',
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
          { scope: 'github', breaking: true, release: 'major' },
          { scope: 'github', type: 'feat', release: 'minor' },
          { scope: 'github', type: 'fix', release: 'patch' },
          { scope: 'github', type: 'refactor', release: 'patch' },
          { scope: 'github', type: 'revert', release: 'patch' },
          { scope: '!(github)', release: false },
        ],
      },
    ],
    [
      '@semantic-release/release-notes-generator',
      {
        preset: 'conventionalcommits',
        presetConfig: { types: TYPES },
        writerOpts: {
          transform: createTransform(['github']),
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
        makeLatest: false,
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
