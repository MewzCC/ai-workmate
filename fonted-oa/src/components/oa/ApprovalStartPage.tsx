'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from '@/lib/nextCompat';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Space,
  Tag,
  Typography,
} from 'antd';
import { useTranslation } from 'react-i18next';
import { approvalEngineApi, type ApprovalForm, type ApprovalProcess } from '@/lib/approvalEngineApi';
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

interface SchemaField {
  name?: string;
  label?: string;
  type?: string;
}

function parseSchemaFields(schemaJson: string): SchemaField[] {
  try {
    const parsed = JSON.parse(schemaJson) as { fields?: SchemaField[] };
    return Array.isArray(parsed.fields) ? parsed.fields.filter((f) => f && f.name) : [];
  } catch {
    return [];
  }
}

/** 打车内置请假模板入口：请假表单路由对所有登录角色开放。 */
const LEAVE_FORM_KEY = 'leave-application';

export default function ApprovalStartPage() {
  const router = useRouter();
  const { t } = useTranslation();
  const [forms, setForms] = useState<ApprovalForm[]>([]);
  const [processes, setProcesses] = useState<ApprovalProcess[]>([]);
  const [loadError, setLoadError] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [detail, setDetail] = useState<{ form: ApprovalForm; process?: ApprovalProcess }>();

  const load = useCallback(async () => {
    setLoadError(undefined);
    try {
      const [formPage, processPage] = await Promise.all([
        approvalEngineApi.listForms({ status: 'ENABLED', page: 1, size: 100 }),
        approvalEngineApi.listProcesses({ status: 'ENABLED', page: 1, size: 100 }),
      ]);
      setForms(formPage.records);
      setProcesses(processPage.records);
    } catch (err) {
      setForms([]);
      setProcesses([]);
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

  const openTemplate = (form: ApprovalForm) => {
    if (form.formKey === LEAVE_FORM_KEY) {
      router.push('/oa/leave-application');
      return;
    }
    setDetail({ form, process: processByFormId.get(form.id) });
  };

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

      <Drawer
        className="approval-template-drawer"
        title={detail?.form.formName}
        width={520}
        open={Boolean(detail)}
        onClose={() => setDetail(undefined)}
      >
        {detail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Alert
              showIcon
              type="info"
              message={t('approval.start.notWiredTitle')}
              description={t('approval.start.notWiredDesc')}
            />
            <Card className="oa-domain-card" variant="borderless" title={t('approval.start.templateInfo')}>
              <Descriptions column={1} size="small">
                <Descriptions.Item label={t('approval.config.form.formKey')}>
                  <Typography.Text code>{detail.form.formKey}</Typography.Text>
                </Descriptions.Item>
                <Descriptions.Item label={t('approval.start.fieldCount')}>
                  {t('approval.start.fieldCountValue', { count: parseSchemaFields(detail.form.schemaJson).length })}
                </Descriptions.Item>
                <Descriptions.Item label={t('approval.start.boundProcess')}>
                  {detail.process
                    ? (
                      <Space size={6}>
                        <Tag color="success" bordered={false}>{detail.process.processName}</Tag>
                        <Typography.Text type="secondary">
                          {t('approval.start.processNodes', { count: parseNodeCount(detail.process.nodeJson) })}
                        </Typography.Text>
                      </Space>
                    )
                    : <Tag bordered={false}>{t('approval.start.unboundProcess')}</Tag>}
                </Descriptions.Item>
              </Descriptions>
            </Card>
            <Card className="oa-domain-card" variant="borderless" title={t('approval.start.formFields')}>
              {parseSchemaFields(detail.form.schemaJson).length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('approval.start.emptyFields')} />
              ) : (
                <div className="approval-template-fields">
                  {parseSchemaFields(detail.form.schemaJson).map((field) => (
                    <div key={field.name}>
                      <Typography.Text>{field.label || field.name}</Typography.Text>
                      <Tag bordered={false}>{t(`approval.start.fieldType.${field.type}`, { defaultValue: field.type })}</Tag>
                    </div>
                  ))}
                </div>
              )}
            </Card>
            <Button block type="primary" disabled icon={<OaIcon name="send" />}>
              {t('approval.start.submitDisabled')}
            </Button>
          </Space>
        )}
      </Drawer>
    </section>
  );
}

function parseNodeCount(nodeJson: string): number {
  try {
    const parsed = JSON.parse(nodeJson);
    return Array.isArray(parsed) ? parsed.length : 0;
  } catch {
    return 0;
  }
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