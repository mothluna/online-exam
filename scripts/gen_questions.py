import random
import sys
from openpyxl import Workbook

TOTAL = int(sys.argv[1]) if len(sys.argv) > 1 else 500
DUP = int(sys.argv[2]) if len(sys.argv) > 2 else 20
OUT = sys.argv[3] if len(sys.argv) > 3 else f"questions_{TOTAL}.xlsx"

random.seed(20261126)

HEADERS = ["分类路径", "题型", "题干", "选项A", "选项B", "选项C", "选项D",
           "选项E", "选项F", "正确答案", "解析", "难度", "分值"]

wb = Workbook()
ws = wb.active
ws.title = "题目导入"
ws.append(HEADERS)

UNIQUE = TOTAL - DUP

categories = ["Java 基础/集合", "Java 基础"]
rows = []

for i in range(1, UNIQUE + 1):
    kind = i % 5
    if kind in (0, 1):      # 单选 40%
        rows.append([
            categories[i % 2], "单选", f"第{i}题：以下哪个选项是 Java 的关键字？",
            "class", "String", "main", "system", "", "",
            random.choice(["A", "B"]), f"第{i}题解析", random.choice(["易", "中"]),
            random.choice(["2", "1.5", "3"]),
        ])
    elif kind == 2:         # 多选 20%
        rows.append([
            categories[i % 2], "多选", f"第{i}题：以下哪些属于 JVM 运行时数据区？",
            "堆", "虚拟机栈", "磁盘", "方法区", "", "",
            "A,B,D", f"第{i}题解析", "中", "3",
        ])
    elif kind == 3:         # 判断 20%
        rows.append([
            categories[i % 2], "判断", f"第{i}题：String 在 Java 中是不可变类。",
            "", "", "", "", "", "",
            random.choice(["正确", "错误"]), f"第{i}题解析", "易", "1",
        ])
    else:                   # 填空 20%
        rows.append([
            categories[i % 2], "填空", f"第{i}题：Java 中所有类的父类是____。",
            "", "", "", "", "", "",
            "Object", f"第{i}题解析", "易", "2",
        ])

# 追加 20 条重复题干（内容一致，仅前后加空格），应全部被拦截
for j in range(DUP):
    base = rows[j]
    dup = list(base)
    dup[2] = "  " + base[2] + "  "
    rows.append(dup)

for r in rows:
    ws.append(r)

wb.save(OUT)
print(f"generated {OUT}: {len(rows)} rows ({UNIQUE} unique + {DUP} duplicate)")
