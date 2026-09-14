import os
import re
import sys
import json
import argparse

def load_config(config_path):
    if not os.path.exists(config_path):
        return {
            "invariants": [
                {
                    "variables": ["distToSupport", "distToResistance"],
                    "sum": 1.0,
                    "description": "distToSupport + distToResistance == 1.0"
                }
            ]
        }
    with open(config_path, 'r') as f:
        return json.load(f)

def evaluate_redundancy(op1, val1, op2, val2, total, var1, var2):
    if op1 in ("<=", "<") and op2 in (">=", ">"):
        implied_val2 = total - val1
        if implied_val2 >= val2:
            return True, f"If {var1} {op1} {val1}, then {var2} is guaranteed to be >= {implied_val2:.2f}, making {var2} {op2} {val2} redundant."
    elif op1 in (">=", ">") and op2 in ("<=", "<"):
        implied_val2 = total - val1
        if implied_val2 <= val2:
            return True, f"If {var1} {op1} {val1}, then {var2} is guaranteed to be <= {implied_val2:.2f}, making {var2} {op2} {val2} redundant."
    if op2 in ("<=", "<") and op1 in (">=", ">"):
        implied_val1 = total - val2
        if implied_val1 >= val1:
            return True, f"If {var2} {op2} {val2}, then {var1} is guaranteed to be >= {implied_val1:.2f}, making {var1} {op1} {val1} redundant."
    elif op2 in (">=", ">") and op1 in ("<=", "<"):
        implied_val1 = total - val2
        if implied_val1 <= val1:
            return True, f"If {var2} {op2} {val2}, then {var1} is guaranteed to be <= {implied_val1:.2f}, making {var1} {op1} {val1} redundant."
    return False, ""

def detect_redundant_conditions(filepath, config):
    if not os.path.exists(filepath):
        return []
    issues = []
    invariants = config.get("invariants", [])
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    for idx, line in enumerate(lines):
        line_num = idx + 1
        for inv in invariants:
            var1, var2 = inv["variables"]
            total = inv["sum"]
            pattern1 = rf"({var1})\s*(<=|>=|<|>)\s*([\d\.]+)f?\s*(?:&&|and)\s*({var2})\s*(<=|>=|<|>)\s*([\d\.]+)f?"
            pattern2 = rf"({var2})\s*(<=|>=|<|>)\s*([\d\.]+)f?\s*(?:&&|and)\s*({var1})\s*(<=|>=|<|>)\s*([\d\.]+)f?"
            for m in re.finditer(pattern1, line):
                v1, op1, val1, v2, op2, val2 = m.groups()
                val1, val2 = float(val1), float(val2)
                is_redundant, msg = evaluate_redundancy(op1, val1, op2, val2, total, v1, v2)
                if is_redundant:
                    issues.append((filepath, line_num, f"Redundant condition: {m.group(0)}. {msg}"))
            for m in re.finditer(pattern2, line):
                v2, op2, val2, v1, op1, val1 = m.groups()
                val1, val2 = float(val1), float(val2)
                is_redundant, msg = evaluate_redundancy(op1, val1, op2, val2, total, v1, v2)
                if is_redundant:
                    issues.append((filepath, line_num, f"Redundant condition: {m.group(0)}. {msg}"))
    return issues

def detect_unreachable_vetos(filepath):
    if not os.path.exists(filepath):
        return []
    issues = []
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    if "TradingEngine.kt" in filepath:
        if "distToResistance <= 0.15f" in content and ("Prohibido comprar sobre Resistencia" in content or "veto" in content.lower()):
            # Find the line number approximately
            lines = content.split('\n')
            for i, line in enumerate(lines):
                if "distToResistance <= 0.15f" in line:
                    issues.append((filepath, i+1, "Unreachable Veto: blocks BUY if distToResistance <= 0.15 (implies distToSupport >= 0.85), but BUY strategies require distToSupport <= 0.22."))
                if "distToSupport <= 0.15f" in line:
                    issues.append((filepath, i+1, "Unreachable Veto: blocks SELL if distToSupport <= 0.15 (implies distToResistance >= 0.85), but SELL strategies require distToResistance <= 0.22."))
    return issues

def main():
    parser = argparse.ArgumentParser(description="Detect inert and redundant conditions.")
    parser.add_argument('--config', default='scripts/inert_config.json', help='Path to configuration file.')
    parser.add_argument('files', nargs='*', help='Files to analyze.')
    args = parser.parse_args()
    config = load_config(args.config)
    files_to_check = args.files if args.files else [
        "app/src/main/java/com/example/tradedraw/SyntheticCandleEngine.kt",
        "app/src/main/java/com/example/tradedraw/TradingEngine.kt"
    ]
    all_issues = []
    for f in files_to_check:
        issues = detect_redundant_conditions(f, config)
        issues += detect_unreachable_vetos(f)
        all_issues.extend(issues)
    if all_issues:
        print("[ERROR] INERT CONDITIONS DETECTED:")
        for filepath, line, msg in all_issues:
            print(f"[{filepath}:{line}] {msg}")
        sys.exit(1)
    else:
        print("[OK] No inert conditions found.")
        sys.exit(0)

if __name__ == "__main__":
    main()
