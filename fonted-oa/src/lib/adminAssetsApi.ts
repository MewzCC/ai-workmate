import { request, queryString, type PageResponse } from '@/lib/oaApi';

// ==================== 资产台账 ====================

export type AssetStatus = 'IN_USE' | 'IDLE' | 'REPAIRING' | 'SCRAPPED';

export interface AssetLedgerPayload {
  assetCode: string;
  name: string;
  category: string;
  specification?: string;
  status?: AssetStatus;
  departmentId?: number | null;
  ownerUserId?: number | null;
  purchaseDate?: string | null;
  originalValue?: number | null;
  remark?: string;
}

export interface AssetLedger {
  id: number;
  assetCode: string;
  name: string;
  category: string;
  specification?: string | null;
  status: AssetStatus;
  departmentId?: number | null;
  departmentName?: string | null;
  ownerUserId?: number | null;
  ownerName?: string | null;
  purchaseDate?: string | null;
  originalValue?: number | null;
  remark?: string | null;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canDelete: boolean;
}

// ==================== 会议室 ====================

export type MeetingRoomStatus = 'OPEN' | 'CLOSED';

export interface MeetingRoomPayload {
  code: string;
  name: string;
  location?: string;
  capacity?: number;
  facilities?: string;
  status?: MeetingRoomStatus;
  remark?: string;
}

export interface MeetingRoom {
  id: number;
  code: string;
  name: string;
  location?: string | null;
  capacity: number;
  facilities?: string | null;
  status: MeetingRoomStatus;
  remark?: string | null;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canDelete: boolean;
}

// ==================== 访客预约 ====================

export type VisitorBookingStatus =
  | 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'VISITED';

export interface VisitorBookingPayload {
  visitorName: string;
  visitorCompany?: string;
  visitorPhone?: string;
  purpose: string;
  hostUserId: number;
  expectedVisitAt: string;
  expectedLeaveAt?: string | null;
  plateNumber?: string;
  partySize?: number;
}

export interface VisitorBooking {
  id: number;
  applicantUserId: number;
  applicantName?: string | null;
  approverUserId?: number | null;
  approverName?: string | null;
  hostUserId: number;
  hostName?: string | null;
  visitorName: string;
  visitorCompany?: string | null;
  visitorPhone?: string | null;
  purpose: string;
  expectedVisitAt: string;
  expectedLeaveAt?: string | null;
  plateNumber?: string | null;
  partySize: number;
  status: VisitorBookingStatus;
  version: number;
  workflowInstanceId?: number | null;
  taskId?: number | null;
  taskVersion?: number | null;
  taskStatus?: string | null;
  submittedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  canWithdraw: boolean;
  canDecide: boolean;
}

// ==================== 印章用印 ====================

export type SealType = 'OFFICIAL' | 'CONTRACT' | 'LEGAL' | 'FINANCE' | 'OTHER';
export type SealUsageStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export interface SealUsagePayload {
  sealType?: SealType;
  documentTitle: string;
  usageReason: string;
  copies?: number;
}

export interface SealUsage {
  id: number;
  applicantUserId: number;
  applicantName?: string | null;
  approverUserId?: number | null;
  approverName?: string | null;
  sealType: SealType;
  documentTitle: string;
  usageReason: string;
  copies: number;
  status: SealUsageStatus;
  version: number;
  workflowInstanceId?: number | null;
  taskId?: number | null;
  taskVersion?: number | null;
  taskStatus?: string | null;
  submittedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  canWithdraw: boolean;
  canDecide: boolean;
}

// ==================== 通用 ====================

export interface VersionPayload {
  version: number;
}

export interface ApprovalDecisionPayload {
  version: number;
  comment?: string;
}

// ==================== API 封装 ====================

const PREFIX = '/admin-assets';

