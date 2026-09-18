#!/usr/bin/env python3
"""Verifica el balance de llaves {} ignorando comentarios y strings (los ${...} dentro de strings
y los comentarios producen falsos positivos con un conteo ingenuo)."""
import sys

def balance(path):
    src = open(path, encoding='utf-8').read()
    i, n = 0, len(src)
    depth_open = depth_close = 0
    state = 'code'  # code | line_comment | block_comment | string | char | raw_string
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ''
        if state == 'code':
            if c == '/' and nxt == '/':
                state = 'line_comment'; i += 2; continue
            if c == '/' and nxt == '*':
                state = 'block_comment'; i += 2; continue
            if src.startswith('"""', i):
                state = 'raw_string'; i += 3; continue
            if c == '"':
                state = 'string'; i += 1; continue
            if c == "'":
                state = 'char'; i += 1; continue
            if c == '{':
                depth_open += 1
            elif c == '}':
                depth_close += 1
            i += 1
        elif state == 'line_comment':
            if c == '\n':
                state = 'code'
            i += 1
        elif state == 'block_comment':
            if c == '*' and nxt == '/':
                state = 'code'; i += 2; continue
            i += 1
        elif state == 'string':
            if c == '\\':
                i += 2; continue
            if c == '"':
                state = 'code'
            i += 1
        elif state == 'char':
            if c == '\\':
                i += 2; continue
            if c == "'":
                state = 'code'
            i += 1
        elif state == 'raw_string':
            if src.startswith('"""', i):
                state = 'code'; i += 3; continue
            i += 1
    return depth_open, depth_close, (depth_open == depth_close)

if __name__ == '__main__':
    ok_all = True
    for p in sys.argv[1:]:
        o, c, ok = balance(p)
        status = 'OK' if ok else 'DESBALANCE'
        print(f'{status:10} open={o:4} close={c:4}  {p}')
        ok_all = ok_all and ok
    sys.exit(0 if ok_all else 1)