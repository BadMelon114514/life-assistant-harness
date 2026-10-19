# -*- coding: utf-8 -*-
"""PyInstaller 入口：把上下文存储服务打包为单文件 exe。"""
import os
import sys

if getattr(sys, "frozen", False):
    sys.path.insert(0, getattr(sys, "_MEIPASS", os.path.dirname(sys.executable)))

from context_store.server import main  # noqa: E402

if __name__ == "__main__":
    main()
