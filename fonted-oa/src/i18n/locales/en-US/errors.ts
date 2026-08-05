export default {
  // Shared error messages
  requestFailed: 'Request failed',
  networkError: 'Network error',
  unknown: 'Unknown error',

  oa: {
    serviceUnavailable: 'Unable to reach the OA service. Please make sure the backend is running.',
    requestFailedRetry: 'Request failed, please try again later.',
    statusUnauthorized: 'Please sign in first to continue.',
    statusForbidden: 'Your account does not have permission to perform this action.',
    statusConflict: 'The data has changed, please refresh and try again.',
    statusTooManyRequests: 'Too many requests, please try again later.',
    statusServer: 'The service is temporarily unavailable, please try again later.',
    traceIdSuffix: ' (Trace ID: {{traceId}})',
  },

  access: {
    requestFailed: 'Access control request failed.',
  },

  auth: {
    serviceUnavailable: 'Unable to reach the authentication service. Please make sure the 8080 backend is running.',
    serverUnavailable: 'Authentication service is unavailable. Please make sure the 8080 backend and Redis are running.',
    requestFailed: 'Authentication request failed.',
    captchaInvalid: 'Captcha data is incomplete, please reload.',
  },

  chat: {
    attachmentLoadFailed: 'Failed to load attachment.',
    markdownLoadFailed: 'Failed to load Markdown document.',
    streamUnavailable: 'The browser cannot read the streaming response.',
    aiServiceUnavailable: 'AI service is temporarily unavailable.',
    ocrSettingsLoadFailed: 'Failed to load OCR settings.',
    ocrSettingsSaveFailed: 'Failed to save OCR settings.',
  },

  hr: {
    organizationLoadFailed: 'Failed to load organization data.',
  },

  knowledge: {
    requestFailed: 'Knowledge base request failed.',
    uploadFailed: 'Upload failed, please check your network connection.',
    uploadTimeout: 'Upload timed out, please try again.',
  },

  navigation: {
    loadFailed: 'Failed to load navigation menu.',
  },

  notification: {
    requestFailed: 'Notification request failed.',
  },

  profile: {
    updateFailed: 'Failed to update user profile.',
    avatarUploadFailed: 'Failed to upload avatar.',
    avatarDeleteFailed: 'Failed to delete avatar.',
    wallpaperLoadFailed: 'Failed to load wallpaper.',
    wallpaperUploadFailed: 'Failed to upload wallpaper.',
    wallpaperDeleteFailed: 'Failed to delete wallpaper.',
  },

  wallpaper: {
    selectImage: 'Please select an image file',
    cropTooLarge: 'The cropped image is still too large. Reduce the crop area or pick a smaller image.',
    readImageFailed: 'Unable to read this image',
    canvasUnsupported: 'Your browser does not support wallpaper cropping',
  },
};
