// 前端手机号校验补跑：直接调用当前 validate.js 的 validatePhone
import { validatePhone, PHONE_RE }
  from '../../_work/shop9/frontend/src/utils/validate.js'

const cases = [
  ['TC-P01', '', '电话为空'],
  ['TC-P02', '   ', '全空格'],
  ['TC-P03', '13800000001a', '含字母'],
  ['TC-P04', '138-0000-0000', '含连字符'],
  ['TC-P05', '138 0000 0000', '含空格'],
  ['TC-P06', '+8613800000000', '带国家码'],
  ['TC-P07', '12345', '5 位短号'],
  ['TC-P08', '1380000000', '10 位'],
  ['TC-P09', '138000000000', '12 位'],
  ['TC-P10', '23800000000', '2 开头'],
  ['TC-P11', '12800000000', '第二位为 2'],
  ['TC-P12', '10800000000', '第二位为 0'],
  ['TC-P13', '13800000000', '合法 13x'],
  ['TC-P14', '19900000000', '合法 19x'],
  ['TC-P15', '  13800000000  ', '首尾空格'],
]

const rows = cases.map(([id, input, desc]) => {
  const msg = validatePhone(input)
  return { id, input, desc, pass: msg === '', msg: msg || '（无提示，通过）' }
})

console.log(JSON.stringify({ rule: String(PHONE_RE), rows }, null, 2))
