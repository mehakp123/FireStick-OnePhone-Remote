#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
errors = []

# Parse every Android XML resource.
for path in (root / "app/src/main/res").rglob("*.xml"):
    try:
        ET.parse(path)
    except Exception as exc:
        errors.append(f"Invalid XML {path.relative_to(root)}: {exc}")

manifest = root / "app/src/main/AndroidManifest.xml"
try:
    ET.parse(manifest)
except Exception as exc:
    errors.append(f"Invalid manifest: {exc}")

# Check that every R.id used in MainActivity exists in layout resources.
main = (root / "app/src/main/java/com/pannu/firestickremote/MainActivity.kt").read_text()
used = set(re.findall(r"R\.id\.([A-Za-z0-9_]+)", main))
layout_text = "\n".join(p.read_text() for p in (root / "app/src/main/res/layout").glob("*.xml"))
defined = set(re.findall(r"@\+id/([A-Za-z0-9_]+)", layout_text))
missing = sorted(used - defined)
if missing:
    errors.append("Missing layout IDs: " + ", ".join(missing))

# Check required workflow and core files.
required = [
    ".github/workflows/build-apk.yml",
    "app/build.gradle.kts",
    "app/src/main/java/com/pannu/firestickremote/network/FireTvClient.kt",
    "app/src/main/java/com/pannu/firestickremote/network/DiscoveryManager.kt",
    "README.md",
]
for item in required:
    if not (root / item).exists():
        errors.append(f"Missing required file: {item}")

# Ensure app only allows private destination IPs.
client = (root / "app/src/main/java/com/pannu/firestickremote/network/FireTvClient.kt").read_text()
if "IpTools.isPrivateIpv4" not in client:
    errors.append("Local-network IP safety check missing")

if errors:
    print("STATIC CHECK FAILED")
    for error in errors:
        print(" -", error)
    sys.exit(1)

print(f"PASS: XML parsed; {len(used)} referenced view IDs are defined; required files present")
