export default {
  title: 'Organization',
  description: 'Visualize department hierarchy and employee distribution. Click a department card in the graph to filter the employee list below.',
  loadFailed: 'Failed to load organization',
  stats: {
    departments: 'Departments',
    employees: 'Employees',
    active: 'Active',
    positions: 'Positions',
  },
  graph: {
    cardTitle: 'Department Structure',
    hint: 'Ctrl + scroll to zoom · Drag to pan · Click a node to filter employees · {{count}} departments',
    empty: 'No department data',
    loading: 'Loading graph…',
    virtualRoot: 'Group',
    employeeCountBadge: '{{count}}',
    childrenCountBadge: '{{count}} sub-departments',
  },
  filter: {
    departmentPlaceholder: 'Filter by department',
    searchPlaceholder: 'Search name or email',
    clearDepartment: 'Clear department filter',
    totalEmployees: '{{count}} people',
  },
  column: {
    employee: 'Employee',
    department: 'Department',
    position: 'Position',
    approver: 'Direct Approver',
  },
  status: {
    active: 'Active',
    inactive: 'Inactive',
  },
};
