'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  Alert,
  Avatar,
  Button,
  Card,
  DatePicker,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Result,
  Space,
  Spin,
  Steps,
  Tag,
  TimePicker,
  Typography,
} from 'antd';
import { useTranslation } from 'react-i18next';
import dayjs, { type Dayjs } from 'dayjs';
import {
  approvalEngineApi,
  type ApprovalForm,
  type ApprovalProcess,
  type ApprovalProcessNode,
} from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import { useAuth } from '@/components/auth/AuthProvider';
import { message } from '@/lib/antdMessage';

/**
 * 通用申请页：按 form_key 动态渲染 approval_form 表单，
 * 走通用提交接口 /api/approval-applications。
 * 布局对齐 LeaveFormPage（左表单 + 右流程预览），请假页保持独立不受影响。
 */

interface SchemaField {
  name?: string;
  label?: string;
  type?: string;
  required?: boolean;
}

/** 在线填写暂不支持、降级为文本输入并提示的复杂类型。 */
const FALLBACK_TEXT_TYPES = new Set(['dateRange', 'user', 'department', 'file', 'image', 'table']);

/** 审批人类型 → i18n key 后缀（approval.designer.approveType.*）。 */
const APPROVE_TYPE_LABEL_KEYS: Record<string, string> = {
  DIRECT_MANAGER: 'directManager',
  DEPARTMENT: 'department',
  USER: 'user',
  SELF: 'self',
  MULTI_LEVEL: 'multiLevel',
  ROLE: 'role',
};

function parseSchemaFields(schemaJson: string): SchemaField[] {
  try {
    const parsed = JSON.parse(schemaJson) as { fields?: SchemaField[] };
    return Array.isArray(parsed.fields) ? parsed.fields.filter((f) => f && f.name) : [];
  } catch {
    return [];
  }
}

function parseNodes(nodeJson: string): ApprovalProcessNode[] {
  try {
    const parsed = JSON.parse(nodeJson);
    return Array.isArray(parsed) ? (parsed as ApprovalProcessNode[]) : [];
  } catch {
    return [];
  }
}

