"""验证问题1的修复：删掉重复的 queueHead 后，测试代码能否编译通过。

流程：备份 -> 删除第二处定义 -> mvn test-compile -> 还原文件
"""
import re
import shutil
import subprocess
import sys

P = r"C:\Users\33857\Desktop\软件工程实践作业\_work\shop9\backend-java\src\test\java\com\shop\BackendIntegrationTest.java"
BAK = P + ".bak"
SETTINGS = r"C:\Users\33857\Desktop\软件工程实践作业\_runtime\maven-settings.xml"
POM_DIR = r"C:\Users\33857\Desktop\软件工程实践作业\_work\shop9\backend-java"

shutil.copyfile(P, BAK)
try:
    lines = open(P, encoding="utf-8").read().splitlines(keepends=True)
    # 定位所有定义行
    idx = [i for i, l in enumerate(lines) if "private String queueHead" in l]
    print("找到 queueHead 定义行（1-based）:", [i + 1 for i in idx])
    assert len(idx) == 2, "预期 2 处定义"

    start = idx[1]
    # 往回找注释块开头
    while start > 0 and (lines[start - 1].strip().startswith("*")
                         or lines[start - 1].strip().startswith("/**")
                         or lines[start - 1].strip() == "*/"):
        start -= 1
    # 往后找方法结束（缩进 4 空格的 }）
    end = idx[1]
    while end < len(lines) and lines[end].rstrip() != "    }":
        end += 1
    print("将删除第 %d ~ %d 行：" % (start + 1, end + 1))
    for i in range(start, end + 1):
        print("   - " + lines[i].rstrip())

    del lines[start:end + 2]  # 连同后面的空行
    open(P, "w", encoding="utf-8").write("".join(lines))
    print("\n已删除重复定义，剩余 queueHead 定义数:",
          sum(1 for l in open(P, encoding="utf-8") if "private String queueHead" in l))

    print("\n=== 运行 mvn test-compile ===")
    r = subprocess.run(
        ["mvn", "-B", "-q", "-s", SETTINGS, "test-compile"],
        cwd=POM_DIR, capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (r.stdout or "") + (r.stderr or "")
    print("退出码:", r.returncode)
    for line in out.splitlines():
        if any(k in line for k in ("ERROR", "BUILD", "Compiling", "reference")):
            print("  " + line.strip()[:160])
    if r.returncode == 0:
        print("\n结果：删除重复方法后，测试代码编译通过 ✔")
    else:
        print("\n结果：仍然编译失败 ✘")
finally:
    shutil.move(BAK, P)
    print("\n源码已还原:", sum(1 for l in open(P, encoding="utf-8") if "private String queueHead" in l), "处定义")
