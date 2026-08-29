'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Input,
  Modal,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import {
  approvalEngineApi,
  type ApprovalApplication,
  type ApprovalForm,
  type ApprovalProcess,
} from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { OaIcon, type OaIconName } from '@/components/OaIcon';

type TemplateCategory = 'hr' | 'finance' | 'admin' | 'purchase' | 'other';

/** 模板展示元信息：分类 + 图标。仅影响展示，模板数据本身来自表单/流程定义。 */
const TEMPLATE_META: Record<string, { category: TemplateCategory; icon: OaIconName; common?: boolean }> = {
  'leave-application': { category: 'hr', icon: 'form', common: true },
  'business-trip': { category: 'admin', icon: 'send', common: true },
  expense: { category: 'finance', icon: 'expense', common: true },
  overtime: { category: 'hr', icon: 'attendance', common: true },
  purchase: { category: 'purchase', icon: 'suppliers', common: true },
  payment: { category: 'finance', icon: 'finance', common: true },
};

const CATEGORY_ICON: Record<TemplateCategory, OaIconName> = {
  hr: 'hr',
  finance: 'finance',
  admin: 'assets',
  purchase: 'suppliers',
  other: 'process',
};

const CATEGORIES: TemplateCategory[] = ['hr', 'finance', 'admin', 'purchase', 'other'];

/** 打车内置请假模板入口：请假表单路由对所有登录角色开放。 */
const LEAVE_FORM_KEY = 'leave-application';

interface SnapshotField {
  name?: string;
  label?: string;
}

function parseSnapshotFields(schemaJson?: string | null): SnapshotField[] {
  try {
    const parsed = JSON.parse(schemaJson || '{}') as { fields?: SnapshotField[] };
    return Array.isArray(parsed.fields) ? parsed.fields.filter((field) => field?.name) : [];
  } catch {
    return [];
  }
}

function parseSnapshotData(dataJson: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(dataJson) as unknown;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : {};
  } catch {
    return {};
  }
}

