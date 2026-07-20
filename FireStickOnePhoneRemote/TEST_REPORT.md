# Test Report

## Test date

July 21, 2026

## Tests performed in this environment

### 1. Android resource and project consistency

- Parsed all XML resources and the Android manifest.
- Confirmed every `R.id` referenced by `MainActivity` exists in the layout.
- Confirmed required source, Gradle, workflow, and README files exist.
- Confirmed the network client rejects public IP addresses.

Result: **PASS**

### 2. Kotlin core compilation and execution

Compiled and executed the Android-independent Kotlin core files with `kotlinc`:

- Fire Stick model/profile mapping
- Endpoint generation
- Private IPv4 validation
- Hotspot subnet extraction
- Vega experimental profile presence

Result: **PASS — 12 selectable profiles validated**

### 3. Mock Fire TV protocol matrix

A local mock Fire TV server exercised PIN pairing, navigation, media, text, app launch, status, and properties endpoints.

| Simulated profile | Result |
|---|---|
| Fire TV Stick Gen 1 | PASS |
| Fire TV Stick Gen 2 | PASS |
| Basic Edition | PASS |
| Fire TV Stick 4K Gen 1 | PASS |
| Fire TV Stick Lite / Gen 3 | PASS |
| Fire TV Stick 4K Max Gen 1 | PASS |
| Fire TV Stick 4K / 4K Max Gen 2 | PASS |
| Fire TV Stick HD — Fire OS profile | PASS |
| Fire TV Stick 4K Select — Vega profile | Unsupported response detected correctly |
| Fire TV Stick HD — Vega profile | Unsupported response detected correctly |

Result: **PASS — protocol handling behaved as expected for 10 simulated profiles**

## Tests not possible in this environment

- Physical testing on real Fire Stick hardware
- Verification against every firmware revision
- OnePlus 12 hotspot-host communication test
- Full Android APK compilation, because this execution environment does not include an Android SDK or internet access for Gradle dependencies

The included GitHub Actions workflow runs Android unit tests and builds `app-debug.apk` after the project is uploaded to GitHub.

## Honest compatibility status

The project is software-tested and packaged correctly. It is **not physically certified** for every Fire Stick generation. Fire OS devices exposing the PIN-paired port-8080 control service are the intended supported target. Vega OS remains experimental until tested on actual hardware.