export const adminAssetsApi = {
  // ---------- 资产台账 ----------
  listAssets: (params: {
    keyword?: string;
    category?: string;
    status?: AssetStatus;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<AssetLedger>>(`${PREFIX}/assets${queryString(params)}`),

  getAsset: (id: number) =>
    request<AssetLedger>(`${PREFIX}/assets/${id}`),

  createAsset: (payload: AssetLedgerPayload) =>
    request<AssetLedger>(`${PREFIX}/assets`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateAsset: (id: number, payload: AssetLedgerPayload) =>
    request<AssetLedger>(`${PREFIX}/assets/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  deleteAsset: (id: number) =>
    request<void>(`${PREFIX}/assets/${id}`, { method: 'DELETE' }),

  // ---------- 会议室 ----------
  listMeetingRooms: (params: {
    keyword?: string;
    status?: MeetingRoomStatus;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<MeetingRoom>>(`${PREFIX}/meeting-rooms${queryString(params)}`),

  getMeetingRoom: (id: number) =>
    request<MeetingRoom>(`${PREFIX}/meeting-rooms/${id}`),

  createMeetingRoom: (payload: MeetingRoomPayload) =>
    request<MeetingRoom>(`${PREFIX}/meeting-rooms`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateMeetingRoom: (id: number, payload: MeetingRoomPayload) =>
    request<MeetingRoom>(`${PREFIX}/meeting-rooms/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  deleteMeetingRoom: (id: number) =>
    request<void>(`${PREFIX}/meeting-rooms/${id}`, { method: 'DELETE' }),

  // ---------- 访客预约 ----------
  submitVisitorBooking: (payload: VisitorBookingPayload) =>
    request<VisitorBooking>(`${PREFIX}/visitor-bookings`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getVisitorBooking: (id: number) =>
    request<VisitorBooking>(`${PREFIX}/visitor-bookings/${id}`),

  listMyVisitorBookings: (params: {
    status?: VisitorBookingStatus;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<VisitorBooking>>(`${PREFIX}/visitor-bookings/mine${queryString(params)}`),

  listPendingVisitorBookings: (params: { page?: number; size?: number } = {}) =>
    request<PageResponse<VisitorBooking>>(`${PREFIX}/visitor-bookings/pending${queryString(params)}`),

  withdrawVisitorBooking: (id: number, payload: VersionPayload) =>
    request<VisitorBooking>(`${PREFIX}/visitor-bookings/${id}/withdraw`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  approveVisitorBooking: (taskId: number, payload: ApprovalDecisionPayload) =>
    request<VisitorBooking>(`${PREFIX}/visitor-bookings/tasks/${taskId}/approve`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  rejectVisitorBooking: (taskId: number, payload: ApprovalDecisionPayload) =>
    request<VisitorBooking>(`${PREFIX}/visitor-bookings/tasks/${taskId}/reject`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  // ---------- 印章用印 ----------
  submitSealUsage: (payload: SealUsagePayload) =>
    request<SealUsage>(`${PREFIX}/seal-usages`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getSealUsage: (id: number) =>
    request<SealUsage>(`${PREFIX}/seal-usages/${id}`),

  listMySealUsages: (params: {
    status?: SealUsageStatus;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<SealUsage>>(`${PREFIX}/seal-usages/mine${queryString(params)}`),

  listPendingSealUsages: (params: { page?: number; size?: number } = {}) =>
    request<PageResponse<SealUsage>>(`${PREFIX}/seal-usages/pending${queryString(params)}`),

  withdrawSealUsage: (id: number, payload: VersionPayload) =>
    request<SealUsage>(`${PREFIX}/seal-usages/${id}/withdraw`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  approveSealUsage: (taskId: number, payload: ApprovalDecisionPayload) =>
    request<SealUsage>(`${PREFIX}/seal-usages/tasks/${taskId}/approve`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  rejectSealUsage: (taskId: number, payload: ApprovalDecisionPayload) =>
    request<SealUsage>(`${PREFIX}/seal-usages/tasks/${taskId}/reject`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};
