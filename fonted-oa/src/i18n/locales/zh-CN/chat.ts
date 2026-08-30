export default {
  // AiChatWorkspace
  conversationsLoadFailed: '会话加载失败',
  sidebarRailAriaLabel: '会话栏快捷操作',
  expandSidebar: '展开会话栏',
  newChat: '新建聊天',
  settings: '设置',
  newConversation: '新对话',
  historySessions: '历史会话',
  recents: '最近会话',
  recentsEmpty: '暂无最近会话',

  // AttachmentPreview
  markdownLoadFailed: 'Markdown 文档加载失败',
  clickToPreview: '点击预览',
  removeAttachment: '移除 {{name}}',
  previewFailed: '文档预览失败',
  parsed: '已解析',
  pendingParse: '待解析',
  ocrParsed: '已通过 OCR 解析',
  ocrPending: '图片识别不可用',

  // ChatInput
  inputPlaceholder: '输入消息，或拖入图片和文档…',
  uploadFile: '上传图片或文件',
  uploading: '上传中',
  inputHint: 'Enter 发送 · Shift + Enter 换行',
  stopGenerating: '停止生成',
  sendMessage: '发送消息',
  dropToUpload: '松开以上传文件',
  unsupportedFileType: '{{name}}：暂不支持该文件类型',
  fileSizeExceeded: '{{name}} 超过 {{limit}} 限制',

  // ChatSidebar
  groupToday: '今天',
  groupLast7Days: '过去 7 天',
  groupLast30Days: '过去 30 天',
  groupEarlier: '更早',
  noMessages: '暂无消息',
  renameConversation: '重命名会话',
  titleRequired: '标题不能为空',
  deleteConversationTitle: '删除该会话？',
  deleteConversationContent: '“{{title}}”及其消息和附件将永久删除。',
  generating: '生成中',
  rename: '重命名',
  manageConversation: '管理会话 {{title}}',
  collapseSidebar: '收起会话栏',
  searchSessions: '搜索会话与消息',
  noSessions: '暂无会话',
  settingsTooltip: '模型、上下文与数据设置',
  yesterday: '昨天',

  // ChatWindow
  serverValidated: '权限由服务端校验',
  selectKnowledgeBase: '选择知识库',
  allKnowledgeBases: '全部知识库',
  switchModel: '切换对话模型',
  disclaimer: 'AI 可能出错；涉及审批、财务或权限变更时请核对执行计划。',

  // CitationList & MarkdownRenderer
  citationsTitle: '引用知识库',
  noContent: '（无内容）',
  similarity: '相似度 {{score}}%',
  viewCitation: '查看引用内容：{{source}}',

  // MarkdownRenderer code block
  codeCopyFailed: '代码复制失败',
  copied: '已复制',
  copyCode: '复制代码',

  // MessageItem
  feedbackRecorded: '反馈已记录',
  feedbackFailed: '反馈提交失败',
  replyCopied: '回复已复制',
  replyCopyFailed: '回复复制失败',
  assistantName: 'WorkMate AI',
  you: '你',
  statusGenerating: '生成中',
  statusIncomplete: '未完成',
  thinking: '正在思考...',
  copyReply: '复制回复',
  regenerate: '重新生成',
  helpful: '有帮助',
  needImprove: '需改进',

  // MessageList
  emptyTitle: '今天想一起完成什么？',
  emptyDesc: '可以直接提问，也可以上传图片、表格或文档作为上下文。',
  retryPrevious: '请重新回答上一条问题。',
  starter: {
    summarize: '帮我总结一份文档',
    analyzeImage: '分析这张图片',
    writeCode: '帮我写一段代码',
    explainError: '解释这个报错',
  },

  // SystemSettingsPage
  settingsTitle: 'AI Workspace 设置',
  saveSettings: '保存设置',
  settingsSaved: '设置已保存',
  ocrSettings: 'OCR 识别',
  apiKeyLabel: 'API 密钥',
  apiKeyManaged: '由服务端环境变量管理',
  apiKeyHint: '密钥不会下发到浏览器，请通过后端 `AI_API_KEY` 配置。',
  model: '对话模型',
  selectModelRequired: '请选择对话模型',
  maxContextRounds: '最大上下文轮数',
  streamOutput: '流式输出',
  forcePdfOcr: 'PDF 文件始终通过 OCR 识别',
  forcePdfOcrHint: '开启后所有 PDF（含带文字层）都走 OCR 识别；关闭时仅扫描版 PDF 自动 OCR。',
  baseUrlHint: '接口地址由服务端 AI_BASE_URL 管理，避免凭据和内部网关信息暴露。',
  dataManagement: '数据管理',
  clearAllTitle: '清空全部聊天记录？',
  clearAllContent: '该操作会删除当前账号的全部会话、消息和附件，且无法恢复。',
  confirmClear: '确认清空',
  clearAllRecords: '清空全部聊天记录',

  // aiChatStore error fallbacks
  loadHistoryFailed: '聊天记录加载失败',
  createConversationFailed: '新建会话失败',
  uploadFailed: '上传失败',
  defaultAttachmentPrompt: '请分析这些附件。',
  aiReplyFailed: 'AI 回复失败',
};
