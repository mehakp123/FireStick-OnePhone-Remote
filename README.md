# Fire Stick Rescue Remote — One Phone

An Android rescue remote intended for the situation where a Fire TV Stick remembers an old Wi-Fi network but the physical remote is unavailable.

The phone creates a hotspot using the **old Wi-Fi name and password**. The Fire Stick connects to that hotspot. This app then tries to discover and control the Fire Stick directly from the same phone that is hosting the hotspot, so a second phone is not required.

## What the app includes

- Guided old-SSID hotspot setup
- Fire Stick generation/profile selector
- Automatic DIAL/SSDP discovery
- Hotspot subnet scanning on ports 8009 and 8080
- Manual Fire Stick IP entry
- PIN pairing and encrypted local token storage
- D-pad, Select, Home, Back and Menu
- Play/Pause, Rewind and Fast-forward
- Fire TV keyboard text entry
- Netflix, YouTube and Prime Video shortcuts
- New Wi-Fi migration guide
- Diagnostic report sharing
- No server, account, Firebase or subscription

## Important compatibility note

The control interface on port 8080 is an undocumented Fire TV local protocol. The project implements the observed PIN-paired “Lightning” REST endpoints and includes Fire OS and experimental Vega profiles. The software tests validate request construction and profile handling; they do **not** replace physical testing on each Fire Stick and firmware combination.

Older Fire Stick models may not expose the port-8080 service. Vega OS support is experimental until confirmed on real hardware.

## One-phone usage

1. Open the app and enter the old Wi-Fi name and password.
2. Tap **Open hotspot settings**.
3. Configure the phone hotspot with exactly the old Wi-Fi name and password.
4. Turn on the hotspot and wait for the Fire Stick to connect.
5. Return to the app and tap **Auto scan**.
6. Select the detected IP address or enter it manually.
7. Tap **Show PIN on TV**.
8. Enter the four-digit PIN displayed on the television and tap **Pair**.
9. Use the remote to open `Settings → Network` on the Fire Stick.
10. Select the new/current Wi-Fi and enter its password.
11. After the Fire Stick leaves the rescue hotspot, turn off the hotspot and connect the phone to the new Wi-Fi.
12. Tap **Auto scan** again. The saved token should reconnect where the firmware permits it.

## Build the APK using GitHub on a phone

1. Create a new empty GitHub repository.
2. Extract this ZIP on the phone.
3. Upload all extracted files and folders to the repository root. Keep the `.github` folder.
4. Commit the files.
5. Open the repository’s **Actions** tab.
6. Select **Build Android APK**.
7. Tap **Run workflow**.
8. Open the completed workflow run.
9. Under **Artifacts**, download **FireStick-Rescue-Remote-APK**.
10. Extract the artifact ZIP and install `app-debug.apk`.

Android may ask you to permit installation from the browser or file manager used to open the APK.

## Local self-tests included

Run:

```bash
tools/run_self_tests.sh
```

The test suite checks:

- Android XML parsing and referenced resource IDs
- Local-IP safety restrictions
- Endpoint generation
- Model/profile mapping
- A mock protocol matrix covering Fire OS-style and unsupported Vega-style responses

GitHub Actions additionally runs Android unit tests and builds the APK.

## Security

- The app refuses to send Fire TV commands to public IP addresses.
- The pairing token is encrypted with Android Keystore before storage.
- The app accepts the Fire Stick’s local self-signed TLS certificate only for a user-supplied private LAN IP.
- Old and new Wi-Fi passwords are not saved by the app.

## Technical protocol paths

- DIAL wake: port 8009, `/apps/FireTVRemote`
- Pairing and commands: port 8080
- PIN display: `/v1/FireTV/pin/display`
- PIN verify: `/v1/FireTV/pin/verify`
- Navigation: `/v1/FireTV?action=...`
- Text: `/v1/FireTV/text`
- Media: `/v1/media?action=...`

## License

This project is provided for personal control of devices you own or are authorised to operate. Fire TV is a trademark of Amazon. This project is unofficial and is not affiliated with Amazon.
