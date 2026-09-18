# -*- coding: utf-8 -*-
import io, sys, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
p = r'C:\Users\heidy\Tradedraw\loops\RENTABILITY_LOOP.md'
t = open(p, encoding='utf-8-sig', errors='replace').read()
lines = t.splitlines()

keys = ['Desglose por nivel de martingala', 'Referencia histórica', 'umbral de rentabilidad',
        'payout que haría', 'matematica del margen', 'H1 —', 'DATO CRITICO', 'DATO CRÍTICO',
        'Veredicto sobre la frecuencia', 'martingala con WR', 'advertencia sobre las afirmaciones',
        'martingala no puede', 'rachas de 10']

for k in keys:
    for i, l in enumerate(lines):
        if l.startswith('#') and k.lower() in l.lower():
            print(">>>> " + l)
            for x in lines[i + 1:i + 30]:
                if re.match(r'^#{1,2} ', x):
                    break
                if x.strip():
                    print(x)
            print()
            break