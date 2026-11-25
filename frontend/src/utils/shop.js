/**
 * 挑战 C 领域常量与数据归一化。
 *
 * 页面只依赖归一化后的字段，后端字段名微调时只改这里：
 *   - 购物车：{ productId, quantity, name, price, image, status, statusLabel, stock }
 *   - 收藏：  { productId, name, price, image, status, statusLabel, stock }
 *   - 订单：  { id, order_no, status, statusLabel, total_amount, created_at,
 *              payment, shipment, address, items[], aftersales[] }
 *   - 地址：  { id, receiver, phone, fullAddress, isDefault }
 *   - 支付：  { method, methodLabel, status, statusLabel, amount, paidAt, transactionNo }
 *   - 物流：  { carrierName, trackingNo, status, statusLabel, traces[] }
 *   - 售后：  { id, orderId, itemId, productName, title, images[], status, result }
 */

/**
 * 订单状态码与后端 com.shop.enums.Enums.OrderStatus 对齐（挑战 C）。
 *
 * PAYMENT_PENDING → PAID → SELLER_CONFIRMED → STOCK_READY → SHIPPING → COMPLETED，
 * CANCELLED 为终态。页面判状态一律用这里的常量，不要再写字面量。
 */
export const ORDER_STATUS_LABELS = {
  PAYMENT_PENDING: '待支付',
  PAID: '已支付',
  SELLER_CONFIRMED: '商家确认',
  STOCK_READY: '备货完成',
  SHIPPING: '开始发货',
  COMPLETED: '交易完成',
  CANCELLED: '已取消',
}

export const ORDER_STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'PAYMENT_PENDING', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'SELLER_CONFIRMED', label: '商家确认' },
  { value: 'STOCK_READY', label: '备货完成' },
  { value: 'SHIPPING', label: '开始发货' },
  { value: 'COMPLETED', label: '交易完成' },
  { value: 'CANCELLED', label: '已取消' },
]

/** 订单正向流转的 6 个状态（已取消是终态，不在步骤条里） */
export const ORDER_STEPS = [
  { status: 'PAYMENT_PENDING', label: '待支付' },
  { status: 'PAID', label: '已支付' },
  { status: 'SELLER_CONFIRMED', label: '商家确认' },
  { status: 'STOCK_READY', label: '备货完成' },
  { status: 'SHIPPING', label: '开始发货' },
  { status: 'COMPLETED', label: '交易完成' },
]

/**
 * 商家可推进的下一步；当前已是终态、或还在等客户支付时返回 null。
 *
 * 后端只有单一「推进一步」接口 POST /seller/orders/{id}/advance，
 * 由后端按状态机决定下一状态；「开始发货」还额外需要填写物流信息。
 */
export function getNextOrderAction(status) {
  switch (status) {
    case 'PAID':
      return { label: '确认订单', targetStatus: 'SELLER_CONFIRMED' }
    case 'SELLER_CONFIRMED':
      return { label: '备货完成', targetStatus: 'STOCK_READY' }
    case 'STOCK_READY':
      return { label: '去发货', targetStatus: 'SHIPPING', requiresShipment: true }
    case 'SHIPPING':
      return { label: '交易完成', targetStatus: 'COMPLETED' }
    default:
      return null
  }
}

/** 客户可在「开始发货」前取消（含待支付 / 已支付 / 商家确认 / 备货完成） */
export function canCustomerCancel(status) {
  return ['PAYMENT_PENDING', 'PAID', 'SELLER_CONFIRMED', 'STOCK_READY'].includes(
    status,
  )
}

/** 商家可在「交易完成」前取消；已取消不可重复取消 */
export function canSellerCancel(status) {
  return !['COMPLETED', 'CANCELLED'].includes(status)
}

/** 待支付订单才允许发起支付 */
export function canPayOrder(status) {
  return status === 'PAYMENT_PENDING'
}

/** 交易完成后，客户可对订单内某件商品发起售后（FR-051） */
export function canApplyAfterSale(status) {
  return status === 'COMPLETED'
}

