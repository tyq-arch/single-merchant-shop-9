"""删除 BackendIntegrationTest.java 中重复的 queueHead（第二处），供验证编译用。
不调用任何子进程，只改文件。验证完用 git checkout 还原。
"""
import os
import shutil

P = r"C:\Users\33857\Desktop\软件工程实践作业\_work\shop9\backend-java\src\test\java\com\shop\BackendIntegrationTest.java"
BAK = P + ".bak"

if os.path.exists(BAK):
    shutil.copyfile(BAK, P)          # 从备份恢复，保证幂等
else:
    shutil.copyfile(P, BAK)

lines = open(P, encoding="utf-8").read().splitlines(keepends=True)
idx = [i for i, l in enumerate(lines) if "private String queueHead" in l]
print("定义行:", [i + 1 for i in idx])
if len(idx) != 2:
    print("当前不是 2 处定义，跳过")
    raise SystemExit(0)

start = idx[1]
while start > 0 and (lines[start - 1].strip().startswith("*")
                     or lines[start - 1].strip().startswith("/**")
                     or lines[start - 1].strip() == "*/"):
    start -= 1
end = idx[1]
while end < len(lines) and lines[end].rstrip() != "    }":
    end += 1

print("删除第 %d ~ %d 行（含方法结束大括号）" % (start + 1, end + 1))
del lines[start:end + 1]              # 只删到方法自己的 }，保留类的 }
open(P, "w", encoding="utf-8").write("".join(lines))

txt = open(P, encoding="utf-8").read()
print("剩余定义数:", txt.count("private String queueHead"))
print("文件末尾 3 行:", [l.rstrip() for l in txt.splitlines()[-3:]])
