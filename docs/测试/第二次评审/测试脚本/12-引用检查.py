"""检查第2次评审测试文件夹内的相对路径引用是否都存在。"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
D = os.path.join(ROOT, "第2次评审测试")

out = []
bad = 0
for dirpath, _, files in os.walk(D):
    for fn in files:
        if not fn.endswith(".md"):
            continue
        p = os.path.join(dirpath, fn)
        rel_dir = os.path.relpath(dirpath, D)
        text = open(p, encoding="utf-8").read()
        # 找反引号内以 ../ 或 目录/ 开头的路径
        for m in re.findall(r"`((?:\.\./)?[\u4e00-\u9fa5A-Za-z0-9_\-/\.]+\.(?:md|xlsx|js|diff|py|ps1))`", text):
            if m.startswith("_runtime") or "/" not in m and not m.startswith(".."):
                pass
            target = os.path.normpath(os.path.join(dirpath, m))
            if not os.path.exists(target):
                out.append("  [缺失] %-34s -> %s" % (os.path.join(rel_dir, fn), m))
                bad += 1
            else:
                out.append("  [OK]   %-34s -> %s" % (os.path.join(rel_dir, fn), m))

txt = "\n".join(sorted(set(out)))
print(txt if txt else "  未发现相对路径引用")
print("\n缺失引用数:", bad)
open(os.path.join(ROOT, "_runtime", "ref-check.txt"), "w", encoding="utf-8").write(txt)
