export default {
  // 共享错误文案
  requestFailed: '请求失败',
  networkError: '网络错误',
  unknown: '未知错误',

  oa: {
    serviceUnavailable: '无法连接 OA 服务，请确认后端已经启动',
    requestFailedRetry: '请求失败，请稍后重试',
    statusUnauthorized: '请先登录后再继续操作',
    statusForbidden: '当前账号没有执行该操作的权限',
    statusConflict: '数据状态已变化，请刷新后重试',
    statusTooManyRequests: '请求过于频繁，请稍后重试',
    statusServer: '服务暂时不可用，请稍后重试',
    traceIdSuffix: '（追踪号：{{traceId}}）',
  },

  access: {
    requestFailed: '权限配置请求失败',
  },

  auth: {
    serviceUnavailable: '无法连接认证服务，请确认 8080 后端已经启动',
    serverUnavailable: '认证服务暂不可用，请确认 8080 后端和 Redis 已启动',
    requestFailed: '认证请求失败',
    captchaInvalid: '图形验证码数据不完整，请重新加载',
  },

  chat: {
    attachmentLoadFailed: '附件加载失败',
    markdownLoadFailed: 'Markdown 文档加载失败',
    streamUnavailable: '浏览器无法读取流式响应',
    aiServiceUnavailable: 'AI 服务暂时不可用',
  },

  hr: {
    organizationLoadFailed: '组织架构数据加载失败',
  },

  knowledge: {
    requestFailed: '知识库请求失败',
    uploadFailed: '上传失败，请检查网络连接',
    uploadTimeout: '上传超时，请重试',
  },

  navigation: {
    loadFailed: '导航菜单加载失败',
  },

  notification: {
    requestFailed: '通知请求失败',
  },

  profile: {
    updateFailed: '用户资料更新失败',
    avatarUploadFailed: '头像上传失败',
    avatarDeleteFailed: '头像删除失败',
    wallpaperLoadFailed: '壁纸加载失败',
    wallpaperUploadFailed: '壁纸上传失败',
    wallpaperDeleteFailed: '壁纸删除失败',
  },

  wallpaper: {
    selectImage: '请选择图片文件',
    cropTooLarge: '裁剪后的图片仍然过大，请缩小裁剪范围或选择尺寸更小的图片',
    readImageFailed: '无法读取该图片',
    canvasUnsupported: '当前浏览器不支持壁纸裁剪',
  },
};
