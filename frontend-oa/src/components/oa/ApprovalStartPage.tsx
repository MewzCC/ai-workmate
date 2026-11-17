import { Alert, Button, Card, Empty, Input, Space, Spin, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ApprovalApplicationTemplate } from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { useApprovalStartPage } from '@/hooks/useApprovalStartPage';
import { OaIcon, type OaIconName } from '@/components/OaIcon';
import { ApplicationFieldsModal } from './ApplicationFieldsModal';

type TemplateCategory = 'hr' | 'finance' | 'admin' | 'purchase' | 'other';
const TEMPLATE_META: Record<string, { category: TemplateCategory; icon: OaIconName; common?: boolean }> = {
  'business-trip': { category: 'admin', icon: 'send', common: true },
  expense: { category: 'finance', icon: 'expense', common: true },
  overtime: { category: 'hr', icon: 'attendance', common: true },
  purchase: { category: 'purchase', icon: 'suppliers', common: true },
  payment: { category: 'finance', icon: 'finance', common: true },
};
const CATEGORIES: TemplateCategory[] = ['hr', 'finance', 'admin', 'purchase', 'other'];
const CATEGORY_ICON: Record<TemplateCategory, OaIconName> = { hr: 'hr', finance: 'finance', admin: 'assets', purchase: 'suppliers', other: 'process' };

