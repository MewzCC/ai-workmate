'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  DatePicker,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
} from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CopyOutlined,
  DeleteOutlined,
  UndoOutlined,
  RedoOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { message } from '@/lib/antdMessage';
import { useTranslation } from 'react-i18next';
import {
  approvalEngineApi,
  type ApprovalConfigStatus,
  type ApprovalForm,
} from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';

// ==================== 字段模型 ====================

export type BuilderFieldType =
  | 'text' | 'textarea' | 'number' | 'money' | 'date' | 'dateRange' | 'time'
  | 'radio' | 'checkbox' | 'select' | 'user' | 'department' | 'file' | 'image'
  | 'table' | 'divider';

export interface BuilderField {
  /** 仅设计器内部使用，保存时剔除 */
  id: string;
  type: BuilderFieldType;
  label: string;
  /** 落库为 fields[].name（后端字段 Key） */
  key: string;
  required: boolean;
  placeholder?: string;
  options: string[];
  width: 'full' | 'half';
}

interface MetaFormValues {
  formKey: string;
  formName: string;
  description?: string;
}

const FIELD_TYPES: { type: BuilderFieldType; category: 'input' | 'choice' | 'person' | 'asset' | 'advanced' }[] = [
  { type: 'text', category: 'input' },
  { type: 'textarea', category: 'input' },
  { type: 'number', category: 'input' },
  { type: 'money', category: 'input' },
  { type: 'date', category: 'input' },
  { type: 'dateRange', category: 'input' },
  { type: 'time', category: 'input' },
  { type: 'radio', category: 'choice' },
  { type: 'checkbox', category: 'choice' },
  { type: 'select', category: 'choice' },
  { type: 'user', category: 'person' },
  { type: 'department', category: 'person' },
  { type: 'file', category: 'asset' },
  { type: 'image', category: 'asset' },
  { type: 'table', category: 'advanced' },
  { type: 'divider', category: 'advanced' },
];

const PALETTE_LABELS: Record<string, { zh: string; en: string }> = {
  text: { zh: '单行文本', en: 'Single Line' },
  textarea: { zh: '多行文本', en: 'Multi Line' },
  number: { zh: '数字', en: 'Number' },
  money: { zh: '金额', en: 'Amount' },
  date: { zh: '日期', en: 'Date' },
  dateRange: { zh: '日期范围', en: 'Date Range' },
  time: { zh: '时间', en: 'Time' },
  radio: { zh: '单选', en: 'Radio' },
  checkbox: { zh: '多选', en: 'Checkbox' },
  select: { zh: '下拉选择', en: 'Select' },
  user: { zh: '人员选择', en: 'User Picker' },
  department: { zh: '部门选择', en: 'Department Picker' },
  file: { zh: '文件上传', en: 'File Upload' },
  image: { zh: '图片上传', en: 'Image Upload' },
  table: { zh: '明细表', en: 'Detail Table' },
  divider: { zh: '分割线', en: 'Divider' },
};

