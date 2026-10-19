# -*- coding: utf-8 -*-
"""验证生活助理 Harness 主题文案"""
import urllib.request

def p(s=""):
    try:
        print(s)
    except UnicodeEncodeError:
        try:
            print(s.encode("gbk", errors="replace").decode("gbk"))
        except Exception:
            print(repr(s))

BASE = "http://127.0.0.1:8080"
d = urllib.request.urlopen(BASE + "/index.html", timeout=10).read().decode("utf-8")
p("title: " + d.split("<title>")[1].split("</title>")[0])
p("logo 生活助理: " + str("生活助理 Harness" in d))
p("残留 电商: " + str("电商" in d))
p("残留 个人生活助理: " + str("个人生活助理" in d))
d2 = urllib.request.urlopen(BASE + "/app.js", timeout=10).read().decode("utf-8")
p("欢迎语 生活助理: " + str("我是你的生活助理" in d2))
p("h1 生活助理: " + str("生活助理</h1>" in d2))
p("建议词 天气热点: " + str("获取天气 今日热点" in d2))
p("app.js 残留 电商: " + str("电商" in d2))
ok = ("个人生活助理" not in d and "个人生活助理" not in d2 and "生活助理 Harness" in d
      and "我是你的生活助理" in d2 and "电商" not in d and "电商" not in d2)
p("===== " + ("PASS - 品牌已统一为 生活助理 Harness" if ok else "FAIL") + " =====")
