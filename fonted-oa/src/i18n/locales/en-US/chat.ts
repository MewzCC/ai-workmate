export default {
  // AiChatWorkspace
  conversationsLoadFailed: 'Failed to load conversations',
  sidebarRailAriaLabel: 'Sidebar quick actions',
  expandSidebar: 'Expand sidebar',
  newChat: 'New chat',
  settings: 'Settings',
  newConversation: 'New conversation',
  historySessions: 'History',
  recents: 'Recent chats',
  recentsEmpty: 'No recent chats',

  // AttachmentPreview
  markdownLoadFailed: 'Failed to load the Markdown document',
  clickToPreview: 'Click to preview',
  removeAttachment: 'Remove {{name}}',
  previewFailed: 'Document preview failed',
  parsed: 'Parsed',
  pendingParse: 'Pending',
  ocrParsed: 'Parsed via OCR',
  ocrPending: 'OCR unavailable',

  // ChatInput
  inputPlaceholder: 'Type a message, or drag in images and documents…',
  uploadFile: 'Upload image or file',
  uploading: 'Uploading',
  inputHint: 'Enter to send · Shift + Enter for new line',
  stopGenerating: 'Stop generating',
  sendMessage: 'Send message',
  dropToUpload: 'Release to upload file',
  unsupportedFileType: '{{name}}: this file type is not supported',
  fileSizeExceeded: '{{name}} exceeds the {{limit}} limit',

  // ChatSidebar
  groupToday: 'Today',
  groupLast7Days: 'Last 7 days',
  groupLast30Days: 'Last 30 days',
  groupEarlier: 'Earlier',
  noMessages: 'No messages',
  renameConversation: 'Rename conversation',
  titleRequired: 'Title cannot be empty',
  deleteConversationTitle: 'Delete this conversation?',
  deleteConversationContent: '“{{title}}” and its messages and attachments will be permanently deleted.',
  generating: 'Generating',
  rename: 'Rename',
  manageConversation: 'Manage conversation {{title}}',
  collapseSidebar: 'Collapse sidebar',
  searchSessions: 'Search conversations and messages',
  noSessions: 'No conversations',
  settingsTooltip: 'Model, context and data settings',
  yesterday: 'Yesterday',

  // ChatWindow
  serverValidated: 'Permissions are validated by the server',
  selectKnowledgeBase: 'Select knowledge base',
  allKnowledgeBases: 'All knowledge bases',
  switchModel: 'Switch conversation model',
  disclaimer: 'AI may make mistakes. Verify the execution plan before approvals, financial actions, or permission changes.',

  // CitationList & MarkdownRenderer
  citationsTitle: 'Knowledge sources',
  noContent: '(No content)',
  similarity: 'Similarity {{score}}%',
  viewCitation: 'View citation: {{source}}',

  // MarkdownRenderer code block
  codeCopyFailed: 'Failed to copy code',
  copied: 'Copied',
  copyCode: 'Copy code',

  // MessageItem
  feedbackRecorded: 'Feedback recorded',
  feedbackFailed: 'Failed to submit feedback',
  replyCopied: 'Reply copied',
  replyCopyFailed: 'Failed to copy reply',
  assistantName: 'WorkMate AI',
  you: 'You',
  statusGenerating: 'Generating',
  statusIncomplete: 'Incomplete',
  thinking: 'Thinking...',
  copyReply: 'Copy reply',
  regenerate: 'Regenerate',
  helpful: 'Helpful',
  needImprove: 'Needs improvement',

  // MessageList
  emptyTitle: 'What would you like to work on today?',
  emptyDesc: 'Ask a question directly, or upload images, spreadsheets, or documents as context.',
  retryPrevious: 'Please answer the previous question again.',
  starter: {
    summarize: 'Summarize a document for me',
    analyzeImage: 'Analyze this image',
    writeCode: 'Write a piece of code for me',
    explainError: 'Explain this error',
  },

  // SystemSettingsPage
  settingsTitle: 'AI Workspace Settings',
  saveSettings: 'Save settings',
  settingsSaved: 'Settings saved',
  ocrSettings: 'OCR Recognition',
  apiKeyManaged: 'Managed by server environment variable',
  apiKeyHint: 'The key is never sent to the browser. Configure it via the backend `AI_API_KEY`.',
  model: 'Conversation model',
  selectModelRequired: 'Please select a conversation model',
  maxContextRounds: 'Max context rounds',
  streamOutput: 'Stream output',
  forcePdfOcr: 'Always OCR PDF files',
  forcePdfOcrHint: 'When enabled, all PDFs (including those with a text layer) go through OCR; when disabled, only scanned PDFs are OCRed automatically.',
  baseUrlHint: 'The API base URL is managed by the server AI_BASE_URL to avoid exposing credentials or internal gateway details.',
  dataManagement: 'Data management',
  clearAllTitle: 'Clear all chat history?',
  clearAllContent: 'This will delete all conversations, messages, and attachments for the current account, and cannot be undone.',
  confirmClear: 'Confirm clear',
  clearAllRecords: 'Clear all chat history',

  // aiChatStore error fallbacks
  loadHistoryFailed: 'Failed to load chat history',
  createConversationFailed: 'Failed to create conversation',
  uploadFailed: 'Upload failed',
  defaultAttachmentPrompt: 'Please analyze these attachments.',
  aiReplyFailed: 'AI response failed',
};
