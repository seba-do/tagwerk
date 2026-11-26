"""Generates values/strings.xml (English) and values-de/strings.xml (German) from strings.txt.

Each line: name | English | German. Lines starting with # are comments. Use %1$s / %1$d for arguments
and \\n for a line break.
"""
import pathlib, sys

root = pathlib.Path(__file__).resolve().parent
res = root.parent / "app/src/main/res"

def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"')
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s

rows, seen = [], set()
for n, line in enumerate((root / "strings.txt").read_text(encoding="utf-8").splitlines(), 1):
    if not line.strip() or line.startswith("#"):
        rows.append(("#", line[1:].strip()))
        continue
    parts = [p.strip() for p in line.split(" | ")]
    if len(parts) != 3:
        sys.exit(f"line {n}: expected 3 columns, got {len(parts)}: {line!r}")
    name, en, de = parts
    if name in seen:
        sys.exit(f"line {n}: duplicate {name}")
    seen.add(name)
    rows.append((name, en, de))

for folder, col in (("values", 1), ("values-de", 2)):
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated from i18n/strings.txt by i18n/gen.py. Edit the TSV, not this file. -->", "<resources>"]
    for row in rows:
        if row[0] == "#":
            if row[1]:
                out.append(f"    <!-- {row[1]} -->")
            continue
        out.append(f'    <string name="{row[0]}">{esc(row[col])}</string>')
    out.append("</resources>\n")
    (res / folder).mkdir(exist_ok=True)
    (res / folder / "strings.xml").write_text("\n".join(out), encoding="utf-8")
print(f"{len(seen)} strings")
