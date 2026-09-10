const dataPermission = {
  eyebrow:'Security governance · Real-time', title:'Data Permission Policies', description:'Control business-data visibility by role and user exception. Query predicates are resolved by the server.',
  newPolicy:'New policy', refresh:'Refresh', policies:'Policy library', roleBindings:'Role bindings', userExceptions:'User exceptions', preview:'Permission preview',
  policyCount:'Policies', boundRoles:'Bound roles', exceptions:'User exceptions', effectiveImmediately:'Effective immediately',
  noPolicy:'No data permission policies', policyName:'Policy name', policyDescription:'Description', scopeType:'Data scope', departments:'Custom departments', status:'Status', enabled:'Enabled', disabled:'Disabled',
  role:'Role', roleDescription:'Description', effectivePolicy:'Effective policy', user:'User', department:'Department', exceptionPolicy:'Exception policy', inheritRole:'Inherit role policy', actions:'Actions',
  createTitle:'Create data permission policy', editTitle:'Edit data permission policy', deleteTitle:'Delete policy', deleteContent:'Deletion cannot be undone. Only policies unused by roles or users can be deleted.',
  save:'Save policy', saveSuccess:'Policy saved and effective immediately', bindSuccess:'Data permission binding updated', deleteSuccess:'Policy deleted', required:'This field is required', customRequired:'Select at least one department for a custom scope',
  previewTitle:'Data permission preview', previewUser:'Preview user', source:'Policy source', sourceRole:'Role policy', sourceException:'User exception', visibleDepartments:'Visible departments', visibleUsers:'Visible users', visibleUserCount:'Can view {{count}} employees',
  scope:{SELF:'Self only',DEPARTMENT:'Department',DEPARTMENT_AND_CHILDREN:'Department and children',CUSTOM_DEPARTMENTS:'Custom departments',ALL:'All data'},
  scopeHint:{SELF:'Only the user’s own business data',DEPARTMENT:'Data in the user’s department',DEPARTMENT_AND_CHILDREN:'The department and every child department',CUSTOM_DEPARTMENTS:'Only explicitly selected departments',ALL:'All business data in the current tenant'},
};
export default dataPermission;
