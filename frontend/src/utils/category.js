/** 把后端类别树转换成 el-cascader 的 options */
export function toCascaderOptions(items) {
  return (items || []).map((parent) => ({
    value: parent.id,
    label: parent.name,
    children: (parent.children || []).map((child) => ({
      value: child.id,
      label: child.name,
    })),
  }))
}

/** 在类别树中按 ID 找到「大类 / 小类」名称，用于列表展示 */
export function findCategoryPath(items, categoryId) {
  for (const parent of items || []) {
    if (parent.id === categoryId) return parent.name
    for (const child of parent.children || []) {
      if (child.id === categoryId) return `${parent.name} / ${child.name}`
    }
  }
  return ''
}
