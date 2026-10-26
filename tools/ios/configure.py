"""Generate the local Xcode config from the same mobile MapKit key as Android."""
import os
from pathlib import Path

root = Path(__file__).resolve().parents[2]
properties = {}
for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
key = os.environ.get("YANDEX_MAPKIT_KEY") or properties.get("yandexMapkitKey", "")
if any(c in key for c in "\r\n"):
    raise ValueError("Invalid MapKit key")
for configuration in ["Debug", "Release"]:
    include = '#include? "Pods/Target Support Files/Pods-ZikrTime/Pods-ZikrTime.' + configuration.lower() + '.xcconfig"\n'
    (root / ("ios/Local-" + configuration + ".xcconfig")).write_text(include + "YANDEX_MAPKIT_KEY = " + key + "\n", encoding="utf-8")
print("iOS MapKit key configured" if key else "iOS MapKit key is missing; places list and routes remain available")
