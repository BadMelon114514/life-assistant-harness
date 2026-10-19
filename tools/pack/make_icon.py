# -*- coding: utf-8 -*-
"""生成应用图标 icon.ico（蓝色圆角方块 + 白色 F 字标，多尺寸）。"""
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "dist", "icon.ico")
S = 256
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([10, 10, S - 10, S - 10], radius=58, fill=(65, 118, 230, 255))
# 顶部高光带（轻立体感）
d.rounded_rectangle([10, 10, S - 10, 118], radius=58, fill=(86, 136, 240, 255))
d.rectangle([10, 70, S - 10, 118], fill=(86, 136, 240, 255))
font = ImageFont.truetype(r"C:\Windows\Fonts\arialbd.ttf", 158)
bbox = d.textbbox((0, 0), "F", font=font)
w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
d.text(((S - w) / 2 - bbox[0], (S - h) / 2 - bbox[1] - 4), "F", font=font, fill=(255, 255, 255, 255))
os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT, sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print("icon written:", os.path.abspath(OUT))
