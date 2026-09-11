const pageActions = {
  eyebrow: 'PAGE · ACTION · LEAST PRIVILEGE', title: 'Page Actions',
  description: 'Narrow Agent-visible actions by page. Changes immediately affect planning without creating capabilities or relaxing safety policy.',
  saved: 'The page action policy is updated and effective immediately', catalog: 'Page action catalog', codeOwned: 'Code-owned ceiling',
  boundaryTitle: 'This configuration can only narrow capabilities',
  boundaryDescription: 'Re-enabling restores only a code-registered page binding. Platform, tenant, tool, role, business permission, confirmation, and Tool Gateway checks still apply.',
  pageSelect: 'Select page', search: 'Search action name, code, or description', empty: 'No controlled actions match this page',
  enabled: 'Visible', disabled: 'Disabled', enableTitle: 'Restore this page action?', disableTitle: 'Disable this page action?',
  enableHint: 'Restoring does not grant a permission or bypass any other safety gate.', disableHint: 'The action is immediately removed from Agent planning candidates on this page.',
  reason: 'Change reason', reasonRequired: 'Enter a change reason between 2 and 300 characters',
  stats: { pages: 'Covered pages', total: 'Registered actions', enabled: 'Page-visible', disabled: 'Disabled' },
  columns: { action: 'Controlled action', risk: 'Risk', policy: 'Execution policy', permissions: 'Business permissions', status: 'Page status' },
};

export default pageActions;
