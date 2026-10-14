#!/usr/bin/env python3
"""Local sanity checks that do not need a JDK / Android SDK.

Checks:
  * every XML resource + the manifest is well formed
  * every @string/@color/@drawable/@style reference in XML resolves
  * every R.string / R.color / R.id used in Kotlin exists in the resources
  * every binding.<name> used in Kotlin matches an @+id in the right layout
  * Kotlin files have balanced braces/parens and no obvious leftovers
  * the CI workflow parses as YAML and its job graph is sane
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
SRC = ROOT / "app/src/main/java"
TEST = ROOT / "app/src/test/java"
errors: list[str] = []
notes: list[str] = []


def fail(msg: str) -> None:
    errors.append(msg)


# ---------------------------------------------------------------- XML parsing
xml_files = sorted(RES.rglob("*.xml")) + [ROOT / "app/src/main/AndroidManifest.xml"]
resources: dict[str, set[str]] = {"string": set(), "color": set(), "drawable": set(), "style": set(), "id": set()}
layout_ids: dict[str, set[str]] = {}

for path in xml_files:
    try:
        tree = ET.parse(path)
    except ET.ParseError as exc:
        fail(f"{path}: XML parse error: {exc}")
        continue
    root = tree.getroot()

    if path.parent.name == "values":
        for child in root:
            name = child.get("name")
            if not name:
                continue
            tag = child.tag
            if tag in resources:
                resources[tag].add(name)
            if tag == "style" and name.startswith("Theme."):
                resources["style"].add(name)
        continue

    if path.parent.name == "layout":
        ids = set()
        for element in root.iter():
            for attr, value in element.attrib.items():
                if attr.endswith("}id") and value.startswith("@+id/"):
                    ids.add(value.split("@+id/", 1)[1])
                elif attr.endswith("}id") and value.startswith("@id/"):
                    ids.add(value.split("@id/", 1)[1])
        layout_ids[path.stem] = ids
        resources["id"] |= ids

    if path.name == "AndroidManifest.xml":
        continue

# drawable files
for path in (RES / "drawable").glob("*.xml"):
    resources["drawable"].add(path.stem)

# ------------------------------------------------------- XML reference checks
ref_re = re.compile(r"@(string|color|drawable|style|id)/([\w.]+)")
android_ref_re = re.compile(r"@android:(?:string|color|style|attr)/[\w.]+")
for path in xml_files:
    text = path.read_text(encoding="utf-8")
    # strip android: framework refs
    for match in ref_re.finditer(android_ref_re.sub("", text)):
        kind, name = match.group(1), match.group(2)
        if kind == "id":
            continue  # forward references inside a layout are fine
        if name not in resources.get(kind, set()):
            fail(f"{path}: unresolved @{kind}/{name}")

# style="@style/SectionTitle" is declared in themes.xml
if "SectionTitle" not in resources["style"]:
    fail("SectionTitle style missing")

# ------------------------------------------------------- Kotlin R.<x> checks
kt_files = sorted(SRC.rglob("*.kt")) + sorted(TEST.rglob("*.kt"))
# a leading dot means it is a framework/material R (android.R.layout.simple_spinner_item)
r_re = re.compile(r"(?<![\w.])R\.(string|color|id|drawable|style|layout)\.(\w+)")
for path in kt_files:
    text = path.read_text(encoding="utf-8")
    for match in r_re.finditer(text):
        kind, name = match.group(1), match.group(2)
        if kind == "layout":
            if not (RES / "layout" / f"{name}.xml").exists():
                fail(f"{path}: unresolved R.layout.{name}")
            continue
        if name not in resources.get(kind, set()):
            fail(f"{path}: unresolved R.{kind}.{name}")

# ------------------------------------------------- Kotlin view binding checks
binding_re = re.compile(r"\bbinding\.(\w+)")
activity_layout = {
    "MainActivity.kt": "activity_main",
    "CodeScanActivity.kt": "activity_code_scan",
}
for activity, layout in activity_layout.items():
    path = SRC / "com/azahar4bd/walton/wrc03" / activity
    if not path.exists():
        fail(f"missing {activity}")
        continue
    ids = layout_ids.get(layout, set())
    used = set(binding_re.findall(path.read_text(encoding="utf-8")))
    for name in sorted(used):
        if name == "root":
            continue
        if name not in ids:
            fail(f"{activity}: binding.{name} has no @+id/{name} in {layout}.xml")
    unused = ids - used
    if unused:
        notes.append(f"{activity}: layout ids not used from code: {sorted(unused)}")

# ---------------------------------------------------------- Kotlin structure
for path in kt_files:
    text = path.read_text(encoding="utf-8")
    # crude string/comment stripper so braces in strings do not confuse the count
    stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', text, flags=re.S)
    stripped = re.sub(r"//.*", "", stripped)
    stripped = re.sub(r"/\*.*?\*/", "", stripped, flags=re.S)
    for open_ch, close_ch in (("{", "}"), ("(", ")"), ("[", "]")):
        if stripped.count(open_ch) != stripped.count(close_ch):
            fail(
                f"{path}: unbalanced {open_ch}{close_ch} "
                f"({stripped.count(open_ch)} vs {stripped.count(close_ch)})"
            )
    if "TODO()" in text or "FIXME" in text:
        fail(f"{path}: leftover TODO/FIXME")
    if not text.splitlines()[0].startswith("package "):
        fail(f"{path}: first line is not a package declaration")
    declared = re.search(r"^package\s+([\w.]+)", text, flags=re.M).group(1)
    base = SRC if str(path).startswith(str(SRC)) else TEST
    relative = path.parent.relative_to(base).as_posix().replace("/", ".")
    if declared != relative:
        fail(f"{path}: package {declared} does not match folder {relative}")

# ---------------------------------------------------------------- YAML check
workflow = ROOT / ".github/workflows/android.yml"
try:
    import yaml  # type: ignore
except ImportError:
    notes.append("pyyaml not installed - workflow YAML not parsed")
    yaml = None
if yaml:
    data = yaml.safe_load(workflow.read_text(encoding="utf-8"))
    jobs = data.get("jobs", {})
    for name in ("test", "build", "release"):
        if name not in jobs:
            fail(f"workflow: job '{name}' missing")
    if jobs.get("release", {}).get("permissions", {}).get("contents") != "write":
        fail("workflow: release job needs contents: write")
    triggers = data.get(True, data.get("on"))
    if "v*" not in str(triggers):
        fail("workflow: tag trigger v* missing")
    notes.append(f"workflow jobs: {sorted(jobs)}")

# ------------------------------------------------------------------- report
print(f"checked {len(xml_files)} XML files, {len(kt_files)} Kotlin files")
for note in notes:
    print(f"note: {note}")
if errors:
    print(f"\n{len(errors)} problem(s):")
    for error in errors:
        print(f"  ✗ {error}")
    sys.exit(1)
print("\n✓ all local checks passed")
