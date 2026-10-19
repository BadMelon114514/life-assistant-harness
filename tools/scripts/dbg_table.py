# -*- coding: utf-8 -*-
import re
import os, sys
os.chdir(os.path.dirname(os.path.abspath(__file__)))
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def splitMdRow(line):
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    parts = s.split("|")
    return [c.strip().replace("**", "").replace("`", "") for c in parts]


def isSep(cells):
    if not cells:
        return False
    for c in cells:
        if c.replace("-", "").replace(":", "").strip():
            return False
    return True


content = open("../../data/knowledge/doc_04861850_202608消费电子报告.md", encoding="utf-8").read()
lines = content.split("\n")
i = 0
tbl_no = 0
while i < len(lines):
    line = lines[i].strip()
    if line.startswith("|"):
        header = splitMdRow(line)
        if len(header) >= 2:
            j = i + 1
            rows = []
            while j < len(lines) and lines[j].strip().startswith("|"):
                cells = splitMdRow(lines[j])
                if not isSep(cells):
                    rows.append(cells)
                j += 1
            if rows:
                tbl_no += 1
                print(f"== 表{tbl_no}: {len(header)}列, {len(rows)}行 表头={header}")
                for r in rows[:2]:
                    print("   ", r)
                # 检查是否有数值列
                numcols = []
                for c in range(len(header)):
                    sample = [r[c] for r in rows if c < len(r) and r[c].strip()]
                    if sample and all(re.match(r"[-+]?\d+(\.\d+)?", v.replace("约", "").replace(",", "").strip()) for v in sample):
                        numcols.append(header[c])
                print(f"   数值列: {numcols}")
            i = j
            continue
    i += 1
