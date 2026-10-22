import http from './http'

/**
 * 文件上传规则，与后端 AppConfig 保持一致：
 *   - 商品图：仅 jpg / jpeg / png，单张 ≤5MB
 *   - 富媒体：图片 jpg/jpeg/png/gif/webp ≤5MB；视频 mp4/webm ≤50MB
 */
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024
export const MAX_MEDIA_IMAGE_BYTES = 5 * 1024 * 1024
export const MAX_MEDIA_VIDEO_BYTES = 50 * 1024 * 1024
export const MAX_PRODUCT_IMAGES = 9

const IMAGE_EXTS = ['jpg', 'jpeg', 'png']
const MEDIA_IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'gif', 'webp']
const VIDEO_EXTS = ['mp4', 'webm']

export function fileExtension(name) {
  return String(name || '').split('.').pop()?.toLowerCase() || ''
}

/** 商品图预校验，通过返回 null */
export function validateImage(file) {
  if (!file) return '请选择图片文件'
  if (!IMAGE_EXTS.includes(fileExtension(file.name))) {
    return '商品图片仅支持 JPG / PNG 格式'
  }
  if (file.size > MAX_IMAGE_BYTES) return '图片大小不能超过 5MB'
  return null
}

/** 富媒体素材预校验，通过返回 null */
export function validateMedia(file) {
  if (!file) return '请选择文件'
  const ext = fileExtension(file.name)
  if (VIDEO_EXTS.includes(ext)) {
    if (file.size > MAX_MEDIA_VIDEO_BYTES) return '视频大小不能超过 50MB'
    return null
  }
  if (MEDIA_IMAGE_EXTS.includes(ext)) {
    if (file.size > MAX_MEDIA_IMAGE_BYTES) return '图片大小不能超过 5MB'
    return null
  }
  return '仅支持 jpg / png / gif / webp 图片或 mp4 / webm 视频'
}

/** 上传商品图，返回 { url, original_name, size, media_type } */
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/files/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

/** 上传富媒体描述素材（图片或视频），返回同上 */
export function uploadMedia(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/files/media', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}
