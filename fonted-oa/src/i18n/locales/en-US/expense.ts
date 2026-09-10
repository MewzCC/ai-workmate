const expense = {
  eyebrow: 'FINANCE · REIMBURSEMENT', title: 'Expense reimbursement',
  description: 'Save reimbursement drafts, submit approvals, and track assignees, deadlines, and the full audit trail.',
  add: 'New claim', refresh: 'Refresh', total: 'All claims', draft: 'Drafts', pending: 'Pending', approved: 'Approved',
  searchStatus: 'All statuses', empty: 'No reimbursement claims', saveDraft: 'Save draft', saveSuccess: 'Draft saved',
  submit: 'Submit', submitSuccess: 'Claim submitted', updateSuccess: 'Claim updated',
  withdraw: 'Withdraw', withdrawSuccess: 'Claim withdrawn', cancel: 'Cancel draft', cancelSuccess: 'Draft cancelled',
  reopen: 'Edit again', reopenSuccess: 'Claim reopened as a draft', remind: 'Remind', remindSuccess: 'Reminder sent',
  view: 'View', edit: 'Edit', actions: 'Actions',
  columns: { expense: 'Claim', category: 'Category', amount: 'Amount', expenseDate: 'Expense date', submittedAt: 'Submitted', status: 'Status', actions: 'Actions' },
  category: { TRAVEL: 'Travel', MEAL: 'Meals', TRANSPORT: 'Transport', OFFICE: 'Office', OTHER: 'Other' },
  status: { DRAFT: 'Draft', PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected', WITHDRAWN: 'Withdrawn', CANCELLED: 'Cancelled' },
  form: { createTitle: 'New reimbursement claim', editTitle: 'Edit reimbursement claim', amount: 'Amount', category: 'Category', expenseDate: 'Expense date', invoiceNumber: 'Invoice number', invoicePlaceholder: 'Enter an invoice or receipt reference', reason: 'Business reason', reasonPlaceholder: 'Describe the purpose and business context', required: 'Required' },
  detail: { title: 'Claim details', overview: 'Application', process: 'Approval progress', applicant: 'Applicant', currentApprover: 'Current approver', dueAt: 'Due at', createdAt: 'Created', submittedAt: 'Submitted', noTimeline: 'No approval events exist for this draft yet' },
  dialog: { submitTitle: 'Submit this claim?', submitContent: 'After submission it enters approval and cannot be edited directly.', withdrawTitle: 'Withdraw this claim?', cancelTitle: 'Cancel this draft?', remindTitle: 'Remind the current approver?' },
};

export default expense;
