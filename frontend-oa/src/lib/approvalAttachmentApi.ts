import { queryString, request } from './oaApi';
import { buildApiHeaders } from './apiHeaders';
import { authenticatedFetch, notifyAuthResponseStatus, readAuthenticatedBlob } from './authEvents';
import i18n from '@/i18n';

export interface ApprovalAttachment {
  id: string;
  fieldName: string;
  fieldType: 'file' | 'image';
  displayName: string;
  mimeType: string;
  fileSize: number;
  createdAt: string;
}

function path(applicationId: number, attachmentId?: string) {
  if (!Number.isSafeInteger(applicationId) || applicationId < 1
    || attachmentId !== undefined && !/^[1-9][0-9]{0,18}$/.test(attachmentId)) throw new Error('PAGE_UI_FIELD_REJECTED');
  return `/approval-applications/${applicationId}/attachments${attachmentId === undefined ? '' : `/${attachmentId}`}`;
}

export const approvalAttachmentApi = {
  upload: (applicationId: number, fieldName: string, file: File, signal?: AbortSignal) => {
    const body = new FormData();
    body.append('fieldName', fieldName);
    body.append('file', file);
    return request<ApprovalAttachment>(path(applicationId), { method: 'POST', body, signal });
  },
  metadata: (applicationId: number, id: string, taskId?: number) =>
    request<ApprovalAttachment>(`${path(applicationId, id)}${queryString({ taskId })}`),
  download: async (applicationId: number, attachment: ApprovalAttachment, taskId?: number) => {
    const response = await authenticatedFetch(`/api${path(applicationId, attachment.id)}/content${queryString({ taskId })}`, {
      credentials: 'include', cache: 'no-store', headers: buildApiHeaders(false),
    });
    notifyAuthResponseStatus(response.status, response);
    if (!response.ok) throw new Error(i18n.t('approval.attachments.downloadFailed'));
    return readAuthenticatedBlob(response);
  },
};
