# -*- coding: utf-8 -*-
"""生成 iOS AppIcon（1024x1024，品牌蓝底 + 白色「生」字）。"""
import os
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "ios", "LifeAssistant", "Assets.xcassets", "AppIcon.appiconset")
OUT = os.path.abspath(OUT)
os.makedirs(OUT, exist_ok=True)

size = 1024
img = Image.new("RGB", (size, size), (0x41, 0x76, 0xE6))
draw = ImageDraw.Draw(img)
font = None
for path in [r"C:\Windows\Fonts\msyhbd.ttc", r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"]:
    if os.path.exists(path):
        try:
            font = ImageFont.truetype(path, 560)
            break
        except Exception:
            font = None
text = "生"
if font is not None:
    box = draw.textbbox((0, 0), text, font=font)
    w, h = box[2] - box[0], box[3] - box[1]
    draw.text(((size - w) / 2 - box[0], (size - h) / 2 - box[1]), text, font=font, fill=(255, 255, 255))
else:
    draw.ellipse((332, 332, 692, 692), fill=(255, 255, 255))
out = os.path.join(OUT, "AppIcon.png")
img.save(out, "PNG")
print("saved", out)