export default function ApprovalStartPage() {
  const { t } = useTranslation();
  const page = useApprovalStartPage();
  if (!page.allowed) return <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />;
  const common = page.visibleForms.filter(form => TEMPLATE_META[form.formKey]?.common);
  const cards = (forms: ApprovalApplicationTemplate[]) => forms.map(form => <TemplateCard key={form.formId} form={form}
    enabled={page.canEdit && !page.action.busy} onOpen={() => page.openTemplate(form)} />);
  const summaryHint = t(page.canViewAll ? 'approval.start.previewWindow' : 'approval.start.previewWindowRestricted');
  return <section className="leave-list-workbench approval-start-page">
    <header className="leave-list-hero">
      <div><span className="leave-list-hero__kicker">{t('approval.start.kicker')}</span>
        <Typography.Title level={2}>{t('approval.start.title')}</Typography.Title>
        <Typography.Paragraph>{t('approval.start.description')}</Typography.Paragraph></div>
      <Space wrap>
        {page.formKey && <Tag closable onClose={() => page.changeSearch('')}>{page.formKey}</Tag>}
        <Input.Search className="approval-start-search" maxLength={120} allowClear value={page.keyword}
          disabled={!page.canTemplates} placeholder={t('approval.start.searchPlaceholder')} onChange={event => page.changeSearch(event.target.value)} />
        <Button disabled={!page.canTemplates && !page.canMine || page.action.busy} onClick={() => { void page.refresh().catch(() => {}); }}
          icon={<OaIcon name="reload" />}>{t('common.refresh')}</Button>
      </Space>
    </header>
    {page.templates.error !== undefined && <Alert showIcon type="error" title={t('approval.start.loadFailedTitle')}
      description={t('approval.start.loadFailedDesc', { error: formatOaApiError(page.templates.error) })}
      action={<Button onClick={() => void page.templates.load()}>{t('common.retry')}</Button>} />}
    {!page.canTemplates && <Alert type="info" showIcon title={t('approval.start.templatePermissionHint')} />}
    {(page.canLeave || common.length > 0) && <section className="approval-start-section">
      <div className="approval-start-section__head"><OaIcon name="todo" /><Typography.Title level={4}>{t('approval.start.commonTitle')}</Typography.Title></div>
      <div className="approval-start-grid">
        {page.canLeave && <Card className="approval-template-card" variant="borderless">
          <div className="approval-template-card__head"><span className="approval-template-card__icon is-hr"><OaIcon name="form" /></span>
            <Typography.Title level={5}>{t('approval.form.titleCreate')}</Typography.Title></div>
          <Typography.Paragraph type="secondary">{t('approval.form.heroDescription')}</Typography.Paragraph>
          <Button type="link" disabled={page.action.busy} onClick={page.openLeave}>{t('approval.start.openForm')}</Button>
        </Card>}
        {cards(common)}
      </div>
    </section>}
    <Spin spinning={page.canMine && page.own.loading}>
      {page.canMine && <section className="approval-start-section">
        <div className="approval-start-section__head"><OaIcon name="edit" /><Typography.Title level={4}>{t('approval.start.myDrafts')}</Typography.Title>
          {page.canViewAll && <Button type="link" onClick={page.viewAll}>{t('approval.start.viewAll')}</Button>}</div>
        <Typography.Paragraph type="secondary">{summaryHint}</Typography.Paragraph>
        {page.own.error !== undefined && <Alert type="error" showIcon title={formatOaApiError(page.own.error)}
          action={<Button onClick={() => void page.own.load()}>{t('common.retry')}</Button>} />}
        <div className="approval-start-grid">{page.drafts.map(draft => <Card key={draft.id} className="approval-template-card" variant="borderless">
          <div className="approval-template-card__head"><span className="approval-template-card__icon is-other"><OaIcon name="edit" /></span>
            <Typography.Title level={5}>{draft.formName}</Typography.Title></div>
          <Typography.Paragraph type="secondary">{t('approval.start.draftUpdatedAt', { time: new Date(draft.updatedAt).toLocaleString() })}</Typography.Paragraph>
          <div className="approval-template-card__foot"><Tag>{t('approval.start.draftStatus')}</Tag><Space>
            <Button type="link" disabled={page.action.busy} onClick={() => page.showDetail(draft)}>{t('approval.start.viewHistoricalDetail')}</Button>
            {page.canEdit && draft.canEditDraft && <Button type="link" disabled={page.action.busy} onClick={() => page.editDraft(draft)}>{t('common.edit')}</Button>}
          </Space></div>
        </Card>)}</div>
        {!page.own.loading && page.own.error === undefined && page.drafts.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('common.empty')} />}
      </section>}
      {page.canMine && page.recent.length > 0 && <section className="approval-start-section">
        <div className="approval-start-section__head"><OaIcon name="history" /><Typography.Title level={4}>{t('approval.start.recentApplications')}</Typography.Title></div>
        <div className="approval-start-grid">{page.recent.map(item => <Card key={item.id} className="approval-template-card" variant="borderless">
          <div className="approval-template-card__head"><span className="approval-template-card__icon is-other"><OaIcon name="approval" /></span>
            <Typography.Title level={5}>{item.formName}</Typography.Title></div>
          <Typography.Paragraph type="secondary">{t('approval.start.applicationUpdatedAt', { time: new Date(item.updatedAt).toLocaleString() })}</Typography.Paragraph>
          <div className="approval-template-card__foot"><Tag>{t(`approval.status.${item.status}`)}</Tag><Space wrap size={4}>
            <Button type="link" size="small" disabled={page.action.busy} onClick={() => page.showDetail(item)}>{t('approval.start.viewHistoricalDetail')}</Button>
            {(['withdraw', 'remind', 'reopen'] as const).filter(action => page.action.can(item, action)).map(action => <Button type="link" size="small" key={action}
              danger={action === 'withdraw'} disabled={page.action.busy} onClick={() => page.action.perform(item, action)}>
              {t(action === 'reopen' ? 'approval.start.editAndResubmit' : `approval.start.${action}`, { count: item.reminderCount })}
            </Button>)}
          </Space></div>
        </Card>)}</div>
      </section>}
    </Spin>
    <Spin spinning={page.canTemplates && page.templates.loading}>
      {page.canTemplates && !page.templates.loading && page.templates.error === undefined && page.forms.length === 0
        && <Card className="leave-list-card"><Empty description={t('approval.start.emptyTemplates')} /></Card>}
      {CATEGORIES.map(category => {
        const forms = page.visibleForms.filter(form => (TEMPLATE_META[form.formKey]?.category || 'other') === category);
        return forms.length > 0 && <section key={category} className="approval-start-section">
          <div className="approval-start-section__head"><OaIcon name={CATEGORY_ICON[category]} /><Typography.Title level={4}>{t(`approval.start.category.${category}`)}</Typography.Title></div>
          <div className="approval-start-grid">{cards(forms)}</div>
        </section>;
      })}
      {page.forms.length > 0 && page.visibleForms.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('common.empty')} />}
    </Spin>
    <ApplicationFieldsModal id={page.selected?.id} allowed={page.canMine} scope={page.own.scope} revision={page.detailRevision}
      processVersion={page.selected?.processVersionSnapshot} onClose={() => page.setSelection(undefined)} />
  </section>;
}

function TemplateCard({ form, enabled, onOpen }: { form: ApprovalApplicationTemplate; enabled: boolean; onOpen: () => void }) {
  const { t } = useTranslation(), meta = TEMPLATE_META[form.formKey] || { category: 'other', icon: 'form' as OaIconName };
  return <Card className="approval-template-card" variant="borderless" hoverable={enabled} onClick={enabled ? onOpen : undefined}>
    <div className="approval-template-card__head"><span className={`approval-template-card__icon is-${meta.category}`}><OaIcon name={meta.icon} /></span>
      <Typography.Title level={5}>{form.formName}</Typography.Title></div>
    <Typography.Paragraph type="secondary" ellipsis={{ rows: 2 }}>{form.description || t('approval.start.noDescription')}</Typography.Paragraph>
    <div className="approval-template-card__foot">
      <Tag color={form.process ? 'success' : undefined}>{t(form.process ? 'approval.start.boundProcess' : 'approval.start.unboundProcess')}</Tag>
      <Button type="link" size="small" disabled={!enabled} title={!enabled ? t('oa.ai.noPermission') : undefined}
        icon={<OaIcon name="send" />} onClick={event => { event.stopPropagation(); onOpen(); }}>{t('approval.start.viewTemplate')}</Button>
    </div>
  </Card>;
}
