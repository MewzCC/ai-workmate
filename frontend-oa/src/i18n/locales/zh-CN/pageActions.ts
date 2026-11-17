const pageActions = {
  eyebrow: '页面 · 动作 · 最小权限', title: '页面操作配置',
  description: '按页面收紧 Agent 可见动作。配置即时参与规划工具解析，但不能创建新能力或放宽安全策略。',
  saved: '页面动作策略已更新并立即生效', catalog: '页面动作目录', codeOwned: '代码注册上限',
  boundaryTitle: '此处只能收紧能力',
  boundaryDescription: '恢复开关只恢复代码已注册的页面绑定，仍需同时通过平台、租户、工具、角色、业务权限、确认和 Tool Gateway 校验。',
  pageSelect: '选择页面', search: '搜索动作名称、编码或说明', empty: '当前页面没有匹配的受控动作',
  enabled: '页面可见', disabled: '页面禁用', enableTitle: '恢复页面动作？', disableTitle: '关闭页面动作？',
  enableHint: '恢复不会授予任何新权限，也不会绕过其他安全门禁。', disableHint: '关闭后，该动作将立即从当前页面的 Agent 规划候选中移除。',
  reason: '变更理由', reasonRequired: '请输入 2–300 个字符的变更理由',
  stats: { pages: '覆盖页面', total: '注册动作', enabled: '页面可见', disabled: '已关闭' },
  columns: { action: '受控动作', risk: '风险', policy: '执行策略', permissions: '业务权限', status: '页面状态' },
};

export default pageActions;
