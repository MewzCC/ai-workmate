export default {
  title: '组织架构',
  description: '直观展示部门层级关系与员工分布，点击架构图中的部门卡片可筛选下方员工列表。',
  loadFailed: '组织架构加载失败',
  stats: {
    departments: '部门总数',
    employees: '员工总数',
    active: '在岗人数',
    positions: '岗位类型',
  },
  graph: {
    cardTitle: '部门架构图',
    hint: 'Ctrl + 滚轮缩放 · 拖拽移动 · 点击节点筛选员工 · 共 {{count}} 个部门',
    empty: '暂无部门数据',
    loading: '加载架构图…',
    virtualRoot: '集团',
    employeeCountBadge: '{{count}} 人',
    childrenCountBadge: '{{count}} 子部门',
  },
  filter: {
    departmentPlaceholder: '按部门筛选',
    searchPlaceholder: '搜索姓名或邮箱',
    clearDepartment: '清除部门筛选',
    totalEmployees: '共 {{count}} 人',
  },
  column: {
    employee: '员工',
    department: '部门',
    position: '岗位',
    approver: '直属审批人',
  },
  status: {
    active: '在岗',
    inactive: '停用',
  },
};
