/**
 * 富媒体描述的最小化净化。
 *
 * 商品描述由卖家通过富文本编辑器录入、后端原样存储，
 * 前端渲染前再过滤一次：移除脚本、事件属性与 javascript: 协议，
 * 只保留图文视频所需的标签。对应 NFR-002 / NFR-012 的 XSS 防护。
 */

const DROP_TAGS = new Set([
  'SCRIPT',
  'STYLE',
  'IFRAME',
  'OBJECT',
  'EMBED',
  'LINK',
  'META',
  'FORM',
  'INPUT',
  'BUTTON',
])

const ALLOWED_TAGS = new Set([
  'P',
  'BR',
  'DIV',
  'SPAN',
  'STRONG',
  'B',
  'EM',
  'I',
  'U',
  'S',
  'H1',
  'H2',
  'H3',
  'H4',
  'UL',
  'OL',
  'LI',
  'BLOCKQUOTE',
  'IMG',
  'VIDEO',
  'SOURCE',
  'A',
])

const ALLOWED_ATTRS = new Set(['src', 'alt', 'title', 'controls', 'href', 'target', 'rel'])

function isSafeUrl(value, { allowHttp = false } = {}) {
  if (!value) return false
  const url = String(value).trim()
  if (url.startsWith('/uploads/')) return true
  if (allowHttp && /^https?:\/\//i.test(url)) return true
  return false
}

function cleanNode(node) {
  const children = Array.from(node.childNodes || [])
  children.forEach((child) => {
    if (child.nodeType === Node.COMMENT_NODE) {
      child.remove()
      return
    }
    if (child.nodeType !== Node.ELEMENT_NODE) return

    if (DROP_TAGS.has(child.tagName)) {
      child.remove()
      return
    }

    if (!ALLOWED_TAGS.has(child.tagName)) {
      // 未知标签保留其文本内容
      const parent = child.parentNode
      while (child.firstChild) parent.insertBefore(child.firstChild, child)
      child.remove()
      return
    }

    Array.from(child.attributes || []).forEach((attr) => {
      const name = attr.name.toLowerCase()
      const value = attr.value || ''
      if (!ALLOWED_ATTRS.has(name) || name.startsWith('on')) {
        child.removeAttribute(attr.name)
        return
      }
      if (name === 'href' && !isSafeUrl(value, { allowHttp: true })) {
        child.removeAttribute(attr.name)
      }
      if ((name === 'src') && !isSafeUrl(value, { allowHttp: false })) {
        child.removeAttribute(attr.name)
      }
    })

    if (child.tagName === 'A') {
      child.setAttribute('rel', 'noopener noreferrer')
      child.setAttribute('target', '_blank')
    }

    cleanNode(child)
  })
}

export function sanitizeHtml(html) {
  if (!html) return ''
  if (typeof DOMParser === 'undefined') return ''
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
  const root = doc.body.firstChild
  if (!root) return ''
  cleanNode(root)
  return root.innerHTML
}