function newFieldId(): string {
  return `f-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function createField(type: BuilderFieldType, index: number): BuilderField {
  const key = `${type}${index + 1}`;
  return {
    id: newFieldId(),
    type,
    label: upperFirst(type),
    key,
    required: false,
    placeholder: '',
    options: [],
    width: 'full',
  };
}

function upperFirst(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

interface PersistedField {
  name?: string;
  label?: string;
  type?: string;
  required?: boolean;
  placeholder?: string;
  options?: string[];
  width?: 'full' | 'half';
}

function parseSchemaFields(schemaJson: string): BuilderField[] {
  try {
    const parsed = JSON.parse(schemaJson) as { fields?: PersistedField[] };
    if (!Array.isArray(parsed.fields)) return [];
    return parsed.fields
      .filter((f) => f && typeof f === 'object')
      .map((f, index) => {
        const type = FIELD_TYPES.some((item) => item.type === f.type)
          ? (f.type as BuilderFieldType)
          : 'text';
        return {
          id: newId(f.name || String(index)),
          type,
          label: f.label || f.name || upperFirst(type),
          key: f.name || `${type}${index + 1}`,
          required: Boolean(f.required),
          placeholder: f.placeholder || '',
          options: Array.isArray(f.options) ? f.options.filter((o) => typeof o === 'string') : [],
          width: f.width === 'half' ? 'half' : 'full',
        };
      });
  } catch {
    return [];
  }
}

function newId(seed: string): string {
  return `f-${seed}-${Math.random().toString(36).slice(2, 6)}`;
}

function stripFieldId(field: BuilderField): Omit<BuilderField, 'id'> {
  const { id: _id, ...rest } = field;
  void _id;
  return rest;
}

function fieldTypeLabel(type: BuilderFieldType) {
  return PALETTE_LABELS[type as keyof typeof PALETTE_LABELS];
}

/**
 * 三栏式表单设计器：左侧组件库 / 中间实时画布 / 右侧字段属性，
 * 顶部支持撤销、重做、预览；保存 / 发布直接落库后端表单定义。
 */
export default function FormDesignerModal({
  open,
  editing,
  onClose,
  onSaved,
}: {
  open: boolean;
  editing: ApprovalForm | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t, i18n } = useTranslation();
  const [metaForm] = Form.useForm<MetaFormValues>();
  const [fields, setFields] = useState<BuilderField[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [past, setPast] = useState<BuilderField[][]>([]);
  const [future, setFuture] = useState<BuilderField[][]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      metaForm.setFieldsValue({
        formKey: editing.formKey,
        formName: editing.formName,
        description: editing.description || '',
      });
      setFields(parseSchemaFields(editing.schemaJson));
    } else {
      metaForm.resetFields();
      setFields([]);
    }
    setSelectedId(undefined);
    setPast([]);
    setFuture([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing]);

  const selected = useMemo(
    () => fields.find((field) => field.id === selectedId),
    [fields, selectedId],
  );

  const commit = (next: BuilderField[]) => {
    setPast((current) => [...current.slice(-49), fields]);
    setFuture([]);
    setFields(next);
  };

  const appendField = (type: BuilderFieldType) => {
    const field = createField(type, fields.length);
    commit([...fields, field]);
    setSelectedId(field.id);
  };

  const moveField = (id: string, direction: -1 | 1) => {
    const index = fields.findIndex((f) => f.id === id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= fields.length) return;
    const next = [...fields];
    next[index] = next[target];
    next[target] = fields[index];
    commit(next);
  };

  const copyField = (id: string) => {
    const index = fields.findIndex((f) => f.id === id);
    if (index < 0) return;
    const source = fields[index];
    const copy: BuilderField = {
      ...source,
      id: newFieldId(),
      key: `${source.key}-copy`,
    };
    const next = [...fields];
    next.splice(index + 1, 0, copy);
    commit(next);
    setSelectedId(copy.id);
  };

  const removeField = (id: string) => {
    commit(fields.filter((f) => f.id !== id));
    if (selectedId === id) setSelectedId(undefined);
  };

  const patchSelected = (patch: Partial<BuilderField>) => {
    if (!selectedId) return;
    commit(fields.map((f) => (f.id === selectedId ? { ...f, ...patch } : f)));
  };

  const undo = () => {
    if (past.length === 0) return;
    const previous = past[past.length - 1];
    setFuture((current) => [fields, ...current]);
    setPast((current) => current.slice(0, -1));
    setFields(previous);
  };

  const redo = () => {
    if (future.length === 0) return;
    const next = future[0];
    setPast((current) => [...current, fields]);
    setFuture((current) => current.slice(1));
    setFields(next);
  };

  const handleSave = async (publish: boolean) => {
    try {
      const values = await metaForm.validateFields();
      if (fields.length === 0) {
        message.warning(t('approval.builder.needField'));
        return;
      }
      setSubmitting(true);
      const payload = {
        formKey: values.formKey,
        formName: values.formName,
        description: values.description,
        schemaJson: JSON.stringify({ fields: fields.map(stripFieldId) }),
        status: (publish ? 'ENABLED' : 'DISABLED') as ApprovalConfigStatus,
      };
      if (editing) {
        await approvalEngineApi.updateForm(editing.id, { ...payload, version: editing.version });
        message.success(t(publish ? 'approval.builder.publishSuccess' : 'approval.config.common.updateSuccess'));
      } else {
        await approvalEngineApi.createForm(payload);
        message.success(t(publish ? 'approval.builder.publishSuccess' : 'approval.config.common.createSuccess'));
      }
      onSaved();
    } catch (err) {
      if (err instanceof Error && err.name === 'ValidationError') return;
      message.error(formatOaApiError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const selectedTypeLabelFor = (type: BuilderFieldType) =>
    fieldTypeLabel(type)[i18n.language === 'en-US' ? 'en' : 'zh'];

  return (
    <Modal
      className="form-designer-modal"
      title={editing ? t('approval.builder.editTitle') : t('approval.builder.createTitle')}
      open={open}
      onCancel={onClose}
      width={1100}
      destroyOnClose
      footer={
        <Space>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button icon={<UndoOutlined />} disabled={past.length === 0} onClick={undo}>
            {t('approval.builder.undo')}
          </Button>
          <Button icon={<RedoOutlined />} disabled={future.length === 0} onClick={redo}>
            {t('approval.builder.redo')}
          </Button>
          <Button icon={<EyeOutlined />} disabled={fields.length === 0} onClick={() => setPreviewOpen(true)}>
            {t('approval.builder.preview')}
          </Button>
          <Button loading={submitting} icon={<OaIcon name="save" />} onClick={() => void handleSave(false)}>
            {t('common.save')}
          </Button>
          <Button type="primary" loading={submitting} icon={<OaIcon name="send" />} onClick={() => void handleSave(true)}>
            {t('approval.builder.publish')}
          </Button>
        </Space>
      }
    >
      <Form form={metaForm} layout="inline" className="form-designer__meta">
        <Form.Item
          name="formKey"
          label={t('approval.config.form.formKey')}
          rules={[
            { required: true, message: t('approval.config.common.fieldRequired') },
            { pattern: /^[a-z][a-z0-9_-]*$/, message: t('approval.config.common.keyInvalid') },
          ]}
          style={{ flex: 1 }}
        >
          <Input maxLength={64} disabled={Boolean(editing)} />
        </Form.Item>
        <Form.Item
          name="formName"
          label={t('approval.config.form.formName')}
          rules={[{ required: true, message: t('approval.config.common.fieldRequired') }]}
          style={{ flex: 1 }}
        >
          <Input maxLength={120} />
        </Form.Item>
        <Form.Item name="description" label={t('approval.config.form.descriptionLabel')} style={{ flex: 2 }}>
          <Input maxLength={500} />
        </Form.Item>
      </Form>

      <div className="form-designer">
        {/* 左侧：基础组件库 */}
        <aside className="form-designer__palette">
          <Typography.Text strong>{t('approval.builder.basicComponents')}</Typography.Text>
          {(['input', 'choice', 'person', 'asset', 'advanced'] as const).map((category) => (
            <div key={category} className="form-designer__palette-group">
              <Typography.Text type="secondary">{t(`approval.builder.category.${category}`)}</Typography.Text>
              {FIELD_TYPES.filter((item) => item.category === category).map((item) => (
                <Button
                  key={item.type}
                  block
                  size="small"
                  onClick={() => appendField(item.type)}
                >
                  {fieldTypeLabel(item.type)[i18n.language === 'en-US' ? 'en' : 'zh']}
                </Button>
              ))}
            </div>
          ))}
        </aside>

        {/* 中间：实时画布 */}
        <div className="form-designer__canvas">
          {fields.length === 0 ? (
            <div className="form-designer__canvas-empty">
              <Typography.Text type="secondary">{t('approval.builder.canvasEmpty')}</Typography.Text>
            </div>
          ) : (
            fields.map((field, index) => (
              <div
                key={field.id}
                className={`form-field-row${field.id === selectedId ? ' is-selected' : ''}`}
                onClick={() => setSelectedId(field.id)}
                role="button"
                tabIndex={0}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') setSelectedId(field.id);
                }}
              >
                <span className="form-field-row__index">{index + 1}</span>
                <div className="form-field-row__main">
                  <Space size={6} wrap>
                    <Typography.Text strong>{field.required ? `${field.label} *` : field.label}</Typography.Text>
                    <Tag bordered={false}>{selectedTypeLabelFor(field.type)}</Tag>
                    {field.width === 'half' && <Tag bordered={false}>{t('approval.builder.halfWidth')}</Tag>}
                  </Space>
                  {field.type === 'divider'
                    ? <Divider style={{ margin: '6px 0 0' }} />
                    : (
                      <Typography.Text type="secondary">
                        {field.placeholder || `${t('approval.builder.placeholderHint')} ${field.key}`}
                      </Typography.Text>
                    )}
                </div>
                <Space size={2}>
                  <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={index === 0}
                    onClick={(e) => { e.stopPropagation(); moveField(field.id, -1); }} aria-label={t('approval.designer.moveUp')} />
                  <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={index === fields.length - 1}
                    onClick={(e) => { e.stopPropagation(); moveField(field.id, 1); }} aria-label={t('approval.designer.moveDown')} />
                  <Button size="small" type="text" icon={<CopyOutlined />}
                    onClick={(e) => { e.stopPropagation(); copyField(field.id); }} aria-label={t('approval.builder.copyField')} />
                  <Button size="small" type="text" danger icon={<DeleteOutlined />}
                    onClick={(e) => { e.stopPropagation(); removeField(field.id); }} aria-label={t('approval.config.common.delete')} />
                </Space>
              </div>
            ))
          )}
        </div>

        {/* 右侧：字段属性 */}
        <div className="form-designer__props">
          <Typography.Title level={5}>{t('approval.builder.fieldProperties')}</Typography.Title>
          {!selected ? (
            <Alert type="info" showIcon message={t('approval.builder.selectHint')} />
          ) : (
            <Form layout="vertical" size="small">
              <Form.Item label={t('approval.builder.fieldLabel')}>
                <Input
                  maxLength={40}
                  value={selected.label}
                  disabled={selected.type === 'divider'}
                  onChange={(e) => patchSelected({ label: e.target.value })}
                />
              </Form.Item>
              <Form.Item label={t('approval.builder.fieldKey')}>
                <Input
                  maxLength={40}
                  value={selected.key}
                  disabled={selected.type === 'divider'}
                  onChange={(e) => patchSelected({ key: e.target.value })}
                />
              </Form.Item>
              <Form.Item label={t('approval.builder.fieldRequired')}>
                <Switch
                  checked={selected.required}
                  disabled={selected.type === 'divider'}
                  onChange={(checked) => patchSelected({ required: checked })}
                />
              </Form.Item>
              {selected.type !== 'divider' && (
                <Form.Item label={t('approval.builder.fieldWidth')}>
                  <Select
                    value={selected.width}
                    onChange={(value: 'full' | 'half') => patchSelected({ width: value })}
                    options={[
                      { value: 'full', label: t('approval.builder.widthFull') },
                      { value: 'half', label: t('approval.builder.widthHalf') },
                    ]}
                  />
                </Form.Item>
              )}
              {selected.placeholder !== undefined && selected.type !== 'divider' && (
                <Form.Item label={t('approval.builder.placeholder')}>
                  <Input
                    maxLength={80}
                    value={selected.placeholder}
                    onChange={(e) => patchSelected({ placeholder: e.target.value })}
                  />
                </Form.Item>
              )}
              {(selected.type === 'select' || selected.type === 'radio' || selected.type === 'checkbox') && (
                <Form.Item label={t('approval.builder.options')}>
                  <OptionsEditor
                    options={selected.options}
                    onChange={(options) => patchSelected({ options })}
                  />
                </Form.Item>
              )}
            </Form>
          )}
        </div>
      </div>

      <FormPreview
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        fields={fields}
      />
    </Modal>
  );
}

function OptionsEditor({
  options,
  onChange,
}: {
  options: string[];
  onChange: (options: string[]) => void;
}) {
  const { t } = useTranslation();
  const update = (index: number, value: string) => {
    onChange(options.map((option, i) => (i === index ? value : option)));
  };
  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      {options.map((option, index) => (
        <Space key={index} style={{ width: '100%' }}>
          <Input
            value={option}
            onChange={(e) => update(index, e.target.value)}
            placeholder={t('approval.builder.optionPlaceholder')}
          />
          <Button
            size="small"
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => onChange(options.filter((_, i) => i !== index))}
            aria-label={t('approval.config.common.delete')}
          />
        </Space>
      ))}
      <Button size="small" type="dashed" block onClick={() => onChange([...options, ''])}>
        {t('approval.builder.addOption')}
      </Button>
    </Space>
  );
}

function FormPreview({
  open,
  onClose,
  fields,
}: {
  open: boolean;
  onClose: () => void;
  fields: BuilderField[];
}) {
  const { t } = useTranslation();
  return (
    <Modal
      title={t('approval.builder.preview')}
      open={open}
      onCancel={onClose}
      footer={<Button onClick={onClose}>{t('common.cancel')}</Button>}
      width={640}
    >
      <Form layout="vertical">
        {fields.map((field) => {
          if (field.type === 'divider') {
            return <Divider key={field.id}>{field.label}</Divider>;
          }
          const label = `${field.label}${field.required ? ' *' : ''}`;
          const placeholder = field.placeholder || '';
          switch (field.type) {
            case 'textarea':
              return (
                <Form.Item key={field.id} label={label}>
                  <Input.TextArea rows={3} placeholder={placeholder} disabled />
                </Form.Item>
              );
            case 'number':
            case 'money':
              return (
                <Form.Item key={field.id} label={label}>
                  <InputNumber style={{ width: '100%' }} placeholder={placeholder} disabled />
                </Form.Item>
              );
            case 'date':
            case 'time':
              return (
                <Form.Item key={field.id} label={label}>
                  <DatePicker style={{ width: '100%' }} placeholder={placeholder} disabled />
                </Form.Item>
              );
            case 'dateRange':
              return (
                <Form.Item key={field.id} label={label}>
                  <DatePicker.RangePicker style={{ width: '100%' }} disabled />
                </Form.Item>
              );
            case 'radio':
              return (
                <Form.Item key={field.id} label={label}>
                  <Radio.Group disabled>
                    {field.options.map((option, i) => <Radio key={i} value={option}>{option}</Radio>)}
                  </Radio.Group>
                </Form.Item>
              );
            case 'checkbox':
              return (
                <Form.Item key={field.id} label={label}>
                  <Checkbox.Group disabled options={field.options} />
                </Form.Item>
              );
            case 'select':
              return (
                <Form.Item key={field.id} label={label}>
                  <Select
                    placeholder={placeholder}
                    disabled
                    options={field.options.map((option) => ({ value: option, label: option }))}
                  />
                </Form.Item>
              );
            case 'user':
            case 'department':
              return (
                <Form.Item key={field.id} label={label}>
                  <Select placeholder={placeholder} disabled />
                </Form.Item>
              );
            default:
              return (
                <Form.Item key={field.id} label={label}>
                  <Input placeholder={placeholder} disabled />
                </Form.Item>
              );
          }
        })}
        {fields.length === 0 && (
          <Typography.Text type="secondary">{t('approval.builder.canvasEmpty')}</Typography.Text>
        )}
      </Form>
    </Modal>
  );
}