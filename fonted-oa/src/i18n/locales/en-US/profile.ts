export default {
  title: 'Profile settings',
  okText: 'Save profile',
  avatar: {
    select: 'Select avatar',
    remove: 'Remove',
    hint: 'Supports JPG, PNG, WebP. Max 2MB.',
    invalidType: 'Avatars only support JPG, PNG, or WebP',
    tooLarge: 'Avatar size cannot exceed {{max}}',
  },
  field: {
    name: 'Name',
    namePlaceholder: 'Please enter your name',
    email: 'Enterprise email',
  },
  validation: {
    nameRequired: 'Please enter your name',
    nameTooLong: 'Name cannot exceed {{max}} characters',
  },
  message: {
    profileUpdated: 'Profile updated',
    profileUpdateFailed: 'Failed to update profile',
    avatarRemoved: 'Avatar removed',
    avatarRemoveFailed: 'Failed to remove avatar',
  },
  crop: {
    title: 'Crop wallpaper',
    apply: 'Apply crop',
    hint: 'Drag the image to adjust its position, and use the controls below to adjust size, orientation, and crop ratio.',
    ratio: 'Crop ratio',
    size: 'Image size',
    rotation: 'Rotation',
    rotateLeft: 'Rotate left',
    rotateRight: 'Rotate right',
  },
};
