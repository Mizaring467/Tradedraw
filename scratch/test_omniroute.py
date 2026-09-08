import urllib.request
import json
import base64

with open('C:/Users/heidy/Tradedraw/test_screen.png', 'rb') as f:
    b64 = base64.b64encode(f.read()).decode()

payload = {
    'model': 'antigravity/gemini-3.7-flash-low',
    'messages': [
        {
            'role': 'user',
            'content': [
                {'type': 'text', 'text': 'Analiza el grafico y responde en formato JSON exacto: {"action": "BUY", "confidence": 0.85, "reason": "rebote en soporte"}'},
                {'type': 'image_url', 'image_url': {'url': 'data:image/png;base64,' + b64}}
            ]
        }
    ],
    'max_tokens': 60
}

req = urllib.request.Request(
    'http://192.168.1.185:20128/v1/chat/completions',
    headers={'Authorization': 'Bearer sk-5f238e76072d7926-95c3e9-7cd7ecb1', 'Content-Type': 'application/json'},
    data=json.dumps(payload).encode()
)

try:
    with urllib.request.urlopen(req, timeout=25) as resp:
        print('Vision Response:', resp.read().decode())
except Exception as e:
    print('Vision Failed:', e)