export default function ApprovalFormPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useTranslation();
  const { user } = useAuth();
  const formKey = searchParams.get('formKey') || '';

  const [form] = Form.useForm<Record<string, unknown>>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [formDef, setFormDef] = useState<ApprovalForm | null>(null);
  const [process, setProcess] = useState<ApprovalProcess | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(undefined);
    setFormDef(null);
    setProcess(null);
    try {
      const [formPage, processPage] = await Promise.all([
        approvalEngineApi.listForms({ status: 'ENABLED', page: 1, size: 100 }),
        approvalEngineApi.listProcesses({ status: 'ENABLED', page: 1, size: 100 }),
      ]);
      const matched = formPage.records.find((item) => item.formKey === formKey) || null;
      setFormDef(matched);
      setProcess(matched
        ? processPage.records.find((item) => item.formId === matched.id) || null
        : null);
    } catch (err) {
      setLoadError(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [formKey]);

  useEffect(() => {
    if (!formKey) {
      setFormDef(null);
      setProcess(null);
      setLoading(false);
      return;
    }
    void load();
  }, [formKey, load]);

  // 切换表单时清空上一份草稿内容
  useEffect(() => {
    form.resetFields();
  }, [formDef?.id, form]);

  const fields = useMemo(
    () => (formDef ? parseSchemaFields(formDef.schemaJson) : []),
    [formDef],
  );
  const nodes = useMemo(
    () => (process ? parseNodes(process.nodeJson) : []),
    [process],
  );

  const backToStart = () => router.push('/oa/approval-start');

  const handleSubmitClick = async () => {
    if (!formDef) return;
    try {
      await form.validateFields();
    } catch {
      return; // 校验错误已由表单项内联展示
    }
    const values = form.getFieldsValue();
    Modal.confirm({
      title: t('approval.formPage.confirmTitle'),
      width: 520,
      content: (
        <div className="approval-template-fields">
          <div>
            <Typography.Text>{t('approval.start.boundProcess')}</Typography.Text>
            <Tag>{process?.processName || '-'}</Tag>
          </div>
          {fields
            .filter((field) => values[field.name as string] !== undefined && values[field.name as string] !== '')
            .map((field) => (
              <div key={field.name}>
                <Typography.Text>{field.label || field.name}</Typography.Text>
                <Typography.Text type="secondary">
                  {formatValueForConfirm(values[field.name as string], field.type)}
                </Typography.Text>
              </div>
            ))}
        </div>
      ),
      okText: t('approval.formPage.confirmOk'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        setSubmitting(true);
        try {
          await approvalEngineApi.submitApplication({
            formKey: formDef.formKey,
            processKey: process?.processKey,
            formData: normalizeFormValues(values, fields),
          });
          message.success(t('approval.formPage.submitSuccess'));
          backToStart();
        } catch (error) {
          message.error(formatOaApiError(error));
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  // ---------- 缺少 formKey ----------
  if (!formKey) {
    return (
      <section className="leave-workbench">
        <Card className="leave-list-card" variant="borderless">
          <Result
            status="info"
            title={t('approval.formPage.missingKeyTitle')}
            subTitle={t('approval.formPage.missingKeyDesc')}
            extra={<Button type="primary" onClick={backToStart}>{t('approval.formPage.backToStart')}</Button>}
          />
        </Card>
      </section>
    );
  }

  // ---------- 加载失败 / 表单不存在 ----------
  if (!loading && !formDef) {
    return (
      <section className="leave-workbench">
        <Card className="leave-list-card" variant="borderless">
          {loadError ? (
            <Alert
              showIcon
              type="error"
              message={t('approval.start.loadFailedTitle')}
              description={t('approval.start.loadFailedDesc', { error: loadError })}
              action={<Button size="small" icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.retry')}</Button>}
            />
          ) : (
            <Empty description={t('approval.formPage.notFoundDesc')}>
              <Button type="primary" onClick={backToStart}>{t('approval.formPage.backToStart')}</Button>
            </Empty>
          )}
        </Card>
      </section>
    );
  }

  return (
    <section className="leave-workbench">
      <header className="leave-page-hero">
        <div>
          <div className="leave-page-hero__kicker">
            <span>OA · REQUEST FORM</span>
          </div>
          <Typography.Title level={2}>{formDef?.formName || <Spin size="small" />}</Typography.Title>
          <Typography.Paragraph>
            {formDef?.description || t('approval.start.noDescription')}
          </Typography.Paragraph>
        </div>
        <div className="leave-page-hero__serial">
          <span>{t('approval.config.form.formKey')}</span>
          <strong>{formDef?.formKey ? formDef.formKey : '--'}</strong>
        </div>
      </header>

      <Spin spinning={loading}>
        <div className="leave-compose-grid">
          <Card className="leave-form-card" variant="borderless">
            <div className="leave-section-title">
              <span className="leave-section-title__index">01</span>
              <div>
                <Typography.Title level={4}>{t('approval.formPage.sectionFormTitle')}</Typography.Title>
                <Typography.Text type="secondary">{t('approval.formPage.sectionFormHint')}</Typography.Text>
              </div>
            </div>

            {!process && !loading && (
              <Alert
                className="leave-inline-alert"
                showIcon
                type="warning"
                message={t('approval.start.noProcessAlert')}
              />
            )}

            {fields.length === 0 && !loading ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('approval.start.emptyFields')} />
            ) : (
              <Form form={form} layout="vertical" className="leave-enterprise-form" requiredMark="optional">
                {fields.map((field) => {
                  if (field.type === 'divider') return null;
                  const label = field.label || field.name || '';
                  return (
                    <Form.Item
                      key={field.name}
                      name={field.name}
                      label={label}
                      rules={field.required
                        ? [{ required: true, message: t('approval.start.fieldRequired', { field: label }) }]
                        : undefined}
                      extra={FALLBACK_TEXT_TYPES.has(field.type || '')
                        ? t('approval.start.unsupportedFieldAlert', { field: label })
                        : undefined}
                    >
                      {renderFieldControl(field)}
                    </Form.Item>
                  );
                })}

                <div className="leave-form-footer">
                  <Typography.Text type="secondary">
                    {t('approval.formPage.footerConfirm')}
                  </Typography.Text>
                  <Space wrap>
                    <Button size="large" onClick={backToStart}>{t('common.cancel')}</Button>
                    <Button
                      size="large"
                      type="primary"
                      icon={<OaIcon name="send" />}
                      loading={submitting}
                      disabled={!process || fields.length === 0}
                      onClick={() => void handleSubmitClick()}
                    >
                      {t('approval.start.submit')}
                    </Button>
                  </Space>
                </div>
              </Form>
            )}
          </Card>

          <aside className="leave-workflow-panel" aria-label={t('approval.formPage.previewAriaLabel')}>
            <div className="leave-workflow-panel__masthead">
              <div className="leave-workflow-panel__eyebrow">
                <OaIcon name="process" />
                <span>PROCESS PREVIEW</span>
              </div>
              <Typography.Title level={4}>{t('approval.formPage.previewTitle')}</Typography.Title>
              <Typography.Paragraph>
                {process?.description || t('approval.formPage.previewDescription')}
              </Typography.Paragraph>
            </div>

            <div className="leave-workflow-panel__identity">
              <Avatar size={44} src={user?.avatarUrl || undefined} icon={<OaIcon name="user" />}>
                {(user?.name || '?').slice(0, 1).toUpperCase()}
              </Avatar>
              <div>
                <Typography.Text type="secondary">{t('approval.workflow.applicantLabel')}</Typography.Text>
                <Typography.Text strong>{user?.name || t('approval.workflow.applicantFallback')}</Typography.Text>
              </div>
              <OaIcon name="next" className="leave-workflow-panel__arrow" />
              <Avatar size={44} icon={<OaIcon name="approval" />} />
              <div>
                <Typography.Text type="secondary">{t('approval.workflow.approverLabel')}</Typography.Text>
                <Typography.Text strong>
                  {nodes[0]?.nodeName || t('approval.start.unboundProcess')}
                </Typography.Text>
              </div>
            </div>

            <Divider />

            <Steps
              orientation="vertical"
              size="small"
              current={0}
              items={[
                {
                  title: t('approval.formPage.stageSubmitTitle'),
                  status: 'process',
                  content: (
                    <div className="leave-workflow-stage">
                      <span>{t('approval.formPage.stageSubmitDesc')}</span>
                    </div>
                  ),
                },
                ...nodes.map((node, index) => ({
                  title: node.nodeName || `${t('approval.designer.nodes')} ${index + 1}`,
                  status: 'wait' as const,
                  content: (
                    <div className="leave-workflow-stage">
                      <span>
                        {node.approveType
                          ? t(`approval.designer.approveType.${APPROVE_TYPE_LABEL_KEYS[node.approveType] || node.approveType}`, { defaultValue: node.approveType })
                          : t('approval.formPage.stageNodeFallback')}
                      </span>
                    </div>
                  ),
                })),
                {
                  title: t('approval.formPage.stageArchiveTitle'),
                  status: 'wait' as const,
                  content: (
                    <div className="leave-workflow-stage">
                      <span>{t('approval.formPage.stageArchiveDesc')}</span>
                    </div>
                  ),
                },
              ]}
            />

            <div className="leave-workflow-panel__policy">
              <OaIcon name="lock" />
              <span>{t('approval.formPage.policyNote')}</span>
            </div>
          </aside>
        </div>
      </Spin>
    </section>
  );
}

function renderFieldControl(field: SchemaField) {
  switch (field.type) {
    case 'textarea':
      return <Input.TextArea rows={4} maxLength={500} showCount />;
    case 'number':
    case 'money':
      return <InputNumber style={{ width: '100%' }} />;
    case 'date':
      return <DatePicker style={{ width: '100%' }} />;
    case 'time':
      return <TimePicker style={{ width: '100%' }} format="HH:mm" />;
    default:
      return <Input maxLength={200} />;
  }
}

/** 把表单值规整为后端 schema 校验可接受的 JSON 标量。 */
function normalizeFormValues(
  values: Record<string, unknown>,
  fields: SchemaField[],
): Record<string, unknown> {
  const data: Record<string, unknown> = {};
  fields.forEach((field) => {
    const value = values[field.name as string];
    if (value === undefined || value === null || value === '') return;
    if (dayjs.isDayjs(value)) {
      const day = value as Dayjs;
      data[field.name as string] = field.type === 'time' ? day.format('HH:mm') : day.format('YYYY-MM-DD');
      return;
    }
    data[field.name as string] = value;
  });
  return data;
}

function formatValueForConfirm(value: unknown, type?: string): string {
  if (value == null || value === '') return '-';
  if (dayjs.isDayjs(value)) {
    const day = value as Dayjs;
    return type === 'time' ? day.format('HH:mm') : day.format('YYYY-MM-DD');
  }
  return String(value);
}
