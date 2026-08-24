'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, DeleteOutlined } from '@ant-design/icons';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import {
  approvalEngineApi,
  type ApprovalConfigStatus,
  type ApprovalForm,
  type ApprovalProcess,
} from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { OaIcon, type OaIconName } from '@/components/OaIcon';

// ==================== 设计器节点模型 ====================

export type DesignerNodeType = 'START' | 'APPROVAL' | 'CONDITION' | 'CC' | 'DELAY' | 'END';

export type DesignerApproveType =
  | 'DIRECT_MANAGER' | 'ROLE' | 'DEPARTMENT' | 'USER' | 'SELF' | 'MULTI_LEVEL';

export type DesignerMode = 'COUNTERSIGN' | 'OR_SIGN' | 'SEQUENTIAL';

export interface DesignerNode {
  /** 仅设计器内部使用，保存时剔除，后端 node_json 不落盘 */
  id: string;
  nodeType: DesignerNodeType;
  nodeName: string;
  approveType?: DesignerApproveType;
  targetKey?: string;
  mode?: DesignerMode;
  timeoutEnabled?: boolean;
  timeoutHours?: number;
  timeoutAction?: 'REMIND' | 'TRANSFER' | 'AUTO_APPROVE';
}

interface ProcessFormValues {
  processKey: string;
  processName: string;
  description?: string;
  formId?: number | null;
  status: ApprovalConfigStatus;
}

const NODE_TYPE_ICON: Record<DesignerNodeType, OaIconName> = {
  START: 'process',
  APPROVAL: 'approval',
  CONDITION: 'rules',
  CC: 'notification',
  DELAY: 'pause',
  END: 'history',
};

const DEFAULT_APPROVE_OPTIONS: { value: DesignerApproveType; key: string }[] = [
  { value: 'DIRECT_MANAGER', key: 'directManager' },
  { value: 'DEPARTMENT', key: 'department' },
  { value: 'USER', key: 'user' },
  { value: 'SELF', key: 'self' },
  { value: 'MULTI_LEVEL', key: 'multiLevel' },
  { value: 'ROLE', key: 'role' },
];

const MODE_OPTIONS: { value: DesignerMode; key: string }[] = [
  { value: 'COUNTERSIGN', key: 'countersign' },
  { value: 'OR_SIGN', key: 'orSign' },
  { value: 'SEQUENTIAL', key: 'sequential' },
];

const TIMEOUT_ACTIONS: { value: 'REMIND' | 'TRANSFER' | 'AUTO_APPROVE'; key: string }[] = [
  { value: 'REMIND', key: 'remind' },
  { value: 'TRANSFER', key: 'transfer' },
  { value: 'AUTO_APPROVE', key: 'autoApprove' },
];

function createNode(nodeType: DesignerNodeType, index: number): DesignerNode {
  const base: DesignerNode = {
    id: `${nodeType}-${Date.now()}-${index}`,
    nodeType,
    nodeName: '',
  };
  if (nodeType === 'START') return { ...base, nodeName: '开始' };
  if (nodeType === 'END') return { ...base, nodeName: '结束' };
  if (nodeType === 'APPROVAL') {
    return {
      ...base,
      nodeName: '审批节点',
      approveType: 'DIRECT_MANAGER',
      targetKey: '',
      mode: 'OR_SIGN',
      timeoutEnabled: false,
      timeoutHours: 48,
      timeoutAction: 'REMIND',
    };
  }
  if (nodeType === 'CONDITION') return { ...base, nodeName: '条件分支' };
  if (nodeType === 'CC') return { ...base, nodeName: '抄送节点' };
  return { ...base, nodeName: '延迟节点', timeoutHours: 24 };
}

function defaultNodes(): DesignerNode[] {
  return [createNode('START', 0), createNode('APPROVAL', 1), createNode('END', 2)];
}

