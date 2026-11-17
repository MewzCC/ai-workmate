export interface ApprovalTaskCoordinates { taskId: number; businessId: number; businessType: string; detailAvailable: boolean }

/** Code-owned routes only. Resource IDs and workflow task IDs have different meanings. */
export function approvalTaskTarget(task: ApprovalTaskCoordinates, source: 'todo' | 'dashboard'): string | null {
  if (!Number.isSafeInteger(task.taskId) || task.taskId < 1 || task.detailAvailable !== true) return null;
  if (task.businessType === 'LEAVE_APPLICATION') return `/oa/approval-tasks/${task.taskId}?from=${source}`;
  if (task.businessType === 'GENERIC_APPROVAL') return `/oa/todo?kind=generic&taskId=${task.taskId}`;
  if (!Number.isSafeInteger(task.businessId) || task.businessId < 1) return null;
  if (task.businessType === 'VISITOR_BOOKING') return `/oa/visitor-booking?id=${task.businessId}`;
  if (task.businessType === 'SEAL_USAGE') return `/oa/seal-usage?id=${task.businessId}`;
  return null;
}

export function approvalTaskRoutePermission(businessType: string): string | null {
  if (businessType === 'LEAVE_APPLICATION' || businessType === 'GENERIC_APPROVAL') return 'route:todo';
  if (businessType === 'VISITOR_BOOKING') return 'route:visitor-booking';
  if (businessType === 'SEAL_USAGE') return 'route:seal-usage';
  return null;
}

export function todoBusinessLabelKey(type: string): string {
  switch (type) {
    case 'ALL': return 'approval.genericTasks.allTab';
    case 'LEAVE_APPLICATION': return 'approval.genericTasks.leaveTab';
    case 'GENERIC_APPROVAL': return 'approval.genericTasks.genericTab';
    case 'VISITOR_BOOKING': return 'approval.genericTasks.visitorTab';
    case 'SEAL_USAGE': return 'approval.genericTasks.sealTab';
    default: return 'approval.genericTasks.unsupportedType';
  }
}
