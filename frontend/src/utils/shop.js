/**
 * 挑战 B 领域常量与数据归一化。
 *
 * 页面只依赖归一化后的字段，后端字段名微调时只改这里：
 *   - 购物车：{ productId, quantity, name, price, image, status, statusLabel, stock }
 *   - 收藏：  { productId, name, price, image, status, statusLabel, stock }
 *   - 订单：  { id, order_no, status, statusLabel, total_amount, created_at, items[] }
 */

/**
 * 订单状态码与后端 com.shop.enums.Enums.OrderStatus 完全一致：
 * CUSTOMER_PLACED → SELLER_CONFIRMED → STOCK_READY → SHIPPING → COMPLETED，
 * CANCELLED 为终态。页面判状态一律用这里的常量，不要再写字面量。
 */
export const ORDER_STATUS_LABELS = {
  CUSTOMER_PLACED: '客户下单',
  SELLER_CONFIRMED: '商家确认',
  STOCK_READY: '备货完成',
  SHIPPING: '开始发货',
  COMPLETED: '交易完成',
  CANCELLED: '已取消',
}

export const ORDER_STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'CUSTOMER_PLACED', label: '客户下单' },
  { value: 'SELLER_CONFIRMED', label: '商家确认' },
  { value: 'STOCK_READY', label: '备货完成' },
  { value: 'SHIPPING', label: '开始发货' },
  { value: 'COMPLETED', label: '交易完成' },
  { value: 'CANCELLED', label: '已取消' },
]

/** 订单正向流转的 5 个状态（已取消是终态，不在步骤条里） */
export const ORDER_STEPS = [
  { status: 'CUSTOMER_PLACED', label: '客户下单' },
  { status: 'SELLER_CONFIRMED', label: '商家确认' },
  { status: 'STOCK_READY', label: '备货完成' },
  { status: 'SHIPPING', label: '开始发货' },
  { status: 'COMPLETED', label: '交易完成' },
]

/**
 * 商家可推进的下一步；当前已是终态时返回 null。
 * 后端只有单一「推进一步」接口 POST /seller/orders/{id}/advance，
 * 由后端按状态机决定下一状态，前端只负责展示与确认。
 */
export function getNextOrderAction(status) {
  switch (status) {
    case 'CUSTOMER_PLACED':
      return { label: '确认订单', targetStatus: 'SELLER_CONFIRMED' }
    case 'SELLER_CONFIRMED':
      return { label: '备货完成', targetStatus: 'STOCK_READY' }
    case 'STOCK_READY':
      return { label: '开始发货', targetStatus: 'SHIPPING' }
    case 'SHIPPING':
      return { label: '交易完成', targetStatus: 'COMPLETED' }
    default:
      return null
  }
}

/** 客户可在「开始发货」前取消（含客户下单/商家确认/备货完成） */
export function canCustomerCancel(status) {
  return ['CUSTOMER_PLACED', 'SELLER_CONFIRMED', 'STOCK_READY'].includes(status)
}

/** 商家可在「交易完成」前取消；已取消不可重复取消 */
export function canSellerCancel(status) {
  return !['COMPLETED', 'CANCELLED'].includes(status)
}

export function orderStatusLabel(order) {
  return order?.status_label || ORDER_STATUS_LABELS[order?.status] || order?.status || '—'
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

/* ---------------- 订单 ---------------- */

export function normalizeOrderItem(item) {
  const product = productOf(item)
  const price = Number(item?.price ?? product.price ?? 0)
  const quantity = Number(item?.quantity ?? 0)
  return {
    ...item,
    productId: String(productIdOf(item) ?? ''),
    name: item?.product_name || product.name || item?.name || '商品已下架',
    image: item?.product_image || product.primary_image || item?.primary_image || '',
    price,
    quantity,
    subtotal: Number(item?.subtotal ?? price * quantity),
  }
}

export function normalizeOrder(order) {
  if (!order) return null
  return {
    ...order,
    items: (order.items || []).map(normalizeOrderItem),
    total_amount: Number(order.total_amount ?? order.total ?? 0),
    statusLabel: orderStatusLabel(order),
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

/** 当前状态在 5 步流程中的下标；已取消返回 -1 */
export function orderStepIndex(status) {
  return ORDER_STEPS.findIndex((step) => step.status === status)
}
