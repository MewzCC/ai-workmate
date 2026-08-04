export default {
  title: '个人设置',
  okText: '保存资料',
  avatar: {
    select: '选择头像',
    remove: '移除',
    hint: '支持 JPG、PNG、WebP，最大 2MB',
    invalidType: '头像仅支持 JPG、PNG 或 WebP',
    tooLarge: '头像大小不能超过 {{max}}',
  },
  field: {
    name: '姓名',
    namePlaceholder: '请输入姓名',
    email: '企业邮箱',
  },
  validation: {
    nameRequired: '请输入姓名',
    nameTooLong: '姓名不能超过 {{max}} 个字符',
  },
  message: {
    profileUpdated: '个人资料已更新',
    profileUpdateFailed: '个人资料更新失败',
    avatarRemoved: '头像已移除',
    avatarRemoveFailed: '头像移除失败',
  },
  crop: {
    title: '裁剪壁纸',
    apply: '应用裁剪',
    hint: '拖动图片调整位置，通过下方控件调整大小、方向和裁剪比例。',
    ratio: '裁剪比例',
    size: '图片大小',
    rotation: '旋转角度',
    rotateLeft: '向左旋转',
    rotateRight: '向右旋转',
  },
};
