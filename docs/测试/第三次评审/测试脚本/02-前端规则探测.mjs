// 第 3 次评审 · 前端校验规则探测（当前 validate.js）
import { validatePhone, validateCustomerPassword, validateSellerPassword,
         PASSWORD_MIN, PASSWORD_RULE_TEXT }
  from '../../_work/shop9/frontend/src/utils/validate.js'

const phones = [
  ['H01', '13800000000'], ['H02', '19900000000'], ['H03', '01012345678'],
  ['H04', '12345678'], ['H05', '1234567'], ['H06', '138-0000-0000'],
  ['H07', '138 0000 0000'], ['H08', '+8613800000000'], ['H09', '12345'],
  ['H10', '23800000000'], ['H11', 'abcdefghijk'], ['H12', ''],
]

const pwds = [
  ['P01', 'pass1234'], ['P02', 'Ab1!ab'], ['P03', 'Ab1!xyz'],
  ['P04', 'abcdefgh'], ['P05', 'Abcd1234'], ['P06', 'Pass1234!'],
]

const sellers = [['S01', 'abcdefgh'], ['S02', 'Abcd1234'], ['S03', 'Ab1!ab']]

const out = {
  passwordMin: PASSWORD_MIN,
  passwordRuleText: PASSWORD_RULE_TEXT,
  phone: phones.map(([id, v]) => ({ id, input: v, msg: validatePhone(v), ok: validatePhone(v) === '' })),
  customerPwd: pwds.map(([id, v]) => ({ id, input: v, msg: validateCustomerPassword(v), ok: validateCustomerPassword(v) === '' })),
  sellerPwd: sellers.map(([id, v]) => ({ id, input: v, msg: validateSellerPassword(v), ok: validateSellerPassword(v) === '' })),
}

console.log(JSON.stringify(out, null, 2))
