import http from './http'

/** 允许的图片扩展名与大小上限，与后端 NFR-012 约束一致 */
export const ALLOWED_IMAGE_TYPES = ['jpg', 'jpeg', 'png', 'gif', 'webp']
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024

/**
 * 前端预校验图片，通过返回 null，不通过返回中文错误文案。
 * 仅用于改善体验，最终校验以后端为准。
 */
export function validateImage(file) {
  if (!file) return '请选择图片文件'
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!ALLOWED_IMAGE_TYPES.includes(ext)) {
    return `图片格式不支持，仅允许 ${ALLOWED_IMAGE_TYPES.join(' / ')}`
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return '图片大小超过 5MB 限制'
  }
  return null
}

/** 上传商品主图，返回 { url, original_name, size }；url 可直接作为商品 image_url */
export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/files/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
