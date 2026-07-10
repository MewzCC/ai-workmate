import type { ApprovalRecord } from '@/types/oa';

export const oaMetrics = [
  { title: '待审批流程', value: 128, suffix: '件', trend: '较昨日 +12' },
  { title: '本月流程准时率', value: 93.6, suffix: '%', trend: 'SLA 稳定' },
  { title: '接口告警', value: 3, suffix: '个', trend: '2 个已恢复' },
  { title: '权限风险', value: 1, suffix: '项', trend: '需复核' },
];

export const quickEntries = [
  { title: '审批管理', description: '处理低风险流程并生成意见', prompt: '帮我处理审批管理里的低风险流程' },
  { title: '费用报销', description: '检查异常单据与预算占用', prompt: '帮我检查费用报销异常单据' },
  { title: '资产管理', description: '为新员工分配设备和门禁', prompt: '帮新员工分配电脑和门禁' },
  { title: '组织人事', description: '部门变更并同步审批链', prompt: '修改员工部门并同步审批链' },
];

export const approvalRecords: ApprovalRecord[] = [
  { id: 'AP-2026-0710', name: '华东区客户招待费用报销', applicant: '周念', department: '销售运营', node: '财务复核', status: 'warning' },
  { id: 'AP-2026-0711', name: '研发云资源采购申请', applicant: '顾清', department: '研发中心', node: '部门负责人', status: 'processing' },
  { id: 'AP-2026-0712', name: '北京办公室会议室改造', applicant: '林予', department: '行政资产', node: '采购确认', status: 'success' },
  { id: 'AP-2026-0713', name: '合同补充协议审批', applicant: '沈禾', department: '法务合同', node: '资料补充', status: 'error' },
  { id: 'AP-2026-0714', name: '员工入职设备领用', applicant: '陈序', department: '组织人事', node: '待补充', status: 'default' },
];

export const timelineSeed = [
  { color: 'green', children: '08:55 SSO 与组织架构同步完成' },
  { color: 'blue', children: '09:20 AI 预审发现 1 条合同资料缺失' },
  { color: 'orange', children: '10:05 接口联调中心产生 3 条告警' },
];
