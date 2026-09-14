const expense = {
  eyebrow: 'FINANCE · REIMBURSEMENT', title: '费用报销',
  description: '保存报销草稿、发起审批并跟踪当前节点、处理时效与完整审批记录。',
  add: '新建报销', refresh: '刷新', total: '全部申请', draft: '草稿', pending: '审批中', approved: '已通过',
  searchStatus: '全部状态', empty: '暂无费用报销申请', saveDraft: '保存草稿', saveSuccess: '报销草稿已保存',
  submit: '提交审批', submitSuccess: '报销申请已提交', updateSuccess: '报销申请已更新',
  withdraw: '撤回', withdrawSuccess: '报销申请已撤回', cancel: '取消草稿', cancelSuccess: '草稿已取消',
  reopen: '重新编辑', reopenSuccess: '报销申请已恢复为草稿', remind: '催办', remindSuccess: '催办提醒已发送',
  view: '查看', edit: '编辑', actions: '操作',
  columns: { expense: '报销事项', category: '费用类型', amount: '报销金额', expenseDate: '费用日期', submittedAt: '提交时间', status: '状态', actions: '操作' },
  category: { TRAVEL: '差旅', MEAL: '餐饮', TRANSPORT: '交通', OFFICE: '办公', OTHER: '其他' },
  status: { DRAFT: '草稿', PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回', CANCELLED: '已取消' },
  form: { createTitle: '新建费用报销', editTitle: '编辑费用报销', amount: '报销金额', category: '费用类型', expenseDate: '费用日期', invoiceNumber: '发票号码', invoicePlaceholder: '填写发票号码或凭证编号', reason: '报销事由', reasonPlaceholder: '说明费用用途与业务背景', required: '此项必填' },
  detail: { title: '报销详情', overview: '申请信息', process: '审批进度', applicant: '申请人', currentApprover: '当前审批人', dueAt: '处理期限', createdAt: '创建时间', submittedAt: '提交时间', noTimeline: '草稿尚未产生审批记录' },
  dialog: { submitTitle: '确认提交报销申请？', submitContent: '提交后将进入审批流程，审批中不可直接修改。', withdrawTitle: '确认撤回申请？', cancelTitle: '确认取消草稿？', remindTitle: '确认催办当前审批人？' },
};

export default expense;