export function orderStatusLabel(order) {
  return order?.status_label || ORDER_STATUS_LABELS[order?.status] || order?.status || '—'
}

/* ---------------- 支付 ---------------- */

export const PAYMENT_METHODS = [
  { value: 'BANK_CARD', label: '银行卡' },
  { value: 'CREDIT_CARD', label: '信用卡' },
  { value: 'ALIPAY', label: '支付宝' },
  { value: 'WECHAT', label: '微信支付' },
]

export const PAYMENT_METHOD_LABELS = Object.fromEntries(
  PAYMENT_METHODS.map((method) => [method.value, method.label]),
)

export const PAYMENT_STATUS_LABELS = {
  UNPAID: '未支付',
  PENDING: '支付中',
  PAID: '已支付',
  FAILED: '支付失败',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
}

/** 支付超时时间：24 小时（TBD-02） */
export const PAYMENT_TIMEOUT_MS = 24 * 60 * 60 * 1000

/* ---------------- 物流 ---------------- */

export const SHIPMENT_STATUS_LABELS = {
  PENDING: '待发货',
  SHIPPED: '已发货',
  IN_TRANSIT: '运输中',
  DELIVERING: '派送中',
  DELIVERED: '已签收',
}

/** 物流状态只能按「发货 → 运输中 → 派送中 → 已签收」单向更新（TBD-09） */
export const SHIPMENT_STATUS_FLOW = [
  'SHIPPED',
  'IN_TRANSIT',
  'DELIVERING',
  'DELIVERED',
]

export const CARRIER_OPTIONS = [
  { value: 'SF', label: '顺丰速运' },
  { value: 'JD', label: '京东物流' },
  { value: 'ZTO', label: '中通快递' },
  { value: 'YTO', label: '圆通速递' },
  { value: 'YD', label: '韵达速递' },
  { value: 'EMS', label: '邮政 EMS' },
  { value: 'OTHER', label: '其他' },
]

export const CARRIER_LABELS = Object.fromEntries(
  CARRIER_OPTIONS.map((carrier) => [carrier.value, carrier.label]),
)

export function nextShipmentStatus(status) {
  const index = SHIPMENT_STATUS_FLOW.indexOf(status)
  if (index < 0 || index >= SHIPMENT_STATUS_FLOW.length - 1) return ''
  return SHIPMENT_STATUS_FLOW[index + 1]
}

/* ---------------- 售后 ---------------- */

export const AFTER_SALE_STATUS_LABELS = {
  PENDING: '待处理',
  NEGOTIATING: '协商中',
  RESOLVED: '已解决',
  REJECTED: '已拒绝',
}

export const AFTER_SALE_STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'PENDING', label: '待处理' },
  { value: 'NEGOTIATING', label: '协商中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'REJECTED', label: '已拒绝' },
]

export const AFTER_SALE_RESULT_LABELS = {
  RETURN: '退货',
  REFUND: '退款',
  EXCHANGE: '换货',
  OTHER: '其他',
}

export const AFTER_SALE_RESULT_OPTIONS = [
  { value: 'RETURN', label: '退货' },
  { value: 'REFUND', label: '退款' },
  { value: 'EXCHANGE', label: '换货' },
  { value: 'OTHER', label: '其他' },
]

/** 售后图片沿用商品图片规则：最多 9 张（TBD-10） */
export const AFTER_SALE_MAX_IMAGES = 9

/* ---------------- 通用解析 ---------------- */

