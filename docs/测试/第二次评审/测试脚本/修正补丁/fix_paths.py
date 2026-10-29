"""修正重组后文件的相对路径引用。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, "第2次评审测试")


def patch(rel, pairs):
    p = os.path.join(D, rel)
    t = open(p, encoding="utf-8").read()
    hit = 0
    for old, new in pairs:
        if old in t:
            t = t.replace(old, new)
            hit += 1
        else:
            print("    未找到:", old[:50])
    open(p, "w", encoding="utf-8").write(t)
    print("  %-30s 修正 %d 处" % (rel, hit))


patch("更新汇总/01-需求更新.md", [
    ("`附件/需求规格说明书-挑战A升级版（原文）.md`", "`../附件/需求规格说明书-挑战A升级版（原文）.md`"),
])

patch("更新汇总/02-前端更新.md", [
    ("`附件/前端构建与部署手册（原文）.md`、`附件/validate.js`、`附件/前端改动.diff`",
     "`../附件/前端构建与部署手册（原文）.md`、`../附件/validate.js`、`../附件/前端改动.diff`"),
    ("位于 `Requirement Analysis/` 目录", "位于仓库 `Requirement Analysis/` 目录"),
])

patch("测试材料/04-本周更新测试结果.md", [
    ("> 测试脚本与历史用例见同目录 `代码/`。",
     "> 测试脚本与历史用例见仓库 `docs/测试/代码/`。"),
])

patch("汇报材料/06-口头汇报稿.md", [
    ("《04-本周更新测试结果.md》", "《../测试材料/04-本周更新测试结果.md》"),
])

print("\n路径修正完成")
