export default {
  eyebrow:'财务 · 预算控制台',title:'预算中心',subtitle:'统一编制年度预算，实时跟踪占用、核销、释放与额度预警。',
  actions:{create:'新建预算',confirmStatus:'确认变更预算状态'},
  stats:{totalPlans:'预算项目',activePlans:'执行中',warningPlans:'额度预警',totalAmount:'启用预算',occupiedAmount:'已占用',availableAmount:'可用额度'},
  filters:{keyword:'搜索编号、名称或负责人',status:'全部状态',year:'预算年度'},
  columns:{budget:'预算项目',owner:'负责人',total:'预算总额',execution:'执行进度',available:'可用额度',status:'状态'},
  status:{DRAFT:'草稿',ACTIVE:'执行中',CLOSED:'已关闭',CANCELLED:'已取消'}, alert:{NORMAL:'正常',WARNING:'预警',FULL:'已用尽'},
  usage:'占用 {{occupied}} · 支出 {{spent}}',
  editor:{create:'编制预算',edit:'编辑预算'}, detail:{title:'预算详情',timeline:'额度流水'},
  fields:{code:'预算编号',year:'预算年度',name:'预算名称',owner:'负责人',amount:'预算总额',currency:'币种',threshold:'预警阈值',summary:'预算说明',operationAmount:'变动金额',reference:'业务单号',note:'备注'},
  operation:{OCCUPY:'占用额度',RELEASE:'释放额度',SPEND:'确认支出'},
  transaction:{CREATED:'创建预算',UPDATED:'更新预算',STATUS_ACTIVE:'启用预算',STATUS_CLOSED:'关闭预算',STATUS_CANCELLED:'取消预算',OCCUPY:'占用额度',RELEASE:'释放额度',SPEND:'确认支出'},
  messages:{saved:'预算已保存',status:'预算状态已更新',operation:'额度流水已登记'},
};