function stripNodeId(node: DesignerNode): Omit<DesignerNode, 'id'> {
  const { id: _id, ...rest } = node;
  void _id;
  return rest;
}

function newId(): string {
  return `n-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

const APPROVAL_OPT_KEYS: Record<DesignerApproveType, string> = {
  DIRECT_MANAGER: 'directManager',
  DEPARTMENT: 'department',
  USER: 'user',
  SELF: 'self',
  MULTI_LEVEL: 'multiLevel',
  ROLE: 'role',
};

/**
 * 节点式流程设计器：
 * 左侧工具条 + 中间自绘竖向画布（选中、排序、删除）+ 右侧节点属性面板。
 * 节点 JSON 与后端 `approval_process.node_json` 数组格式兼容。
 */
export default function ProcessDesignerModal({
  open,
  editing,
  forms,
  onClose,
  onSaved,
}: {
  open: boolean;
  editing: ApprovalProcess | null;
  forms: ApprovalForm[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [metaForm] = Form.useForm<ProcessFormValues>();
  const [nodes, setNodes] = useState<DesignerNode[]>(defaultNodes());
  const [selectedId, setSelectedId] = useState<string>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      metaForm.setFieldsValue({
        processKey: editing.processKey,
        processName: editing.processName,
        description: editing.description || '',
        formId: editing.formId,
        status: editing.status,
      });
      const parsed = ensureEndpoints(parseExistingNodes(editing.nodeJson));
      setNodes(parsed.length > 0 ? parsed : defaultNodes());
    } else {
      metaForm.resetFields();
      setNodes(defaultNodes());
    }
    setSelectedId(undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing]);

  const selected = useMemo(
    () => nodes.find((node) => node.id === selectedId),
    [nodes, selectedId],
  );

  const approvalCount = useMemo(
    () => nodes.filter((node) => node.nodeType === 'APPROVAL').length,
    [nodes],
  );

  const insertBeforeEnd = (nodeType: DesignerNodeType) => {
    setNodes((current) => {
      const endIndex = current.findIndex((node) => node.nodeType === 'END');
      const index = endIndex >= 0 ? endIndex : current.length;
      const node = createNode(nodeType, index);
      const next = [...current];
      next.splice(index, 0, node);
      setSelectedId(node.id);
      return next;
    });
  };

  const removeNode = (id: string) => {
    setNodes((current) => {
      const node = current.find((item) => item.id === id);
      if (!node || node.nodeType === 'START' || node.nodeType === 'END') return current;
      return current.filter((item) => item.id !== id);
    });
    if (selectedId === id) setSelectedId(undefined);
  };

  const moveNode = (id: string, direction: -1 | 1) => {
    setNodes((current) => {
      const index = current.findIndex((item) => item.id === id);
      const target = index + direction;
      const node = current[index];
      if (!node || target < 0 || target >= current.length) return current;
      const guard = current[target];
      if (guard?.nodeType === 'START' || guard?.nodeType === 'END') return current;
      const next = [...current];
      next[index] = next[target];
      next[target] = node;
      return next;
    });
  };

  const patchSelected = (patch: Partial<DesignerNode>) => {
    if (!selectedId) return;
    setNodes((current) =>
      current.map((node) => (node.id === selectedId ? { ...node, ...patch } : node)),
    );
  };

  const handleSave = async (publish: boolean) => {
    try {
      const values = await metaForm.validateFields();
      if (approvalCount === 0) {
        message.warning(t('approval.designer.needApprovalNode'));
        return;
      }
      setSubmitting(true);
      const status: ApprovalConfigStatus = publish ? 'ENABLED' : 'DISABLED';
      const payload = {
        processKey: values.processKey,
        processName: values.processName,
        description: values.description,
        formId: values.formId || null,
        nodeJson: JSON.stringify(nodes.map(stripNodeId)),
        status,
      };
      if (editing) {
        await approvalEngineApi.updateProcess(editing.id, { ...payload, version: editing.version });
        message.success(t(publish ? 'approval.designer.publishSuccess' : 'approval.designer.draftSuccess'));
      } else {
        await approvalEngineApi.createProcess(payload);
        message.success(t(publish ? 'approval.designer.publishSuccess' : 'approval.designer.draftSuccess'));
      }
      onSaved();
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      className="process-designer-modal"
      title={editing ? t('approval.designer.editTitle') : t('approval.designer.createTitle')}
      open={open}
      onCancel={onClose}
      width={1180}
      destroyOnClose
      footer={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button loading={submitting} icon={<OaIcon name="save" />} onClick={() => void handleSave(false)}>
            {t('approval.designer.saveDraft')}
          </Button>
          <Button type="primary" loading={submitting} icon={<OaIcon name="send" />} onClick={() => void handleSave(true)}>
            {t('approval.designer.publish')}
          </Button>
        </Space>
      }
    >
      <div className="process-designer">
        {/* 左侧：节点工具条 */}
        <aside className="process-designer__toolbox">
          <Typography.Text strong>{t('approval.designer.nodes')}</Typography.Text>
          <Button block icon={<OaIcon name="approval" />} onClick={() => insertBeforeEnd('APPROVAL')}>
            {t('approval.designer.addApproval')}
          </Button>
          <Button block icon={<OaIcon name="rules" />} onClick={() => insertBeforeEnd('CONDITION')}>
            {t('approval.designer.addCondition')}
          </Button>
          <Button block icon={<OaIcon name="notification" />} onClick={() => insertBeforeEnd('CC')}>
            {t('approval.designer.addCc')}
          </Button>
          <Button block icon={<OaIcon name="pause" />} onClick={() => insertBeforeEnd('DELAY')}>
            {t('approval.designer.addDelay')}
          </Button>
        </aside>

        {/* 中间：竖向节点画布 */}
        <div className="process-designer__canvas">
          {nodes.map((node, index) => (
            <div key={node.id} className="process-designer__chain">
              {index > 0 && <div className="process-designer__connector" aria-hidden />}
              <div
                className={`process-node is-${node.nodeType.toLowerCase()}${node.id === selectedId ? ' is-selected' : ''}`}
                onClick={() => setSelectedId(node.id)}
                role="button"
                tabIndex={0}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') setSelectedId(node.id);
                }}
              >
                <span className="process-node__icon"><OaIcon name={NODE_TYPE_ICON[node.nodeType]} /></span>
                <div className="process-node__main">
                  <Typography.Text strong>{node.nodeName || t('approval.designer.unnamed')}</Typography.Text>
                  <Tag bordered={false}>{t(`approval.designer.nodeType.${node.nodeType}`)}</Tag>
                  {node.nodeType === 'APPROVAL' && (
                    <Typography.Text type="secondary">
                      {t(`approval.designer.approveType.${APPROVAL_OPT_KEYS[node.approveType || 'DIRECT_MANAGER']}`)}
                      {node.mode ? ` · ${t(`approval.designer.mode.${node.mode}`)}` : ''}
                    </Typography.Text>
                  )}
                </div>
                <Space size={2} className="process-node__ops">
                  <Button
                    size="small"
                    type="text"
                    disabled={index <= 1 || nodes[index - 1]?.nodeType === 'START'}
                    icon={<ArrowUpOutlined />}
                    onClick={(event) => { event.stopPropagation(); moveNode(node.id, -1); }}
                    aria-label={t('approval.designer.moveUp')}
                  />
                  <Button
                    size="small"
                    type="text"
                    disabled={index >= nodes.length - 2 || nodes[index + 1]?.nodeType === 'END'}
                    icon={<ArrowDownOutlined />}
                    onClick={(event) => { event.stopPropagation(); moveNode(node.id, 1); }}
                    aria-label={t('approval.designer.moveDown')}
                  />
                  <Button
                    size="small"
                    type="text"
                    danger
                    disabled={node.nodeType === 'START' || node.nodeType === 'END'}
                    icon={<DeleteOutlined />}
                    onClick={(event) => { event.stopPropagation(); removeNode(node.id); }}
                    aria-label={t('approval.designer.removeNode')}
                  />
                </Space>
              </div>
            </div>
          ))}
        </div>

        {/* 右侧：元信息 + 节点属性 */}
        <div className="process-designer__panel">
          <Typography.Title level={5}>{t('approval.designer.metaTitle')}</Typography.Title>
          <Form form={metaForm} layout="vertical" size="small" initialValues={{ status: 'ENABLED' }}>
            <Form.Item
              name="processKey"
              label={t('approval.config.process.processKey')}
              rules={[
                { required: true, message: t('approval.config.common.fieldRequired') },
                { pattern: /^[a-z][a-z0-9_-]*$/, message: t('approval.config.common.keyInvalid') },
              ]}
            >
              <Input maxLength={64} disabled={Boolean(editing)} />
            </Form.Item>
            <Form.Item
              name="processName"
              label={t('approval.config.process.processName')}
              rules={[{ required: true, message: t('approval.config.common.fieldRequired') }]}
            >
              <Input maxLength={120} />
            </Form.Item>
            <Form.Item name="formId" label={t('approval.config.process.formId')}>
              <Select
                allowClear
                placeholder={t('approval.config.process.formPlaceholder')}
                options={forms.map((f) => ({ value: f.id, label: f.formName }))}
              />
            </Form.Item>
            <Form.Item name="description" label={t('approval.config.process.descriptionLabel')}>
              <Input.TextArea rows={2} maxLength={500} showCount />
            </Form.Item>
          </Form>

          <Typography.Title level={5} style={{ marginTop: 8 }}>
            {t('approval.designer.nodeProperties')}
          </Typography.Title>
          {!selected ? (
            <Alert type="info" showIcon message={t('approval.designer.selectHint')} />
          ) : (
            <NodeProperties node={selected} onPatch={patchSelected} onRemove={() => removeNode(selected.id)} />
          )}
        </div>
      </div>
      {approvalCount === 0 && (
        <Alert
          style={{ marginTop: 12 }}
          type="warning"
          showIcon
          message={t('approval.designer.needApprovalNode')}
        />
      )}
    </Modal>
  );
}

function NodeProperties({
  node,
  onPatch,
  onRemove,
}: {
  node: DesignerNode;
  onPatch: (patch: Partial<DesignerNode>) => void;
  onRemove: () => void;
}) {
  const { t } = useTranslation();
  const isApproval = node.nodeType === 'APPROVAL';

  return (
    <div className="process-node-props">
      <Form layout="vertical" size="small" initialValues={node}>
        <Form.Item label={t('approval.designer.nodeName')}>
          <Input
            maxLength={40}
            value={node.nodeName}
            disabled={node.nodeType === 'START' || node.nodeType === 'END'}
            onChange={(e) => onPatch({ nodeName: e.target.value })}
          />
        </Form.Item>

        {isApproval && (
          <>
            <Form.Item label={t('approval.designer.approverType')}>
              <Select
                value={node.approveType}
                onChange={(value: DesignerApproveType) => onPatch({ approveType: value })}
                options={DEFAULT_APPROVE_OPTIONS.map((opt) => ({
                  value: opt.value,
                  label: t(`approval.designer.approveType.${opt.key}`),
                }))}
              />
            </Form.Item>
            {(node.approveType === 'USER' || node.approveType === 'ROLE') && (
              <Form.Item label={t('approval.designer.targetKey')}>
                <Input
                  value={node.targetKey}
                  placeholder={t('approval.designer.targetKeyPlaceholder')}
                  onChange={(e) => onPatch({ targetKey: e.target.value })}
                />
              </Form.Item>
            )}
            <Form.Item label={t('approval.designer.nodeMode')}>
              <Select
                value={node.mode}
                onChange={(value: DesignerMode) => onPatch({ mode: value })}
                options={MODE_OPTIONS.map((opt) => ({
                  value: opt.value,
                  label: t(`approval.designer.mode.${opt.key}`),
                }))}
              />
            </Form.Item>
            <Form.Item label={t('approval.designer.timeoutEnabled')}>
              <Switch
                checked={node.timeoutEnabled}
                onChange={(checked) => onPatch({ timeoutEnabled: checked })}
              />
            </Form.Item>
            {node.timeoutEnabled && (
              <>
                <Form.Item label={t('approval.designer.timeoutHours')}>
                  <InputNumber
                    min={1}
                    max={720}
                    style={{ width: '100%' }}
                    value={node.timeoutHours}
                    onChange={(value) => onPatch({ timeoutHours: value ?? undefined })}
                  />
                </Form.Item>
                <Form.Item label={t('approval.designer.timeoutActionLabel')}>
                  <Select
                    value={node.timeoutAction}
                    onChange={(value: 'REMIND' | 'TRANSFER' | 'AUTO_APPROVE') => onPatch({ timeoutAction: value })}
                    options={TIMEOUT_ACTIONS.map((opt) => ({
                      value: opt.value,
                      label: t(`approval.designer.timeoutAction.${opt.key}`),
                    }))}
                  />
                </Form.Item>
              </>
            )}
          </>
        )}

        {node.nodeType === 'DELAY' && (
          <Form.Item label={t('approval.designer.delayHours')}>
            <InputNumber
              min={1}
              max={720}
              style={{ width: '100%' }}
              value={node.timeoutHours}
              onChange={(value) => onPatch({ timeoutHours: value ?? undefined })}
            />
          </Form.Item>
        )}

        {node.nodeType === 'CC' && (
          <Form.Item label={t('approval.designer.targetKey')}>
            <Input
              value={node.targetKey}
              placeholder={t('approval.designer.targetKeyPlaceholder')}
              onChange={(e) => onPatch({ targetKey: e.target.value })}
            />
          </Form.Item>
        )}

        {node.nodeType !== 'START' && node.nodeType !== 'END' && (
          <Button danger size="small" icon={<OaIcon name="delete" />} onClick={onRemove}>
            {t('approval.designer.removeNode')}
          </Button>
        )}
      </Form>
    </div>
  );
}

function parseExistingNodes(nodeJson: string): DesignerNode[] {
  try {
    const parsed = JSON.parse(nodeJson) as Array<Record<string, unknown>>;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item) => item && typeof item === 'object')
      .map((item) => {
        const nodeType = (item.nodeType as DesignerNodeType) || 'APPROVAL';
        return {
          id: newId(),
          nodeType,
          nodeName: String(item.nodeName || ''),
          approveType: (item.approveType as DesignerApproveType) || 'DIRECT_MANAGER',
          targetKey: item.targetKey ? String(item.targetKey) : '',
          mode: (item.mode as DesignerMode) || (nodeType === 'APPROVAL' ? 'OR_SIGN' : undefined),
          timeoutEnabled: Boolean(item.timeoutEnabled),
          timeoutHours: typeof item.timeoutHours === 'number' ? item.timeoutHours : 48,
          timeoutAction: (item.timeoutAction as 'REMIND' | 'TRANSFER' | 'AUTO_APPROVE') || 'REMIND',
        };
      });
  } catch {
    return [];
  }
}

/** 历史节点 JSON 不含开始/结束端点时自动补齐，保证画布链路完整。 */
function ensureEndpoints(nodes: DesignerNode[]): DesignerNode[] {
  if (nodes.length === 0) return nodes;
  const next = [...nodes];
  if (!next.some((node) => node.nodeType === 'START')) {
    next.unshift(createNode('START', 0));
  }
  if (!next.some((node) => node.nodeType === 'END')) {
    next.push(createNode('END', next.length));
  }
  return next;
}