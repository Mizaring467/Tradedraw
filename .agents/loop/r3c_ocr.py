# OCR del HUD (overlay Canvas) desde una captura PNG. Salida: TEXTO plano a stdout.
import sys
from paddleocr import PaddleOCR

img = sys.argv[1]
ocr = PaddleOCR(lang='en', enable_mkldnn=False)
res = ocr.predict(img)

lines = []
def walk(r):
    # r puede ser lista de [bbox, (txt, score)] o dict (3.x)
    if isinstance(r, dict):
        texts = r.get('rec_texts') or []
        scores = r.get('rec_scores') or []
        for t, s in zip(texts, scores):
            lines.append(f"{t}")
        return
    for item in r or []:
        if item is None:
            continue
        if isinstance(item, dict):
            walk(item); continue
        for ln in item:
            try:
                txt = ln[1][0]; sc = ln[1][1]
                if sc >= 0.4:
                    lines.append(txt)
            except Exception:
                pass
walk(res)
print("\n".join(lines))
