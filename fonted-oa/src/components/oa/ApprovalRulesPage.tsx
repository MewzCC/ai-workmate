'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  approvalEngineApi,
  type ApprovalConfigStatus,
  type ApprovalRule,
  type ApprovalRulePayload,
  type ApprovalRuleType,
} from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import ApprovalConfigShell from './ApprovalConfigShell';
import { StatusTag } from './FormEnginePage';

const RULE_TYPES: ApprovalRuleType[] = [
  'AMOUNT_THRESHOLD', 'LEAVE_TYPE', 'EMPLOYEE_LEVEL', 'LIMIT_OVERRIDE',
];

const RULE_TYPE_TAG_COLOR: Record<ApprovalRuleType, string> = {
  AMOUNT_THRESHOLD: 'gold',
  LEAVE_TYPE: 'blue',
  EMPLOYEE_LEVEL: 'purple',
  LIMIT_OVERRIDE: 'cyan',
};

interface RuleFormValues extends Omit<ApprovalRulePayload, 'priority' | 'conditionJson' | 'actionJson'> {
  priority?: number;
}

interface ConditionRow {
  id: string;
  field: string;
  op: string;
  value: string;
}

interface RuleActionState {
  appendNode: string;
  enabled: boolean;
  mode: string;
}

const CONDITION_FIELDS = ['amount', 'durationDays', 'department', 'employeeLevel', 'leaveType'];
const CONDITION_OPS = ['eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'in'];
const ACTION_NODES = ['DEPARTMENT_HEAD', 'FINANCE_REVIEW', 'DIRECT_MANAGER'];

function newConditionId(): string {
  return `c-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
}

/** 兼容历史平铺格式 {field,op,value} 与分组格式 {logic,conditions:[...]}。 */
function parseConditions(json: string): { logic: 'AND' | 'OR'; rows: ConditionRow[] } {
  try {
    const parsed = JSON.parse(json) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return { logic: 'AND', rows: [] };
    }
    const obj = parsed as { logic?: string; conditions?: Array<{ field?: string; op?: string; value?: unknown }> };
    const rawRows = Array.isArray(obj.conditions) ? obj.conditions : [obj as { field?: string; op?: string; value?: unknown }];
    const rows = rawRows
      .filter((row): row is { field?: string; op?: string; value?: unknown } =>
        Boolean(row && typeof row === 'object'))
      .map((row) => ({
        id: newConditionId(),
        field: typeof row.field === 'string' ? row.field : 'amount',
        op: typeof row.op === 'string' ? row.op : 'gte',
        value: row.value == null ? '' : String(row.value),
      }));
    return {
      logic: obj.logic === 'OR' ? 'OR' : 'AND',
      rows,
    };
  } catch {
    return { logic: 'AND', rows: [] };
  }
}

function serializeConditions(logic: 'AND' | 'OR', rows: ConditionRow[]): string {
  const items = rows.map((row) => ({ field: row.field, op: row.op, value: row.value }));
  if (items.length === 0) return '{"field":"amount","op":"gte","value":0}';
  return items.length === 1
    ? JSON.stringify(items[0])
    : JSON.stringify({ logic, conditions: items });
}

function parseAction(json: string): RuleActionState {
  try {
    const parsed = JSON.parse(json) as { appendNode?: string; enabled?: boolean; mode?: string };
    return {
      appendNode: typeof parsed.appendNode === 'string' ? parsed.appendNode : 'DEPARTMENT_HEAD',
      enabled: parsed.enabled !== false,
      mode: typeof parsed.mode === 'string' ? parsed.mode : 'OR_SIGN',
    };
  } catch {
    return { appendNode: 'DEPARTMENT_HEAD', enabled: true, mode: 'OR_SIGN' };
  }
}

function serializeAction(action: RuleActionState): string {
  return JSON.stringify({
    appendNode: action.appendNode,
    enabled: action.enabled,
    mode: action.mode,
  });
}

export default function ApprovalRulesPage() {
  const { t } = useTranslation();
  const [data, setData] = useState<ApprovalRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<ApprovalConfigStatus | undefined>(undefined);

  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<ApprovalRule | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<RuleFormValues>();
  const [condLogic, setCondLogic] = useState<'AND' | 'OR'>('AND');
  const [conditionRows, setConditionRows] = useState<ConditionRow[]>([]);
  const [action, setAction] = useState<RuleActionState>({ appendNode: 'DEPARTMENT_HEAD', enabled: true, mode: 'OR_SIGN' });

  const load = useCallback(async (p = page) => {
    setLoading(true);
    try {
      const res = await approvalEngineApi.listRules({
        keyword: keyword || undefined,
        status,
        page: p,
        size: 20,
      });
      setData(res.records);
      setTotal(res.total);
    } catch (err) {
      message.error(formatOaApiError(err));
    } finally {
      setLoading(false);
    }
  }, [keyword, status, page]);

  useEffect(() => {
    void load(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setCondLogic('AND');
    setConditionRows([{ id: newConditionId(), field: 'amount', op: 'gte', value: '' }]);
    setAction({ appendNode: 'DEPARTMENT_HEAD', enabled: true, mode: 'OR_SIGN' });
    setEditOpen(true);
  };

  const openEdit = (record: ApprovalRule) => {
    setEditing(record);
    form.setFieldsValue({
      ruleKey: record.ruleKey,
      ruleName: record.ruleName,
      ruleType: record.ruleType,
      priority: record.priority,
      description: record.description || '',
      status: record.status,
    });
    const parsed = parseConditions(record.conditionJson);
    setCondLogic(parsed.logic);
    setConditionRows(parsed.rows.length > 0 ? parsed.rows : [{ id: newConditionId(), field: 'amount', op: 'gte', value: '' }]);
    setAction(parseAction(record.actionJson));
    setEditOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const payload: ApprovalRulePayload = {
        ...values,
        priority: values.priority ?? 100,
        conditionJson: serializeConditions(condLogic, conditionRows),
        actionJson: serializeAction(action),
      };
      if (editing) {
        await approvalEngineApi.updateRule(editing.id, { ...payload, version: editing.version });
        message.success(t('approval.config.common.updateSuccess'));
      } else {
        await approvalEngineApi.createRule(payload);
        message.success(t('approval.config.common.createSuccess'));
      }
      setEditOpen(false);
      await load(page);
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await approvalEngineApi.deleteRule(id);
      message.success(t('approval.config.common.deleteSuccess'));
      await load(page);
    } catch (err) {
      message.error(formatOaApiError(err));
    }
  };

  const patchCondition = (id: string, patch: Partial<ConditionRow>) => {
    setConditionRows((rows) => rows.map((row) => (row.id === id ? { ...row, ...patch } : row)));
  };

  const addCondition = () => {
    setConditionRows((rows) => [
      ...rows,
      { id: newConditionId(), field: 'department', op: 'eq', value: '' },
    ]);
  };

  const removeCondition = (id: string) => {
    setConditionRows((rows) => rows.filter((row) => row.id !== id));
  };

  const isNumericCondition = (field: string) =>
    ['amount', 'durationDays', 'employeeLevel', 'priority'].includes(field);

  const columns: ColumnsType<ApprovalRule> = [
    {
      title: t('approval.config.rule.columnKey'),
      dataIndex: 'ruleKey',
      key: 'ruleKey',
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    {
      title: t('approval.config.rule.columnName'),
      dataIndex: 'ruleName',
      key: 'ruleName',
      render: (v: string) => <Typography.Text strong>{v}</Typography.Text>,
    },
    {
      title: t('approval.config.rule.columnType'),
      dataIndex: 'ruleType',
      key: 'ruleType',
      width: 140,
      render: (v: ApprovalRuleType) => (
        <Tag color={RULE_TYPE_TAG_COLOR[v]}>
          {t(`approval.config.rule.ruleTypeOption.${v}`, { defaultValue: v })}
        </Tag>
      ),
    },
    {
      title: t('approval.config.rule.columnPriority'),
      dataIndex: 'priority',
      key: 'priority',
      width: 90,
    },
    {
      title: t('approval.config.common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (v: ApprovalConfigStatus) => <StatusTag status={v} />,
    },
    {
      title: t('approval.config.common.version'),
      dataIndex: 'version',
      key: 'version',
      width: 80,
    },
    {
      title: t('approval.config.common.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: 130,
      render: (_, record) =>
        record.canEdit ? (
          <Space>
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              {t('approval.config.common.edit')}
            </Button>
            <Popconfirm
              title={t('approval.config.common.deleteConfirm')}
              onConfirm={() => handleDelete(record.id)}
            >
              <Button type="link" size="small" danger disabled={!record.canDelete}>
                {t('approval.config.common.delete')}
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <ApprovalConfigShell
      eyebrow="APPROVAL RULES"
      title={t('approval.config.rule.title')}
      description={t('approval.config.rule.description')}
      actions={
        <Button type="primary" onClick={openCreate}>
          {t('approval.config.rule.create')}
        </Button>
      }
    >
      <Card className="leave-list-card" variant="borderless">
        <div className="leave-list-toolbar">
          <Input.Search
            placeholder={t('approval.config.common.searchPlaceholder')}
            allowClear
            style={{ width: 240 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={() => { setPage(1); void load(1); }}
          />
          <Space wrap>
            <Select
              allowClear
              placeholder={t('approval.config.common.status')}
              style={{ width: 130 }}
              value={status}
              onChange={(v) => { setStatus(v); setPage(1); }}
              options={(['ENABLED', 'DISABLED'] as const).map((s) => ({
                value: s,
                label: t(`approval.config.common.statusOption.${s}`, { defaultValue: s }),
              }))}
            />
            <Button icon={<OaIcon name="reload" />} onClick={() => void load(page)}>
              {t('common.refresh')}
            </Button>
          </Space>
        </div>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={data}
          loading={loading}
          size="middle"
          locale={{ emptyText: <Empty description={t('approval.config.common.noData')} /> }}
          scroll={{ x: 980 }}
          pagination={{
            current: page,
            pageSize: 20,
            total,
            showSizeChanger: false,
            onChange: (p) => { setPage(p); void load(p); },
          }}
        />
      </Card>

      <Modal
        title={editing ? t('approval.config.rule.editTitle') : t('approval.config.rule.createTitle')}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={handleSubmit}
        confirmLoading={submitting}
        okText={t('approval.config.common.save')}
        destroyOnClose
        width={640}
      >
        <Form form={form} layout="vertical" initialValues={{ status: 'ENABLED', priority: 100 }}>
          <Form.Item
            name="ruleKey"
            label={t('approval.config.rule.ruleKey')}
            rules={[
              { required: true, message: t('approval.config.common.fieldRequired') },
              {
                pattern: /^[a-z][a-z0-9_-]*$/,
                message: t('approval.config.common.keyInvalid'),
              },
            ]}
          >
            <Input maxLength={64} disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item
            name="ruleName"
            label={t('approval.config.rule.ruleName')}
            rules={[{ required: true, message: t('approval.config.common.fieldRequired') }]}
          >
            <Input maxLength={120} />
          </Form.Item>
          <Form.Item
            name="ruleType"
            label={t('approval.config.rule.ruleType')}
            rules={[{ required: true, message: t('approval.config.common.fieldRequired') }]}
          >
            <Select
              options={RULE_TYPES.map((type) => ({
                value: type,
                label: t(`approval.config.rule.ruleTypeOption.${type}`, { defaultValue: type }),
              }))}
            />
          </Form.Item>
          <Form.Item
            name="priority"
            label={t('approval.config.rule.priority')}
            extra={t('approval.config.rule.priorityHint')}
          >
            <InputNumber style={{ width: '100%' }} min={0} precision={0} />
          </Form.Item>
          <Form.Item label={t('approval.config.rule.conditionTitle')} required>
            <div className="rule-condition-builder">
              <Segmented
                block
                value={condLogic}
                options={[
                  { value: 'AND', label: t('approval.builder.logicAnd') },
                  { value: 'OR', label: t('approval.builder.logicOr') },
                ]}
                onChange={(value) => setCondLogic(value as 'AND' | 'OR')}
              />
              <div className="rule-condition-builder__rows">
                {conditionRows.map((row) => (
                  <div key={row.id} className="rule-condition-row">
                    <Select
                      style={{ width: 160 }}
                      value={row.field}
                      onChange={(value) => patchCondition(row.id, { field: value })}
                      options={CONDITION_FIELDS.map((field) => ({
                        value: field,
                        label: t(`approval.builder.field.${field}`, { defaultValue: field }),
                      }))}
                    />
                    <Select
                      style={{ width: 105 }}
                      value={row.op}
                      onChange={(value) => patchCondition(row.id, { op: value })}
                      options={CONDITION_OPS.map((op) => ({
                        value: op,
                        label: t(`approval.builder.op.${op}`, { defaultValue: op }),
                      }))}
                    />
                    {isNumericCondition(row.field) ? (
                      <InputNumber
                        style={{ width: 140 }}
                        value={row.value === '' ? undefined : Number(row.value)}
                        onChange={(value) => patchCondition(row.id, { value: value == null ? '' : String(value) })}
                        placeholder={t('approval.config.rule.conditionValue')}
                      />
                    ) : (
                      <Input
                        style={{ width: 160 }}
                        value={row.value}
                        onChange={(e) => patchCondition(row.id, { value: e.target.value })}
                        placeholder={t('approval.config.rule.conditionValue')}
                      />
                    )}
                    <Button
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => removeCondition(row.id)}
                      aria-label={t('approval.config.common.delete')}
                    />
                  </div>
                ))}
              </div>
              <Button type="dashed" block icon={<PlusOutlined />} onClick={addCondition}>
                {t('approval.builder.addCondition')}
              </Button>
            </div>
          </Form.Item>
          <Form.Item label={t('approval.config.rule.actionTitle')} required>
            <Space wrap>
              <Select
                style={{ width: 200 }}
                value={action.appendNode}
                onChange={(value) => setAction((current) => ({ ...current, appendNode: value }))}
                options={ACTION_NODES.map((node) => ({
                  value: node,
                  label: t(`approval.builder.actionNode.${node}`, { defaultValue: node }),
                }))}
              />
              <Select
                style={{ width: 130 }}
                value={action.mode}
                onChange={(value) => setAction((current) => ({ ...current, mode: value }))}
                options={(['COUNTERSIGN', 'OR_SIGN', 'SEQUENTIAL'] as const).map((mode) => ({
                  value: mode,
                  label: t(`approval.designer.mode.${mode}`, { defaultValue: mode }),
                }))}
              />
              <Space size={6}>
                <Switch checked={action.enabled} onChange={(checked) => setAction((current) => ({ ...current, enabled: checked }))} />
                <Typography.Text type="secondary">{t('approval.config.rule.actionEnabled')}</Typography.Text>
              </Space>
            </Space>
          </Form.Item>
          <Form.Item name="description" label={t('approval.config.rule.descriptionLabel')}>
            <Input.TextArea rows={2} maxLength={500} showCount />
          </Form.Item>
          <Form.Item name="status" label={t('approval.config.common.status')}>
            <Select
              options={(['ENABLED', 'DISABLED'] as const).map((s) => ({
                value: s,
                label: t(`approval.config.common.statusOption.${s}`, { defaultValue: s }),
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </ApprovalConfigShell>
  );
}