/** 解析后端时间：兼容 "2026-11-11 10:00:00" 与 ISO 字符串 */
export function parseDateTime(value) {
  if (!value) return null
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }
  const text = String(value).trim()
  if (!text) return null
  const normalized = /^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}/.test(text)
    ? text.replace(/\s+/, 'T')
    : text
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 把毫秒差格式化成「还剩 23:59:59」用的 HH:mm:ss */
export function formatCountdown(ms) {
  const total = Math.max(0, Math.floor(Number(ms) / 1000))
  const hours = String(Math.floor(total / 3600)).padStart(2, '0')
  const minutes = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const seconds = String(total % 60).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

/**
 * 待支付订单的支付截止时间 = 下单时间 + 24h。
 * 后端若返回 payment.expire_at，以它为准。
 */
export function paymentDeadline(order) {
  const explicit = parseDateTime(
    order?.payment?.expireAt || order?.payment_expire_at || order?.expire_at,
  )
  if (explicit) return explicit
  const created = parseDateTime(order?.created_at)
  if (!created) return null
  return new Date(created.getTime() + PAYMENT_TIMEOUT_MS)
}

function asArray(value) {
  if (Array.isArray(value)) return value
  if (typeof value === 'string' && value.trim()) {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function asBoolean(value) {
  return value === true || value === 1 || value === '1' || value === 'true'
}

/* ---------------- 购物车 / 收藏 ---------------- */

function productOf(record) {
  return record?.product || {}
}

function productIdOf(record) {
  return (
    record?.product_id ??
    record?.productId ??
    productOf(record).id ??
    record?.id
  )
}

/** 购物车行（后端返回 product 嵌套或扁平字段均可） */
export function normalizeCartLine(item) {
  const product = productOf(item)
  return {
    productId: String(productIdOf(item) ?? ''),
    quantity: Number(item?.quantity) || 1,
    name: product.name || item?.product_name || item?.name || '商品已下架',
    price: Number(product.price ?? item?.price ?? 0),
    image: product.primary_image || item?.product_image || item?.primary_image || '',
    status: product.status || item?.status || '',
    statusLabel: product.status_label || item?.status_label || '',
    stock:
      product.stock === undefined || product.stock === null
        ? item?.stock ?? null
        : Number(product.stock),
    raw: item,
  }
}

/** 收藏项 */
export function normalizeFavorite(item) {
  const product = productOf(item)
  return {
    productId: String(productIdOf(item) ?? ''),
    name: product.name || item?.product_name || item?.name || '商品已下架',
    price: Number(product.price ?? item?.price ?? 0),
    image: product.primary_image || item?.product_image || item?.primary_image || '',
    status: product.status || item?.status || '',
    statusLabel: product.status_label || item?.status_label || '',
    stock:
      product.stock === undefined || product.stock === null
        ? item?.stock ?? null
        : Number(product.stock),
    createdAt: item?.created_at || '',
    raw: item,
  }
}

/** 是否还能加购 / 下单：只有在售且剩余可售 > 0 才算 */
export function isLinePurchasable(line) {
  const statusOk = !line.status || line.status === 'ON_SALE'
  const stockOk = line.stock === null || line.stock === undefined || Number(line.stock) > 0
  return statusOk && stockOk
}

/** 收藏里「商品已下架 / 无货」的提示文案 */
export function favoriteUnavailableText(line) {
  if (line.status && line.status !== 'ON_SALE') {
    return line.statusLabel || '已下架 / 无货'
  }
  if (line.stock !== null && Number(line.stock) <= 0) return '无货'
  return ''
}

/* ---------------- 收货地址 ---------------- */

export function normalizeAddress(address) {
  if (!address) return null

  const receiver = address.receiver || address.consignee || address.name || ''
  const phone = address.phone || address.tel || address.mobile || ''
  const province = address.province || ''
  const city = address.city || ''
  const district = address.district || address.area || ''
  const detail = address.detail || address.detail_address || address.address || ''
  const joined = [province, city, district, detail].filter(Boolean).join('')
  const fullAddress = address.full_address || joined

  return {
    ...address,
    id: String(address.id ?? address.address_id ?? ''),
    receiver,
    phone,
    province,
    city,
    district,
    detail,
    postalCode: address.postal_code || address.postalCode || '',
    isDefault: asBoolean(address.is_default ?? address.default ?? address.isDefault),
    fullAddress,
    display: [receiver, phone, fullAddress].filter(Boolean).join(' '),
  }
}

/* ---------------- 支付 / 物流 / 售后 ---------------- */

export function normalizePayment(record) {
  if (!record) return null

  const method = record.method || record.payment_method || ''
  const status = record.status || record.payment_status || ''
  const amount = Number(record.amount ?? record.pay_amount ?? 0)
  const refundAmount = Number(record.refund_amount ?? 0)

  return {
    ...record,
    method,
    methodLabel: record.method_label || PAYMENT_METHOD_LABELS[method] || method || '—',
    status,
    statusLabel:
      record.status_label || PAYMENT_STATUS_LABELS[status] || status || '未支付',
    amount,
    paidAt: record.paid_at || record.paid_time || '',
    transactionNo: record.transaction_no || record.trade_no || record.tradeNo || '',
    refundStatus: record.refund_status || '',
    refundStatusLabel:
      record.refund_status_label ||
      PAYMENT_STATUS_LABELS[record.refund_status] ||
      record.refund_status ||
      '',
    refundAmount,
    refundedAt: record.refunded_at || record.refund_time || '',
    // FR-045：退款流水/退款时间需可追溯（前端展示用，字段名兼容后端多种命名）
    refundTransactionNo:
      record.refund_transaction_no ||
      record.refund_trade_no ||
      record.refund_no ||
      record.refund_transaction_id ||
      '',
    expireAt: record.expire_at || record.expired_at || '',
  }
}

/**
 * 订单取消原因文案（FR-028 / 3.6 节「取消原因标记」）。
 *
 * 后端挑战 C 尚未定稿字段，这里兼容常见命名；无法判定时回退为通用文案。
 */
export function cancelReasonText(order) {
  if (!order) return '订单已取消'
  const raw =
    order.cancel_reason || order.cancelReason || order.cancellation_reason || ''
  const by = order.cancelled_by || order.cancelledBy || order.cancel_by || ''
  const code = String(raw || by).toUpperCase()
  if (code.includes('TIMEOUT') || code.includes('OVERDUE') || code.includes('EXPIRE')) {
    return '支付超时，系统自动取消'
  }
  if (code.includes('SELLER') || code.includes('MERCHANT')) {
    return '商家取消'
  }
  if (code.includes('CUSTOMER') || code.includes('BUYER')) {
    return '客户取消'
  }
  if (raw) return String(raw)
  if (by) return String(by)
  return '订单已取消'
}

export function normalizeShipment(record) {
  if (!record) return null

  const carrier = record.carrier || record.logistics_company || record.company || ''
  const trackingNo =
    record.tracking_no || record.waybill_no || record.tracking_number || ''
  const status = record.status || record.shipment_status || ''
  const traces = asArray(record.traces || record.tracks || record.events).map(
    (trace) => ({
      ...trace,
      time: trace.time || trace.created_at || trace.updated_at || '',
      text: trace.text || trace.description || trace.content || trace.status_label || '',
    }),
  )

  return {
    ...record,
    carrier,
    carrierName: record.carrier_name || CARRIER_LABELS[carrier] || record.company || carrier || '—',
    trackingNo,
    status,
    statusLabel:
      record.status_label || SHIPMENT_STATUS_LABELS[status] || status || '待发货',
    shippedAt: record.shipped_at || record.shipped_time || '',
    updatedAt: record.updated_at || '',
    traces,
  }
}

export function normalizeAfterSale(record) {
  if (!record) return null

  const item = record.order_item || record.item || {}
  const product = item.product || record.product || {}
  const status = record.status || ''
  const result = record.result || ''

  return {
    ...record,
    id: String(record.id ?? record.after_sale_id ?? ''),
    orderId: String(record.order_id ?? record.orderId ?? ''),
    orderNo: record.order_no || record.orderNo || '',
    itemId: String(
      record.order_item_id ?? record.item_id ?? item.id ?? record.itemId ?? '',
    ),
    productId: String(
      record.product_id ?? item.product_id ?? product.id ?? record.productId ?? '',
    ),
    productName:
      record.product_name ||
      item.product_name ||
      product.name ||
      item.name ||
      '商品',
    productImage:
      record.product_image ||
      item.product_image ||
      product.primary_image ||
      item.primary_image ||
      '',
    title: record.title || record.subject || '',
    description: record.description || record.content || '',
    images: asArray(record.images || record.image_urls || record.attachments),
    status,
    statusLabel:
      record.status_label || AFTER_SALE_STATUS_LABELS[status] || status || '—',
    result,
    resultLabel:
      record.result_label || AFTER_SALE_RESULT_LABELS[result] || result || '',
    resultNote: record.result_note || record.note || record.remark || '',
    refundAmount: Number(record.refund_amount ?? 0),
    // FR-045 / TBD-14：售后退款共用原路退回通道，流水与时间需展示
    refundTransactionNo:
      record.refund_transaction_no ||
      record.refund_trade_no ||
      record.refund_no ||
      '',
    refundedAt: record.refunded_at || record.refund_time || '',
    createdAt: record.created_at || '',
    handledAt: record.handled_at || '',
  }
}

/* ---------------- 订单 ---------------- */

function flatPaymentOf(order) {
  if (!order) return null
  if (
    order.payment_method ||
    order.payment_status ||
    order.paid_at ||
    order.transaction_no
  ) {
    return {
      method: order.payment_method,
      status: order.payment_status,
      amount: order.paid_amount,
      paid_at: order.paid_at,
      transaction_no: order.transaction_no,
      refund_status: order.refund_status,
      refund_amount: order.refund_amount,
      refunded_at: order.refunded_at,
      refund_transaction_no:
        order.refund_transaction_no || order.refund_trade_no || order.refund_no || '',
    }
  }
  return null
}

function flatShipmentOf(order) {
  if (!order) return null
  if (order.carrier || order.tracking_no || order.waybill_no || order.shipment_status) {
    return {
      carrier: order.carrier,
      tracking_no: order.tracking_no || order.waybill_no,
      status: order.shipment_status,
      shipped_at: order.shipped_at,
      traces: order.logistics_traces || order.traces,
    }
  }
  return null
}

export function normalizeOrderItem(item) {
  const product = productOf(item)
  const price = Number(item?.price ?? product.price ?? 0)
  const quantity = Number(item?.quantity ?? 0)
  return {
    ...item,
    itemId: String(item?.order_item_id ?? item?.item_id ?? item?.id ?? ''),
    productId: String(productIdOf(item) ?? ''),
    name: item?.product_name || product.name || item?.name || '商品已下架',
    image: item?.product_image || product.primary_image || item?.primary_image || '',
    price,
    quantity,
    subtotal: Number(item?.subtotal ?? price * quantity),
    aftersale: normalizeAfterSale(
      item?.aftersale || item?.after_sale || item?.latest_aftersale,
    ),
  }
}

export function normalizeOrder(order) {
  if (!order) return null

  const rawItems = order.items || []
  const items = rawItems.map(normalizeOrderItem)
  const aftersales = asArray(order.aftersales || order.after_sales).map(
    normalizeAfterSale,
  )

  // 后端若只在订单级返回售后列表，把它回填到对应订单项，页面上才能逐件展示
  for (const item of items) {
    if (item.aftersale || !item.itemId) continue
    item.aftersale =
      aftersales.find((aftersale) => aftersale.itemId === item.itemId) || null
  }

  return {
    ...order,
    items,
    aftersales,
    total_amount: Number(order.total_amount ?? order.total ?? 0),
    statusLabel: orderStatusLabel(order),
    payment: normalizePayment(order.payment || flatPaymentOf(order)),
    shipment: normalizeShipment(order.shipment || flatShipmentOf(order)),
    address: normalizeAddress(
      order.shipping_address || order.address || order.delivery_address,
    ),
  }
}

/** 订单里的商品件数（数量求和） */
export function orderItemCount(order) {
  return (order?.items || []).reduce(
    (sum, item) => sum + (Number(item.quantity) || 0),
    0,
  )
}

/** 订单卡片上的一行商品摘要 */
export function orderItemsSummary(order) {
  const items = order?.items || []
  if (!items.length) return '—'
  const first = items[0]
  const name = first.name || first.product_name || '商品'
  return items.length === 1
    ? `${name} × ${first.quantity}`
    : `${name} 等 ${items.length} 种商品`
}

/** 当前状态在 6 步流程中的下标；已取消返回 -1 */
export function orderStepIndex(status) {
  return ORDER_STEPS.findIndex((step) => step.status === status)
}
