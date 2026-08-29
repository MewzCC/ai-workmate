'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Avatar,
  Badge,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  List,
  Select,
  Space,
  Spin,
  Statistic,
  Tag,
  Timeline,
  Typography,
  Upload,
  type UploadProps,
} from 'antd';
import { DownloadOutlined, ReloadOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import {
  hrApi,
  type EmployeeDetail,
  type EmployeeDocument,
  type EmployeeDocumentType,
  type HrEmployee,
  type OrganizationOverview,
} from '@/lib/hrApi';
import { message } from '@/lib/antdMessage';
import { useRouter, useSearchParams } from '@/lib/nextCompat';

const { Title, Text } = Typography;

/** 近期记录状态 → Tag 颜色 */
const ACTIVITY_STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'default',
};

export default function EmployeeFilePage() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const urlId = searchParams.get('id');

  const [overview, setOverview] = useState<OrganizationOverview | null>(null);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(
    urlId ? Number(urlId) : null,
  );
  const [detail, setDetail] = useState<EmployeeDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [documents, setDocuments] = useState<EmployeeDocument[]>([]);
  const [documentsLoading, setDocumentsLoading] = useState(false);
  const [documentType, setDocumentType] = useState<EmployeeDocumentType>('CONTRACT');
  const [uploading, setUploading] = useState(false);
  const [downloadingId, setDownloadingId] = useState<number>();
  const requestSeq = useRef(0);
  const canManage = user?.permissions.includes('hr:manage') ?? false;

  const loadOverview = useCallback(async () => {
    setOverviewLoading(true);
    try {
      const data = await hrApi.overview();
      setOverview(data);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('organization.loadFailed'));
    } finally {
      setOverviewLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadOverview();
  }, [loadOverview]);

  // 当 URL 中的 id 变化时（例如从组织架构跳转而来）同步选中项
  useEffect(() => {
    if (urlId && Number(urlId) !== selectedId && overview?.employees.some((e) => e.id === Number(urlId))) {
      setSelectedId(Number(urlId));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [urlId]);

  // 选中员工后加载详情；默认选中第一个员工
  useEffect(() => {
    if (overview && selectedId === null) {
      if (overview.employees.length > 0) {
        setSelectedId(overview.employees[0].id);
      }
      return;
    }
    if (selectedId === null) return;

    const seq = ++requestSeq.current;
    setDetailLoading(true);
    hrApi.detail(selectedId)
      .then((data) => {
        if (seq === requestSeq.current) setDetail(data);
      })
      .catch((error) => {
        if (seq === requestSeq.current) {
          message.error(error instanceof Error ? error.message : t('errors.hr.employeeLoadFailed'));
          setDetail(null);
        }
      })
      .finally(() => {
        if (seq === requestSeq.current) setDetailLoading(false);
      });
  }, [overview, selectedId, t]);

  const loadDocuments = useCallback(async (employeeId: number) => {
    setDocumentsLoading(true);
    try {
      setDocuments(await hrApi.listEmployeeDocuments(employeeId));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('employeeFile.documents.loadFailed'));
      setDocuments([]);
    } finally {
      setDocumentsLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (selectedId !== null) void loadDocuments(selectedId);
  }, [loadDocuments, selectedId]);

  const filteredEmployees = useMemo(() => {
    if (!overview) return [];
    const kw = keyword.trim().toLowerCase();
    if (!kw) return overview.employees;
    return overview.employees.filter(
      (emp) =>
        emp.name.toLowerCase().includes(kw) || emp.email.toLowerCase().includes(kw),
    );
  }, [overview, keyword]);

  const selectEmployee = (emp: HrEmployee) => {
    setSelectedId(emp.id);
    // 同 pathname 下仅更新 query，不重新挂载侧栏
    router.replace(`/oa/employee-files?id=${emp.id}`);
  };

  const handleRefresh = () => {
    setDetail(null);
    void loadOverview();
    if (selectedId !== null) void loadDocuments(selectedId);
  };

  const handleUpload: UploadProps['customRequest'] = async ({ file, onError, onSuccess }) => {
    if (selectedId === null || !(file instanceof File)) return;
    setUploading(true);
    try {
      const document = await hrApi.uploadEmployeeDocument(selectedId, documentType, file);
      setDocuments((current) => [document, ...current]);
      message.success(t('employeeFile.documents.uploadSuccess'));
      onSuccess?.(document);
    } catch (error) {
      const resolved = error instanceof Error ? error : new Error(t('employeeFile.documents.uploadFailed'));
      message.error(resolved.message);
      onError?.(resolved);
    } finally {
      setUploading(false);
    }
  };

  const handleDownload = async (document: EmployeeDocument) => {
    setDownloadingId(document.id);
    try {
      await hrApi.downloadEmployeeDocument(document);
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('employeeFile.documents.downloadFailed'));
    } finally {
      setDownloadingId(undefined);
    }
  };

  const formatDateTime = (value?: string | null) => {
    if (!value) return '-';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return '-';
    return `${d.toLocaleDateString()} ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  };

  const roleLabel = (role?: string | null) =>
    role ? t(`employeeFile.role.${role}`, { defaultValue: role }) : '-';

  const activityTitle = (act: EmployeeDetail['recentActivities'][number]) => {
    const ns = t(`employeeFile.activity.${act.type === 'LEAVE' ? 'leaveType' : 'clockType'}.${act.title}`, {
      defaultValue: act.title,
    });
    return ns;
  };

  const activityDateLabel = (act: EmployeeDetail['recentActivities'][number]) => {
    const start = act.startDate ? new Date(act.startDate).toLocaleDateString() : '-';
    const end = act.endDate ? new Date(act.endDate).toLocaleDateString() : '-';
    return start === end ? start : t('employeeFile.activity.dateRange', { start, end });
  };

  const attendanceItems = detail ? [
    { key: 'total', label: t('employeeFile.attendance.totalDays'), value: detail.attendance.totalDays },
    { key: 'normal', label: t('employeeFile.attendance.normalDays'), value: detail.attendance.normalDays },
    { key: 'late', label: t('employeeFile.attendance.lateDays'), value: detail.attendance.lateDays },
    { key: 'early', label: t('employeeFile.attendance.earlyLeaveDays'), value: detail.attendance.earlyLeaveDays },
    { key: 'lateEarly', label: t('employeeFile.attendance.lateAndEarlyDays'), value: detail.attendance.lateAndEarlyDays },
    { key: 'missing', label: t('employeeFile.attendance.missingClockDays'), value: detail.attendance.missingClockDays },
  ] : [];

  return (
    <div className="oa-employee-file">
      <Card className="oa-employee-file__list-card" bordered={false}>
        <div className="oa-employee-file__list-head">
          <div>
            <Title level={5} className="oa-employee-file__list-title">
              {t('employeeFile.employeeList')}
            </Title>
            <Text type="secondary" className="oa-employee-file__list-count">
              {t('organization.filter.totalEmployees', { count: filteredEmployees.length })}
            </Text>
          </div>
          <Button
            type="text"
            icon={<ReloadOutlined />}
            aria-label={t('organization.loadFailed')}
            onClick={handleRefresh}
          />
        </div>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder={t('employeeFile.searchPlaceholder')}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          className="oa-employee-file__search"
        />
        <div className="oa-employee-file__list-body">
          {overviewLoading ? (
            <div className="oa-employee-file__empty">
              <Spin />
            </div>
          ) : filteredEmployees.length === 0 ? (
            <Empty description={t('employeeFile.noEmployeeInList')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <List
              dataSource={filteredEmployees}
              split={false}
              renderItem={(emp) => {
                const active = emp.status === 1;
                const selected = emp.id === selectedId;
                return (
                  <List.Item
                    className={`oa-employee-file__person${selected ? ' is-selected' : ''}`}
                    onClick={() => selectEmployee(emp)}
                  >
                    <Badge status={active ? 'success' : 'default'} offset={[-6, 24]}>
                      <Avatar src={emp.avatarUrl || undefined} size={40}>
                        {emp.name?.charAt(0) || 'U'}
                      </Avatar>
                    </Badge>
                    <div className="oa-employee-file__person-meta">
                      <span className="oa-employee-file__person-name">{emp.name}</span>
                      <Text className="oa-employee-file__person-sub" type="secondary">
                        {roleLabel(emp.role)}
                      </Text>
                    </div>
                  </List.Item>
                );
              }}
            />
          )}
        </div>
      </Card>

      <Card className="oa-employee-file__main-card" bordered={false}>
        {detailLoading ? (
          <div className="oa-employee-file__empty">
            <Spin size="large" />
          </div>
        ) : detail ? (
          <Space direction="vertical" size={16} className="oa-employee-file__stack">
            {/* 概览卡 */}
            <div className="oa-employee-file__overview">
              <Avatar src={detail.avatarUrl || undefined} size={64}>
                {detail.name?.charAt(0) || 'U'}
              </Avatar>
              <div className="oa-employee-file__overview-meta">
                <Space align="center">
                  <Title level={4} className="oa-employee-file__overview-name">
                    {detail.name}
                  </Title>
                  <Tag color={detail.status === 1 ? 'success' : 'default'}>
                    {detail.status === 1
                      ? t('employeeFile.status.active')
                      : t('employeeFile.status.inactive')}
                  </Tag>
                </Space>
                <Space size={[8, 4]} wrap>
                  <Tag>{roleLabel(detail.role)}</Tag>
                  {detail.positionName && <Tag color="geekblue">{detail.positionName}</Tag>}
                  {detail.departmentName && <Tag color="blue">{detail.departmentName}</Tag>}
                </Space>
              </div>
            </div>

            {/* 基本 + 任职信息 */}
            <div className="oa-employee-file__grid">
              <Card
                size="small"
                className="oa-employee-file__section"
                title={t('employeeFile.profile.basicInfo')}
              >
                <Descriptions column={1} size="small" colon={false}>
                  <Descriptions.Item label={t('employeeFile.field.name')}>
                    {detail.name}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('employeeFile.field.email')}>
                    {detail.email}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('employeeFile.field.role')}>
                    {roleLabel(detail.role)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('employeeFile.field.joinDate')}>
                    {detail.createdAt ? formatDateTime(detail.createdAt) : '-'}
                  </Descriptions.Item>
                </Descriptions>
              </Card>

              <Card
                size="small"
                className="oa-employee-file__section"
                title={t('employeeFile.profile.orgInfo')}
              >
                <Descriptions column={1} size="small" colon={false}>
                  <Descriptions.Item label={t('employeeFile.field.department')}>
                    {detail.departmentName || t('employeeFile.profile.notSet')}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('employeeFile.field.position')}>
                    {detail.positionName || t('employeeFile.profile.notSet')}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('employeeFile.field.approver')}>
                    <Space size={8}>
                      {detail.approverName ? (
                        <>
                          <Avatar src={detail.approverAvatarUrl || undefined} size={20}>
                            {detail.approverName.charAt(0)}
                          </Avatar>
                          <span>{detail.approverName}</span>
                        </>
                      ) : (
                        t('employeeFile.profile.notSet')
                      )}
                    </Space>
                  </Descriptions.Item>
                </Descriptions>
              </Card>
            </div>

            <Card
              size="small"
              className="oa-employee-file__section"
              title={t('employeeFile.profile.employmentHistory')}
            >
              {detail.employmentHistory?.length ? (
                <Timeline
                  className="oa-employee-file__history"
                  items={detail.employmentHistory.map((record) => ({
                    color: 'blue',
                    children: (
                      <div className="oa-employee-file__history-item">
                        <Space size={8} wrap>
                          <Tag color="geekblue">
                            {t(`employeeFile.history.type.${record.changeType}`)}
                          </Tag>
                          <Text strong>{new Date(record.effectiveDate).toLocaleDateString()}</Text>
                        </Space>
                        <div className="oa-employee-file__history-route">
                          <Text type="secondary">
                            {[record.currentDepartmentName, record.currentPositionName, record.currentSupervisorName]
                              .filter(Boolean).join(' · ') || t('employeeFile.profile.notSet')}
                          </Text>
                          <span aria-hidden="true">→</span>
                          <Text>
                            {[record.targetDepartmentName, record.targetPositionName, record.targetSupervisorName]
                              .filter(Boolean).join(' · ') || t('employeeFile.profile.notSet')}
                          </Text>
                        </div>
                        <Text type="secondary">{record.reason}</Text>
                      </div>
                    ),
                  }))}
                />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('employeeFile.history.empty')} />
              )}
            </Card>

            <Card
              size="small"
              className="oa-employee-file__section"
              title={t('employeeFile.profile.documents')}
              extra={canManage ? (
                <Space wrap>
                  <Select<EmployeeDocumentType>
                    value={documentType}
                    onChange={setDocumentType}
                    options={(['CONTRACT', 'PROFILE'] as EmployeeDocumentType[]).map((value) => ({
                      value, label: t(`employeeFile.documents.type.${value}`),
                    }))}
                  />
                  <Upload
                    accept=".pdf,.doc,.docx,.xls,.xlsx,.txt,.md,.csv,.jpg,.jpeg,.png,.webp"
                    customRequest={handleUpload}
                    showUploadList={false}
                    disabled={uploading}
                  >
                    <Button icon={<UploadOutlined />} loading={uploading}>
                      {t('employeeFile.documents.upload')}
                    </Button>
                  </Upload>
                </Space>
              ) : undefined}
            >
              <Spin spinning={documentsLoading}>
                {documents.length ? (
                  <List
                    className="oa-employee-file__documents"
                    dataSource={documents}
                    renderItem={(document) => (
                      <List.Item
                        actions={[
                          <Button
                            key="download"
                            type="link"
                            icon={<DownloadOutlined />}
                            loading={downloadingId === document.id}
                            onClick={() => void handleDownload(document)}
                          >
                            {t('employeeFile.documents.download')}
                          </Button>,
                        ]}
                      >
                        <List.Item.Meta
                          avatar={<Tag color={document.documentType === 'CONTRACT' ? 'gold' : 'cyan'}>
                            {t(`employeeFile.documents.type.${document.documentType}`)}
                          </Tag>}
                          title={document.displayName}
                          description={t('employeeFile.documents.meta', {
                            size: formatFileSize(document.fileSize),
                            uploader: document.uploadedByName || '-',
                            time: formatDateTime(document.createdAt),
                          })}
                        />
                      </List.Item>
                    )}
                  />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('employeeFile.documents.empty')} />
                )}
              </Spin>
            </Card>

            {/* 考勤概览 */}
            <Card size="small" className="oa-employee-file__section" title={t('employeeFile.profile.attendance')}>
              {detail.attendance && detail.attendance.totalDays > 0 ? (
                <div className="oa-employee-file__attendance">
                  {attendanceItems.map((item) => (
                    <div className="oa-employee-file__attendance-item" key={item.key}>
                      <Statistic title={item.label} value={item.value} />
                    </div>
                  ))}
                </div>
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('employeeFile.attendance.empty')} />
              )}
            </Card>

            {/* 近期记录 */}
            <Card size="small" className="oa-employee-file__section" title={t('employeeFile.profile.recentActivities')}>
              {detail.recentActivities && detail.recentActivities.length > 0 ? (
                <List
                  dataSource={detail.recentActivities}
                  renderItem={(act) => (
                    <List.Item>
                      <List.Item.Meta
                        avatar={
                          <Tag color={act.type === 'LEAVE' ? 'blue' : 'orange'}>
                            {act.type === 'LEAVE'
                              ? t('employeeFile.activity.leave')
                              : t('employeeFile.activity.reissue')}
                          </Tag>
                        }
                        title={
                          <Space size={8}>
                            <span>{activityTitle(act)}</span>
                            <Tag color={ACTIVITY_STATUS_COLOR[act.status] || 'default'}>
                              {t(`employeeFile.activity.status.${act.status}`, { defaultValue: act.status })}
                            </Tag>
                          </Space>
                        }
                        description={
                          <Space size={12}>
                            <span>{activityDateLabel(act)}</span>
                            {act.createdAt && (
                              <Text type="secondary">{formatDateTime(act.createdAt)}</Text>
                            )}
                          </Space>
                        }
                      />
                    </List.Item>
                  )}
                />
              ) : (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={t('employeeFile.activity.empty')}
                />
              )}
            </Card>
          </Space>
        ) : (
          <Empty
            className="oa-employee-file__select-prompt"
            description={t('employeeFile.selectPrompt')}
          />
        )}
      </Card>
    </div>
  );
}
