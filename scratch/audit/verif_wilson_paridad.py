"""Comparacion bit a bit de wilson_interval/to_float/Z95 entre edge_analysis y analyze_journal."""
import sys
sys.path.insert(0, "scripts")
import edge_analysis as ea  # noqa: E402
import analyze_journal as aj  # noqa: E402

casos = [(81, 193), (0, 1), (1, 1), (0, 0), (0, 10), (10, 10), (1, 2),
         (50, 100), (193, 193), (0, 5), (7, 7), (3, 4), (204, 204)]
print("%-10s %-30s %-30s %s" % ("(w,n)", "EA wilson_interval", "AJ wilson_interval", "IGUAL"))
ok = True
for w, n in casos:
    a = ea.wilson_interval(w, n)
    b = aj.wilson_interval(w, n)
    same = a[0] == b[0] and a[1] == b[1]
    ok = ok and same
    print("%-10s %-30s %-30s %s" % ((w, n), "[%.12f, %.12f]" % a, "[%.12f, %.12f]" % b, same))
print("TODOS IGUALES bit a bit:", ok)
print("Z95 EA=%r AJ=%r igual=%s" % (ea.Z95, aj.Z95, ea.Z95 == aj.Z95))
print("wilson_interval(81,193) EA =", ea.wilson_interval(81, 193))
print("to_float EA('1.5')=%r AJ=%r" % (ea.to_float("1.5"), aj.to_float("1.5")))
print("to_float basura EA('abc',7.0)=%r AJ=%r" % (ea.to_float("abc", 7.0), aj.to_float("abc", 7.0)))
print("to_float vacio EA('')=%r AJ=%r" % (ea.to_float(""), aj.to_float("")))
print("to_float None EA=%r AJ=%r" % (ea.to_float(None), aj.to_float(None)))