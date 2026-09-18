# -*- coding: utf-8 -*-
import io, csv, os

f = r"C:\Users\heidy\Tradedraw\scratch\journal_v2.csv"
txt = open(f, "rb").read().decode("utf-8-sig")
lines = txt.splitlines()

# ultimas 6 lineas crudas, contar campos por linea
print("### TAIL CRUDO journal_v2.csv")
for l in lines[-6:]:
    r = next(csv.reader([l]))
    print("nfields=%d | %s" % (len(r), l[:260]))
print()
print("### distribucion de nfields por linea")
from collections import Counter
c = Counter()
for l in lines[1:]:
    if not l.strip(): continue
    c[len(next(csv.reader([l])))] += 1
print(c)
print()
print("### cuantas lineas tienen 24 campos y su rango de fecha")
n24 = []
for l in lines[1:]:
    if not l.strip(): continue
    r = next(csv.reader([l]))
    if len(r) == 24:
        n24.append(r)
print("total 24-campos:", len(n24))
if n24:
    print("primera:", n24[0][1], "| ultima:", n24[-1][1])
    print("ejemplo fila:", n24[0][:24])