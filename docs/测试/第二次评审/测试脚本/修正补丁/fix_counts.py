"""修正 Java 后端的文件数与接口数（实际 45 个文件 / 41 个 Java 源文件 / 39 个接口）。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, "第2次评审测试")

FIX = [
    ("汇报材料/06-口头汇报稿.md", [
        ("`backend-java`——44 个文件、30 个接口，Spring Boot + SQLite。",
         "`backend-java`——45 个文件（其中 41 个 Java 源文件）、39 个接口，Spring Boot + SQLite。"),
        ("59 / 59 / 100%、5 个问题、14 处出错、30 个接口",
         "59 / 59 / 100%、5 个问题、15 处出错、39 个接口"),
    ]),
    ("README.md", [
        ("**新增整套 Java 后端** `backend-java`（44 个文件、30 个接口）",
         "**新增整套 Java 后端** `backend-java`（45 个文件 / 41 个 Java 源文件、39 个接口）"),
        ("过程中出现 **14 处错误**", "过程中出现 **15 处错误**"),
    ]),
    ("测试材料/08-挑战A新增功能测试报告.md", [
        ("| 被测后端 | `backend-java`（44 个 Java 文件，30 个 REST 接口） |",
         "| 被测后端 | `backend-java`（45 个文件 / 41 个 Java 源文件，39 个 REST 接口） |"),
    ]),
    ("过程记录/AI出错与修正记录.md", [
        ("共 **14 处出错**", "共 **15 处出错**"),
        ("其余 11 处为工具或环境问题", "其余 12 处为工具或环境问题"),
    ]),
]

for rel, pairs in FIX:
    p = os.path.join(D, rel)
    t = open(p, encoding="utf-8").read()
    n = 0
    for old, new in pairs:
        if old in t:
            t = t.replace(old, new)
            n += 1
        else:
            print("  [未找到] %s :: %s" % (rel, old[:45]))
    open(p, "w", encoding="utf-8").write(t)
    print("  %-36s 修正 %d 处" % (rel, n))

print("\n数字修正完成")
