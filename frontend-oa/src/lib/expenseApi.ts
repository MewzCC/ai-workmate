import { approvalEngineApi, type ApprovalApplication } from '@/lib/approvalEngineApi';

export const EXPENSE_FORM_KEY = 'expense-application';

export type ExpenseCategory = 'TRAVEL' | 'MEAL' | 'TRANSPORT' | 'OFFICE' | 'OTHER';

export interface ExpenseFormData {
  amount?: number;
  category?: ExpenseCategory;
  expenseDate?: string;
  invoiceNumber?: string;
  reason?: string;
}

export function parseExpenseData(application: ApprovalApplication): ExpenseFormData {
  try {
    const value = JSON.parse(application.dataJson) as unknown;
    return value && typeof value === 'object' && !Array.isArray(value) ? value as ExpenseFormData : {};
  } catch {
    return {};
  }
}

export const expenseApi = {
  list: (status?: ApprovalApplication['status'], page = 1, size = 20) =>
    approvalEngineApi.listMyApplications({ formKey: EXPENSE_FORM_KEY, status, page, size }),
  detail: approvalEngineApi.getApplication,
  createDraft: (formData: ExpenseFormData) =>
    approvalEngineApi.createDraft({ formKey: EXPENSE_FORM_KEY, formData: { ...formData } }),
  updateDraft: (id: number, version: number, formData: ExpenseFormData) =>
    approvalEngineApi.updateDraft(id, { version, formData: { ...formData } }),
  submit: approvalEngineApi.submitDraft,
  cancel: approvalEngineApi.cancelDraft,
  withdraw: approvalEngineApi.withdrawApplication,
  reopen: approvalEngineApi.reopenApplication,
  remind: approvalEngineApi.remindApplication,
};
