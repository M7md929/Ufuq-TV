# Third-party protocol references and media

The local Android TV Remote v2 wire implementation follows the Polo and Remote message schemas and pairing algorithm documented by these upstream projects:

- [tronikos/androidtvremote2](https://github.com/tronikos/androidtvremote2), Apache License 2.0. The license text is preserved in `LICENSE-androidtvremote2.txt`. Its protobuf-generated Python modules were used by the separate development test fixture; they are not bundled in the APK.
- [AOSP google-tv-pairing-protocol](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/), origin of Polo pairing.
- [Android TV Remote v2 protocol notes](https://github.com/Aymkdn/assistant-freebox-cloud/wiki/Google-TV-(aka-Android-TV)-Remote-Control-(v2)).

`Wire`, `RemoteSession`, `ClientIdentity` and the phone UI are native Java implementations for this project. The photo catalog, renderer and monkey geometry were adapted from Ufuq TV. `MonkeyMotion` sizes the portrait character using screen width while retaining corner anchoring.

Oman photographs: see `assets/credits.txt` and `assets/photos.json`. Each image retains its individual attribution, license or reserved rights. The monkey atlas was generated using ImageGen for Ufuq.

No open-source license has been selected automatically for the original application code.
