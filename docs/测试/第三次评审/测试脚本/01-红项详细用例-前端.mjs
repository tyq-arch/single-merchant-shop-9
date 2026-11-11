// 读取 detail_cases.json，对每个输入调用当前 validate.js，输出前端实际结果
import fs from 'node:fs'
import { validatePhone, validateSellerPassword }
  from '../../_work/shop9/frontend/src/utils/validate.js'

const ROOT = '../'
const src = JSON.parse(fs.readFileSync(new URL('./红项用例定义.json', import.meta.url), 'utf8'))

const out = { phone: [], seller: [] }

for (const [id, desc, input] of src.phone) {
  const msg = validatePhone(input)
  out.phone.push({ id, desc, input, pass: msg === '', msg: msg || '（无提示，通过）' })
}

for (const [id, desc, input] of src.seller) {
  let v = input
  if (v === '__LONG128__') v = 'Ab1!' + 'x'.repeat(124)
  if (v === '__LONG129__') v = 'Ab1!' + 'x'.repeat(125)
  const msg = validateSellerPassword(v)
  out.seller.push({ id, desc, input: v === input ? input : `${v.slice(0, 4)}…（共 ${v.length} 位）`,
                    pass: msg === '', msg: msg || '（无提示，通过）', rawLen: v.length })
}

console.log(JSON.stringify(out, null, 2))
