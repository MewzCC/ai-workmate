import { Alert, Avatar, Button, Card, Divider, Empty, Form, Result, Space, Spin, Steps, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { OaIcon } from '@/components/OaIcon';
import { useGenericApplicationForm } from '@/hooks/useGenericApplicationForm';
import { isApplicationReferenceField, renderApplicationField, supportedApplicationField } from './approvalFormFields';
import { ApprovalFormReferenceSelect } from './ApprovalFormReferenceSelect';

const APPROVE_TYPE_LABEL_KEYS: Record<string, string> = {
  DIRECT_MANAGER: 'directManager', DEPARTMENT: 'department', USER: 'user',
  SELF: 'self', MULTI_LEVEL: 'multiLevel', ROLE: 'role',
};

export default function ApprovalFormPage() {
  const { t } = useTranslation(), { user } = useAuth();
  const { form, formKey, formDef, process, fields, nodes, draft, loading, loadError, load,
    allowed, editable, busy, confirming, canSubmit, canCancel, changed, saveDraft, cancelDraft, submit, backToStart } = useGenericApplicationForm();

  if (!allowed) return <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />;

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
              <Form form={form} layout="vertical" className="leave-enterprise-form" requiredMark="optional" disabled={!editable || busy || confirming} onValuesChange={changed}>
                {fields.map((field) => {
                  if (field.type === 'divider') return null;
                  const label = field.label || field.name || '';
                  return (
                    <Form.Item
                      key={field.name}
                      name={field.name}
                      label={label}
                      rules={[
                        ...(field.required ? [{ required: true, message: t('approval.start.fieldRequired', { field: label }) }] : []),
                        ...(['number', 'amount', 'money'].includes(field.type) ? [{ validator: (_: unknown, value: unknown) => {
                          if (value === undefined || value === null || value === '') return Promise.resolve();
                          const number = Number(value);
                          return /^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(String(value)) && Number.isFinite(number) && (field.min == null || number >= field.min)
                            ? Promise.resolve() : Promise.reject(new Error(t('approval.formPage.invalidNumeric')));
                        } }] : []),
                      ]}
                      extra={!supportedApplicationField(field)
                        ? t('approval.formPage.unsupportedControl', { field: label })
                        : undefined}
                    >
                      {isApplicationReferenceField(field) ? <ApprovalFormReferenceSelect
                        formKey={formKey} fieldName={field.name} placeholder={field.placeholder || undefined}
                        active={editable && !loading} disabled={busy || confirming}
                        scopeKey={JSON.stringify([user?.tenantId, user?.id, user?.permissionVersion, formKey, draft?.id, field.name])}
                      /> : renderApplicationField(field)}
                    </Form.Item>
                  );
                })}

                <div className="leave-form-footer">
                  <Typography.Text type="secondary">
                    {draft ? t('approval.formPage.footerDraft') : t('approval.formPage.footerConfirm')}
                  </Typography.Text>
                  <Space wrap>
                    {draft?.canCancel && canCancel && <Button size="large" danger disabled={busy || confirming} onClick={cancelDraft}>{t('approval.formPage.cancelDraft')}</Button>}
                    <Button
                      size="large"
                      loading={busy}
                      disabled={!editable || busy || confirming || fields.length === 0}
                      onClick={() => void saveDraft()}
                    >
                      {t('approval.formPage.saveDraft')}
                    </Button>
                    <Button
                      size="large"
                      type="primary"
                      icon={<OaIcon name="send" />}
                      loading={busy}
                      disabled={!canSubmit || busy || confirming || !process || fields.length === 0}
                      onClick={() => void submit()}
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
                {t('approval.formPage.previewDescription')}
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