function formatSnapshotValue(value: unknown): string {
  if (value == null || value === '') return '-';
  if (Array.isArray(value)) return value.map(formatSnapshotValue).join(', ');
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

export default function ApprovalStartPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [forms, setForms] = useState<ApprovalForm[]>([]);
  const [processes, setProcesses] = useState<ApprovalProcess[]>([]);
  const [drafts, setDrafts] = useState<ApprovalApplication[]>([]);
  const [recentApplications, setRecentApplications] = useState<ApprovalApplication[]>([]);
  const [actingId, setActingId] = useState<number>();
  const [detail, setDetail] = useState<ApprovalApplication | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loadError, setLoadError] = useState<string>();
  const [keyword, setKeyword] = useState('');

  const load = useCallback(async () => {
    setLoadError(undefined);
    try {
      const [formPage, processPage, draftPage, applicationPage] = await Promise.all([
        approvalEngineApi.listForms({ status: 'ENABLED', page: 1, size: 100 }),
        approvalEngineApi.listProcesses({ status: 'ENABLED', page: 1, size: 100 }),
        approvalEngineApi.listMyApplications({ status: 'DRAFT', page: 1, size: 20 }),
        approvalEngineApi.listMyApplications({ page: 1, size: 20 }),
      ]);
      setForms(formPage.records);
      setProcesses(processPage.records);
      setDrafts(draftPage.records);
      setRecentApplications(applicationPage.records.filter((item) => item.status !== 'DRAFT'));
    } catch (err) {
      setForms([]);
      setProcesses([]);
      setDrafts([]);
      setRecentApplications([]);
      setLoadError(formatOaApiError(err));
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const processByFormId = useMemo(
    () => new Map(processes.filter((p) => p.formId != null).map((p) => [p.formId as number, p])),
    [processes],
  );

  const visibleForms = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    return forms.filter((form) =>
      !q
      || form.formName.toLowerCase().includes(q)
      || (form.formKey || '').toLowerCase().includes(q)
      || (form.description || '').toLowerCase().includes(q),
    );
  }, [forms, keyword]);

  const hasTemplates = forms.length > 0;
  const commonForms = visibleForms
    .filter((form) => TEMPLATE_META[form.formKey]?.common)
    .sort((a, b) => {
      const rank = (key: string) =>
        Object.keys(TEMPLATE_META).indexOf(key);
      return rank(a.formKey) - rank(b.formKey);
    });

  /** 请假走专用页面；其余模板进入通用申请页按 form_key 动态渲染。 */
  const openTemplate = (form: ApprovalForm) => {
    if (form.formKey === LEAVE_FORM_KEY) {
      router.push('/oa/leave-application');
      return;
    }
    router.push(`/oa/approval-form?formKey=${encodeURIComponent(form.formKey)}`);
  };

  const withdraw = (application: ApprovalApplication) => {
    Modal.confirm({
      title: t('approval.start.withdrawTitle'),
      content: t('approval.start.withdrawContent'),
      okText: t('approval.start.withdrawOk'),
      okButtonProps: { danger: true },
      cancelText: t('common.cancel'),
      onOk: async () => {
        setActingId(application.id);
        try {
          await approvalEngineApi.withdrawApplication(application.id, application.version);
          message.success(t('approval.start.withdrawSuccess'));
          await load();
        } catch (error) {
          message.error(formatOaApiError(error));
        } finally {
          setActingId(undefined);
        }
      },
    });
  };

  const reopen = async (application: ApprovalApplication) => {
    setActingId(application.id);
    try {
      const draft = await approvalEngineApi.reopenApplication(application.id, application.version);
      message.success(t('approval.start.reopenSuccess'));
      router.push(`/oa/approval-form?formKey=${encodeURIComponent(draft.formKey)}&draftId=${draft.id}`);
    } catch (error) {
      message.error(formatOaApiError(error));
      setActingId(undefined);
    }
  };

  const remind = async (application: ApprovalApplication) => {
    setActingId(application.id);
    try {
      await approvalEngineApi.remindApplication(application.id, application.version);
      message.success(t('approval.start.remindSuccess'));
      await load();
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setActingId(undefined);
    }
  };

  const viewHistoricalDetail = async (application: ApprovalApplication) => {
    setDetailLoading(true);
    try {
      setDetail(await approvalEngineApi.getApplication(application.id));
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setDetailLoading(false);
    }
  };

  const detailFields = parseSnapshotFields(detail?.formSchemaSnapshot);
  const detailData = detail ? parseSnapshotData(detail.dataJson) : {};

  return (
    <section className="leave-list-workbench approval-start-page">
      <header className="leave-list-hero">
        <div>
          <span className="leave-list-hero__kicker">APPROVAL START</span>
          <Typography.Title level={2}>{t('approval.start.title')}</Typography.Title>
          <Typography.Paragraph>{t('approval.start.description')}</Typography.Paragraph>
        </div>
        <Input.Search
          className="approval-start-search"
          placeholder={t('approval.start.searchPlaceholder')}
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onSearch={() => undefined}
        />
      </header>

      {loadError && (
        <Alert
          showIcon
          type="error"
          message={t('approval.start.loadFailedTitle')}
          description={t('approval.start.loadFailedDesc', { error: loadError })}
          action={<Button size="small" icon={<OaIcon name="reload" />} onClick={() => void load()}>{t('common.retry')}</Button>}
        />
      )}

      {drafts.length > 0 && (
        <section className="approval-start-section">
          <div className="approval-start-section__head">
            <OaIcon name="edit" />
            <Typography.Title level={4}>{t('approval.start.myDrafts')}</Typography.Title>
          </div>
          <div className="approval-start-grid">
            {drafts.map((draft) => (
              <Card
                key={draft.id}
                className="approval-template-card"
                variant="borderless"
                hoverable
                onClick={() => router.push(`/oa/approval-form?formKey=${encodeURIComponent(draft.formKey)}&draftId=${draft.id}`)}
              >
                <div className="approval-template-card__head">
                  <span className="approval-template-card__icon is-other"><OaIcon name="edit" /></span>
                  <Typography.Title level={5}>{draft.formName}</Typography.Title>
                </div>
                <Typography.Paragraph type="secondary">
                  {t('approval.start.draftUpdatedAt', { time: new Date(draft.updatedAt).toLocaleString() })}
                </Typography.Paragraph>
                <Tag bordered={false}>{t('approval.start.draftStatus')}</Tag>
              </Card>
            ))}
          </div>
        </section>
      )}

      {recentApplications.length > 0 && (
        <section className="approval-start-section">
          <div className="approval-start-section__head">
            <OaIcon name="history" />
            <Typography.Title level={4}>{t('approval.start.recentApplications')}</Typography.Title>
          </div>
          <div className="approval-start-grid">
            {recentApplications.map((application) => (
              <Card key={application.id} className="approval-template-card" variant="borderless">
                <div className="approval-template-card__head">
                  <span className="approval-template-card__icon is-other"><OaIcon name="approval" /></span>
                  <Typography.Title level={5}>{application.formName}</Typography.Title>
                </div>
                <Typography.Paragraph type="secondary">
                  {t('approval.start.applicationUpdatedAt', { time: new Date(application.updatedAt).toLocaleString() })}
                </Typography.Paragraph>
                <div className="approval-template-card__foot">
                  <Tag bordered={false}>{t(`approval.status.${application.status}`)}</Tag>
                  <Space size={4}>
                    <Button
                      type="link"
                      size="small"
                      onClick={() => void viewHistoricalDetail(application)}
                    >
                      {t('approval.start.viewHistoricalDetail')}
                    </Button>
                    {application.canWithdraw && (
                      <Button
                        danger
                        type="link"
                        size="small"
                        loading={actingId === application.id}
                        onClick={() => withdraw(application)}
                      >
                        {t('approval.start.withdraw')}
                      </Button>
                    )}
                    {application.status === 'PENDING' && application.taskId && (
                      <Button
                        type="link"
                        size="small"
                        disabled={!application.canRemind}
                        loading={actingId === application.id}
                        title={!application.canRemind && application.remindAvailableAt
                          ? t('approval.start.remindAvailableAt', {
                            time: new Date(application.remindAvailableAt).toLocaleString(),
                          })
                          : undefined}
                        onClick={() => void remind(application)}
                      >
                        {t('approval.start.remind', { count: application.reminderCount })}
                      </Button>
                    )}
                    {(application.status === 'REJECTED' || application.status === 'WITHDRAWN') && (
                      <Button
                        type="link"
                        size="small"
                        loading={actingId === application.id}
                        onClick={() => void reopen(application)}
                      >
                        {t('approval.start.editAndResubmit')}
                      </Button>
                    )}
                  </Space>
                </div>
              </Card>
            ))}
          </div>
        </section>
      )}

      {!hasTemplates && !loadError && (
        <Card className="leave-list-card" variant="borderless">
          <Empty description={t('approval.start.emptyTemplates')} />
        </Card>
      )}

      {hasTemplates && (
        <>
          <section className="approval-start-section">
            <div className="approval-start-section__head">
              <OaIcon name="todo" />
              <Typography.Title level={4}>{t('approval.start.commonTitle')}</Typography.Title>
            </div>
            <div className="approval-start-grid">
              {commonForms.map((form) => (
                <TemplateCard
                  key={form.id}
                  form={form}
                  meta={TEMPLATE_META[form.formKey] || { category: 'other', icon: 'form' }}
                  process={processByFormId.get(form.id)}
                  onOpen={() => openTemplate(form)}
                />
              ))}
              {commonForms.length === 0 && (
                <Card className="leave-empty-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /></Card>
              )}
            </div>
          </section>

          {CATEGORIES.map((category) => {
            const items = visibleForms.filter(
              (form) => (TEMPLATE_META[form.formKey] || { category: 'other' }).category === category,
            );
            if (items.length === 0) return null;
            return (
              <section key={category} className="approval-start-section">
                <div className="approval-start-section__head">
                  <OaIcon name={CATEGORY_ICON[category]} />
                  <Typography.Title level={4}>{t(`approval.start.category.${category}`)}</Typography.Title>
                </div>
                <div className="approval-start-grid">
                  {items.map((form) => (
                    <TemplateCard
                      key={form.id}
                      form={form}
                      meta={TEMPLATE_META[form.formKey] || { category: 'other', icon: 'form' }}
                      process={processByFormId.get(form.id)}
                      onOpen={() => openTemplate(form)}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </>
      )}

      <Modal
        open={detailLoading || Boolean(detail)}
        title={t('approval.start.historicalDetailTitle')}
        footer={<Button onClick={() => setDetail(null)}>{t('common.close')}</Button>}
        onCancel={() => setDetail(null)}
        width={720}
      >
        <Spin spinning={detailLoading}>
          {detail && (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Alert
                showIcon
                type="info"
                message={t('approval.start.snapshotNotice')}
              />
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label={t('approval.start.snapshotFormVersion')}>
                  {detail.formVersionSnapshot ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('approval.start.snapshotProcessVersion')}>
                  {detail.processVersionSnapshot ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label={t('approval.start.snapshotStatus')}>
                  <Tag>{t(`approval.status.${detail.status}`)}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label={t('approval.start.snapshotSubmittedAt')}>
                  {detail.submittedAt ? new Date(detail.submittedAt).toLocaleString() : '-'}
                </Descriptions.Item>
                {detailFields.map((field) => (
                  <Descriptions.Item key={field.name} label={field.label || field.name} span={2}>
                    {formatSnapshotValue(detailData[field.name as string])}
                  </Descriptions.Item>
                ))}
              </Descriptions>
              {detailFields.length === 0 && (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('approval.start.snapshotUnavailable')} />
              )}
            </Space>
          )}
        </Spin>
      </Modal>
    </section>
  );
}

function TemplateCard({
  form,
  meta,
  process,
  onOpen,
}: {
  form: ApprovalForm;
  meta: { category: TemplateCategory; icon: OaIconName };
  process?: ApprovalProcess;
  onOpen: () => void;
}) {
  const { t } = useTranslation();
  const isLeave = form.formKey === LEAVE_FORM_KEY;
  return (
    <Card className="approval-template-card" variant="borderless" hoverable onClick={onOpen}>
      <div className="approval-template-card__head">
        <span className={`approval-template-card__icon is-${meta.category}`}>
          <OaIcon name={meta.icon} />
        </span>
        <Typography.Title level={5}>{form.formName}</Typography.Title>
      </div>
      <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }}>
        {form.description || t('approval.start.noDescription')}
      </Typography.Paragraph>
      <div className="approval-template-card__foot">
        {process
          ? <Tag color="success" bordered={false}>{t('approval.start.boundProcess')}</Tag>
          : <Tag bordered={false}>{t('approval.start.unboundProcess')}</Tag>}
        <Space size={4}>
          <Button type="link" size="small" icon={<OaIcon name="send" />}>
            {isLeave ? t('approval.start.openForm') : t('approval.start.viewTemplate')}
          </Button>
        </Space>
      </div>
    </Card>
  );
}
