"""Reject a blank screenshot instead of reporting a launched process as a visible UI."""
import sys
from PIL import Image, ImageStat

with Image.open(sys.argv[1]) as image:
    # Ignore system bars: they can be visible while the app itself has not drawn.
    width, height = image.size
    content = image.crop((0, height // 10, width, height * 9 // 10)).convert("RGB")
    content.thumbnail((100, 100))
    stats = ImageStat.Stat(content)
    sys.exit(0 if max(stats.var) > 100 else 1)
