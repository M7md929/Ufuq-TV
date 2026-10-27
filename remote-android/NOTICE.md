# Third-party protocol references and media

The local Android TV Remote v2 wire implementation follows the Polo and Remote message schemas and pairing algorithm documented by these upstream projects:

- [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2), Apache License 2.0. The license text is preserved in `LICENSE-androidtvremote2.txt`. Its protobuf-generated Python modules were used by the separate development test fixture; they are not bundled in the APK.
- [AOSP google-tv-pairing-protocol](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/), origin of Polo pairing.
- [Android TV Remote v2 protocol notes](https://github.com/Aymkdn/assistant-freebox-cloud/wiki/Google-TV-(aka-Android-TV)-Remote-Control-(v2)).

`Wire`, `RemoteSession`, `ClientIdentity` and the phone UI are native Java implementations for this project. The photo catalog, renderer and monkey geometry were adapted from Ufuq TV. `MonkeyMotion` sizes the portrait character using screen width while retaining corner anchoring.

The optional mouse transport implements the [AOSP ADB protocol](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/protocol.txt) and [Android RSA public key format](https://android.googlesource.com/platform/system/core/+/refs/heads/main/libcrypto_utils/android_pubkey.cpp). `AdbWire`, `AdbIdentity`, `AdbPointer`, `PointerAgent` and `WakeOnLan` are project implementations. No ADB executable or third-party ADB library is embedded. `PointerAgent` uses Android's input APIs as an explicitly authorized shell process, comparable to the [AOSP input command](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android10-release/cmds/input/src/com/android/commands/input/Input.java); it is not an accessibility service and does not listen on a network port.

Oman photographs: see `assets/credits.txt` and `assets/photos.json`. Each image retains its individual attribution, license or reserved rights. The monkey atlas was generated using ImageGen for Ufuq.

No open-source license has been selected automatically for the original application code.
