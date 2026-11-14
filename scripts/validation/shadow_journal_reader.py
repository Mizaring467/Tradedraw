"""Read the bridge's raw v1/v2 shadow rows, including historical mixed-header files."""
import csv


def parse_shadow_rows(lines):
    entries = []
    for row in csv.reader(lines):
        if not row or row[0] == "timestamp":
            continue
        if len(row) not in (11, 15):
            raise ValueError("Unsupported shadow column count")
        version = 1 if len(row) == 11 else int(row[11])
        if version not in (1, 2) or (len(row) == 15 and version != 2):
            raise ValueError("Unsupported shadow schema")
        entry = dict(timestamp=int(row[0]), decision_id=row[9], reason=row[10],
                     action=row[4], price=float(row[6]), schema_version=version)
        if version == 2:
            entry.update(sim_result=row[12], sim_exit_price=float(row[13]) if row[13] else None,
                         sim_balance=float(row[14]) if row[14] else None)
        entries.append(entry)
    return entries
