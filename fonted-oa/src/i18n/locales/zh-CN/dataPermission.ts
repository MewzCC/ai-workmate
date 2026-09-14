const dataPermission = {
  eyebrow:'安全治理 · 实时生效', title:'数据权限策略', description:'按角色和用户例外控制业务数据可见范围，所有查询条件由服务端解析。',
  newPolicy:'新建策略', refresh:'刷新', policies:'策略库', roleBindings:'角色绑定', userExceptions:'用户例外', preview:'权限预览',
  policyCount:'策略总数', boundRoles:'已绑定角色', exceptions:'用户例外', effectiveImmediately:'实时生效',
  noPolicy:'暂无数据权限策略', policyName:'策略名称', policyDescription:'策略说明', scopeType:'数据范围', departments:'自定义部门', status:'状态', enabled:'启用', disabled:'停用',
  role:'角色', roleDescription:'角色说明', effectivePolicy:'生效策略', user:'用户', department:'部门', exceptionPolicy:'例外策略', inheritRole:'继承角色策略', actions:'操作',
  createTitle:'新建数据权限策略', editTitle:'编辑数据权限策略', deleteTitle:'删除策略', deleteContent:'删除后不可恢复，仅未被角色或用户引用的策略可删除。',
  save:'保存策略', saveSuccess:'策略已保存并实时生效', bindSuccess:'数据权限绑定已更新', deleteSuccess:'策略已删除', required:'此项为必填项', customRequired:'自定义范围至少选择一个部门',
  previewTitle:'数据权限预览', previewUser:'预览用户', source:'策略来源', sourceRole:'角色策略', sourceException:'用户例外', visibleDepartments:'可见部门', visibleUsers:'可见用户', visibleUserCount:'可查看 {{count}} 名员工',
  scope:{SELF:'仅本人',DEPARTMENT:'本部门',DEPARTMENT_AND_CHILDREN:'本部门及下级',CUSTOM_DEPARTMENTS:'自定义部门',ALL:'全部数据'},
  scopeHint:{SELF:'只允许查看本人业务数据',DEPARTMENT:'查看本人所在部门数据',DEPARTMENT_AND_CHILDREN:'包含本部门和所有下级部门',CUSTOM_DEPARTMENTS:'仅查看明确选择的部门',ALL:'查看当前租户全部业务数据'},
};
export default dataPermission;
