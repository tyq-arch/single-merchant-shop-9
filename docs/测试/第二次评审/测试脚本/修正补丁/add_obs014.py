"""补充本周新发现的观察项 OBS-014（前后端手机号校验不一致），并同步计数 13 -> 14。"""
import ast
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
R = os.path.join(ROOT, "_runtime")

OBS014 = '''    {
        "id": "OBS-014", "level": "中", "module": "输入校验",
        "title": "前后端手机号校验规则不一致",
        "desc": "前端已将联系电话收紧为「11 位大陆手机号」（1 开头、第二位 3~9，"
                "见 frontend/src/utils/validate.js 的 PHONE_RE）；后端 schemas.py 仍为"
                "「5~20 位，允许 + - 空格」（_PHONE_RE）。同一号码如 +8613800000000、"
                "138-0000-0000、12345 会被前端拒绝而被后端接受。",
        "evidence": "代码核对 frontend/src/utils/validate.js 与 backend/app/schemas.py；"
                    "前端输入框已限制 maxlength=11 且 inputmode=numeric，界面层无法提交"
                    "非数字号码，但接口层仍是宽松校验（直接调用 API 可绕过）。",
        "advice": "二选一：①后端同步收紧为 11 位大陆手机号规则，并在需求中明确电话格式；"
                  "②若业务允许座机或国际号码，则放宽前端校验。避免界面与接口两层规则长期不一致。",
    },
'''


def patch(relpath, pairs, is_py=False):
    p = os.path.join(ROOT, relpath)
    t = open(p, encoding="utf-8").read()
    for old, new in pairs:
        assert old in t, "[%s] 未找到锚点: %s" % (os.path.basename(relpath), old[:70])
        t = t.replace(old, new, 1)
    open(p, "w", encoding="utf-8").write(t)
    if is_py:
        ast.parse(t)
    print("  %-40s %d 处" % (os.path.basename(relpath), len(pairs)))


# 1. testdata.py：在 OBSERVATIONS 末尾追加 OBS-014
t = open(os.path.join(R, "testdata.py"), encoding="utf-8").read()
i = t.index('"id": "OBS-013"')
j = t.index("    },\n]    ", i) if "    },\n]    " in t[i:] else None
# 找到 OBS-013 所在字典的结尾 "    },\n]"
seg_end = t.index("    },\n]", i) + len("    },\n")
t = t[:seg_end] + OBS014 + t[seg_end:]
open(os.path.join(R, "testdata.py"), "w", encoding="utf-8").write(t)
ast.parse(t)
print("  %-40s 追加 OBS-014" % "testdata.py")

# 2. 计数 13 -> 14
patch("_runtime/build_code_dir.py", [
    ("- 确认缺陷 **2 个**（BUG-001 中级、BUG-002 低级），观察项 **13 项**",
     "- 确认缺陷 **2 个**（BUG-001 中级、BUG-002 低级），观察项 **14 项**"),
    ("> 由此新发现 3 处「需求与实现不一致」（见 `../03-BUG记录与汇总表.xlsx` 的 OBS-011 ~ OBS-013）。",
     "> 由此新发现 3 处「需求与实现不一致」（见 `../03-BUG记录与汇总表.xlsx` 的 OBS-011 ~ OBS-013）；\n"
     "> 第 2 次评审周期又新增 OBS-014（前后端手机号校验不一致）。"),
])

patch("缪茂锦-测试交付物/06-阶段性评审报告素材-缪茂锦.md", [
    ("达成：缺陷 2 个、观察项 13 项 |", "达成：缺陷 2 个、观察项 14 项 |"),
    ("- 观察项与改进建议：**13 项**", "- 观察项与改进建议：**14 项**"),
    ("| 5 | 汇总观察项与改进建议 13 项：",
     "| 5 | 汇总观察项与改进建议 14 项："),
])

print("\nOBS-014 已并入，计数同步为 14 项")
