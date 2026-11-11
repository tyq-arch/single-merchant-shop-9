// 用 @vue/compiler-sfc 逐个编译 .vue，验证语法与模板（不 spawn 子进程）
import fs from 'node:fs'
import path from 'node:path'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'

const ROOT = path.resolve('src')
const files = []
function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name)
    if (e.isDirectory()) walk(p)
    else if (e.name.endsWith('.vue')) files.push(p)
  }
}
walk(ROOT)
files.sort()

let ok = 0
const bad = []
for (const f of files) {
  const rel = path.relative(process.cwd(), f).replace(/\\/g, '/')
  try {
    const src = fs.readFileSync(f, 'utf8')
    const { descriptor, errors } = parse(src, { filename: f })
    if (errors && errors.length) throw new Error(errors[0].message || String(errors[0]))
    const id = rel.replace(/[^a-z0-9]/gi, '')
    if (descriptor.script || descriptor.scriptSetup) {
      compileScript(descriptor, { id })
    }
    if (descriptor.template) {
      const r = compileTemplate({
        source: descriptor.template.content,
        filename: f,
        id,
        compilerOptions: { mode: 'module' },
      })
      if (r.errors && r.errors.length) {
        throw new Error(typeof r.errors[0] === 'string' ? r.errors[0] : r.errors[0].message)
      }
    }
    ok++
  } catch (e) {
    bad.push([rel, String(e.message).split('\n')[0].slice(0, 160)])
  }
}

console.log(JSON.stringify({ total: files.length, ok, failed: bad.length, bad }, null, 2))
