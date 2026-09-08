from PIL import Image
import numpy as np

img = Image.open("C:/Users/heidy/Tradedraw/screen_live.png").convert("RGB")
w, h = img.size
print(f"Image size: {w}x{h}")

# Test Buy button detection in portrait
xMin, xMax = int(w * 0.05), int(w * 0.48)
yMin, yMax = int(h * 0.80), int(h * 0.98)

pixels = np.array(img)
crop_buy = pixels[yMin:yMax, xMin:xMax]

r = crop_buy[:, :, 0].astype(int)
g = crop_buy[:, :, 1].astype(int)
b = crop_buy[:, :, 2].astype(int)

mask_buy = (g > 140) & (r < 100) & (b < 170) & ((g - r) > 45)
pts_y, pts_x = np.where(mask_buy)

if len(pts_x) > 15:
    cx = xMin + np.mean(pts_x)
    cy = yMin + np.mean(pts_y)
    print(f"BUY Button detected at: ({cx:.1f}, {cy:.1f}) [{len(pts_x)} px]")
else:
    print(f"BUY Button NOT detected ({len(pts_x)} px)")

# Test Sell button detection
xMin_s, xMax_s = int(w * 0.52), int(w * 0.95)
crop_sell = pixels[yMin:yMax, xMin_s:xMax_s]
r_s = crop_sell[:, :, 0].astype(int)
g_s = crop_sell[:, :, 1].astype(int)
b_s = crop_sell[:, :, 2].astype(int)

mask_sell = (r_s > 180) & (g_s < 140) & (b_s < 140) & ((r_s - g_s) > 55)
pts_sy, pts_sx = np.where(mask_sell)

if len(pts_sx) > 15:
    cx_s = xMin_s + np.mean(pts_sx)
    cy_s = yMin + np.mean(pts_sy)
    print(f"SELL Button detected at: ({cx_s:.1f}, {cy_s:.1f}) [{len(pts_sx)} px]")
else:
    print(f"SELL Button NOT detected ({len(pts_sx)} px)")
