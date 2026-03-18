'use strict';

const TYPES = [
  { type: 'chore', hidden: true },
  { type: 'feat', section: ':gift: Features', hidden: false },
  { type: 'fix', section: ':lady_beetle: Bug Fixes', hidden: false },
  { type: 'refactor', section: ':hammer_and_wrench: Refactored', hidden: false },
  { type: 'revert', section: ':back: Reverted', hidden: false },
  { type: 'style', hidden: true },
  { type: 'test', hidden: true },
];

function createTransform(allowedScopes) {
  const scopeSet = new Set(allowedScopes);
  const typeMap = new Map(TYPES.map(t => [t.type, t]));

  return (commit, context) => {
    if (!scopeSet.has(commit.scope)) return null;

    let discard = true;
    const issues = [];

    commit.notes.forEach(note => {
      note.title = 'BREAKING CHANGES';
      discard = false;
    });

    if (typeMap.has(commit.type)) {
      const typeConfig = typeMap.get(commit.type);
      if (typeConfig.hidden) return null;
      commit.type = typeConfig.section;
      discard = false;
    } else if (discard) {
      return null;
    }

    if (commit.scope === '*') commit.scope = '';

    if (typeof commit.hash === 'string') {
      commit.shortHash = commit.hash.substring(0, 7);
    }

    if (typeof commit.subject === 'string') {
      let url = context.repository
        ? `${context.host}/${context.owner}/${context.repository}`
        : context.repoUrl;
      if (url) {
        url = `${url}/issues/`;
        commit.subject = commit.subject.replace(/#([0-9]+)/g, (_, issue) => {
          issues.push(issue);
          return `[#${issue}](${url}${issue})`;
        });
      }
      if (context.host) {
        commit.subject = commit.subject.replace(
          /\B@([a-z0-9](?:-(?=[a-z0-9])|[a-z0-9]){0,38})/g,
          (_, username) => {
            if (username.includes('/')) return `@${username}`;
            return `[@${username}](${context.host}/${username})`;
          }
        );
      }
    }

    commit.references = commit.references.filter(
      reference => !issues.includes(reference.issue)
    );

    return commit;
  };
}

module.exports = { createTransform, TYPES };
