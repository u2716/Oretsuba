# We Have No Wings · Apple Platform Port

Native port of 《俺たちに翼はない》 (Navel, 2009) for iPhone / iPad / Apple Vision Pro / Mac.

> **v0.1.1 pre-release.** In the Chinese version, launching goes straight to the game title main screen, no need to click through the three patch notice images. Chinese text, sound, and progression have been confirmed on real iPhone, iPad, and Vision Pro devices; iPad normal / quick save and load, Vision Pro normal save and load, native large buttons, history toggle, and chapter text have all passed. The first opening movie on iPhone / Vision Pro, and the second opening movie on iPad — continuous video, music, and return at the end — have been confirmed on real devices. On Mac, the Chinese opening and directory selection in the release build have been verified. Full character routes have not yet all been played through one by one, and some visual effects still have open reverse-engineering issues, see [Validation Records and Coverage Limits](docs/VALIDATION.md). Install packages are in the [v0.1.1 Release](https://github.com/galhack/Oretsuba_iOS/releases/tag/v0.1.1).

This project rewrote from scratch the Lucifen engine used by the game (script virtual machine, layer compositing, transitions and effects, text, UI, audio, opening animation, saves), running natively on Apple devices, and supports the "Chinese Patch No. 3" translation.

**This repository does not contain any game content.** You need to purchase the game yourself and use your own game files.

## Supported Devices

| Device | Method | Minimum System Requirement for Build |
|---|---|---|
| iPhone, iPad | Sideload (Sideloadly or Xcode) | iOS / iPadOS 17 and above |
| Apple Vision Pro | Install native visionOS app with Xcode signing | visionOS 1 and above |
| Mac (Apple Silicon or Intel) | Run directly | macOS 14 and above |

## Preparing Game Files

1. The **`SET_UP` folder** from the game disc (or disc image), which should contain `SCRIPT.LPK`, `SYS.LPK`, `CHR.LPK`, `PIC.LPK`, `BGM.LPK`, `SE.LPK`, `VOICE.LPK`, `DATA.LPK`. No need to install the game on Windows.
2. (Optional) **Chinese Patch No. 3** folder, which should contain the `SCRIPT_CHS`, `SYS`, `DATA` folders and `ORE_TUBA_CHS.dll`. With the patch, you can select "Simplified Chinese" in the launcher.

## Installation

### iPhone / iPad (Sideload)

1. Download the latest `Oretsuba-iOS-unsigned.ipa` from the [Releases](../../releases) page of this repository.
2. Connect the device with a cable, open [Sideloadly](https://sideloadly.io/), select the device and IPA, enter your Apple ID and click Start. Apps signed with a free Apple ID need to be re-signed after 7 days, and at most 3 free-signed apps can be installed on the same device at the same time.
3. Before opening for the first time, trust your developer certificate in device Settings › General › VPN & Device Management.
4. If the system requires Developer Mode, enable it in Settings › Privacy & Security › Developer Mode, and restart / confirm as prompted.

See [Apple Personal Team description](https://developer.apple.com/help/account/basics/about-your-developer-account) for the validity period and app count limits for free accounts. When updating or re-signing, overwrite-install with the same Apple ID and Bundle ID to keep the app and local saves; do not uninstall first. See [official FAQ](https://sideloadly.io/faq.html) for Sideloadly overwrite install and auto re-signing.

### Apple Vision Pro

This project uses a native visionOS target on the first-generation Vision Pro, install as follows:

1. On Mac, install Xcode with the visionOS platform components, and sign in with your own Apple Account in Settings › Accounts.
2. Generate and open the project as in "Building from Source" below.
3. Pair Vision Pro with the Mac in Xcode device management; Xcode 27 uses Device Hub. Keep the headset unlocked, and enable Developer Mode as prompted by the system.
4. In the project's Signing & Capabilities, select your own Team, and keep Automatically manage signing on.
5. Select the paired Vision Pro as the run target and click Run. After the app is installed, import the game files as described below.

See [Apple's on-device running instructions](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices) and [Developer Mode instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device). The `Oretsuba-visionOS-unsigned.ipa` in Release needs separate signing and cannot be installed directly; the currently verified install method is Xcode, Sideloadly support for native visionOS packages has not yet been verified.

### Mac

Download `Oretsuba-macOS.zip`, unzip it, and drag `Oretsuba.app` into Applications. If blocked on first open, click "Open Anyway" in System Settings › Privacy & Security.

## Importing Game Files

* **iPhone / iPad**: Connect the Mac with a cable, in Finder sidebar select the device › Files tab › "We Have No Wings", drag the `SET_UP` folder (and the translation patch folder) in. You can also first save the folders to the Files app, then in the launcher tap "Import Folder from Files...".
* **Vision Pro**: AirDrop the folders to Vision Pro and save them to Files, then import in the launcher.
* **Mac**: In the launcher, directly select the game directory and translation patch directory, no copying needed.

Once the launcher detects the files, you can select the language and start the game.

## Controls

**Vision Pro**: Use the native "Save / Load / Quick Save / Quick Load / History / Menu" buttons below the game window. Click "History" again to close the text log, look at the text then pinch to advance.

| Action | Touch | Mouse / Keyboard |
|---|---|---|
| Advance / Confirm | Tap | Left click, Enter |
| Menu / Hide textbox | Two-finger tap | Right click |
| History (text log) | Swipe down | Scroll wheel up |
| Fast-forward | Long press | Hold Ctrl (or change to Shift in settings) |

Gamepad also supported: A confirm, B menu, right trigger fast-forward, D-pad up / down scroll.

## Building from Source

This round of validation uses Xcode 27.1. If the system defaults to Command Line Tools, set it in the current terminal first:

```sh
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
```

```sh
ORE_TEAM=YOUR_TEAM_ID tools/apple/gen_project.sh   # Replace YOUR_TEAM_ID with your own development team ID
open app/Oretsuba.xcodeproj
```

Team ID can be confirmed in Xcode signing settings. To build only Mac, you can directly run `tools/apple/gen_project.sh`, no development team needed. The project generation script uses XcodeGen in PATH; if not installed, it downloads a pinned version to this project's ignored `build/tools` directory.

Command-line test host (macOS, runs without a window and outputs screenshots):

```sh
make -C engine
build/host/orehost --data <SET_UP directory> --frames 1200 --shot 600,1200 --out /tmp/ore
```

See `docs/ENGINE_GUIDE.md` and `docs/re/` for engine structure and reverse-engineering notes.

## Acknowledgements and License

* Game 《俺たちに翼はない》 © Navel. This project is not affiliated with Navel, for players who have purchased the genuine game only.
* Chinese translation: Chinese Patch No. 3 team (translation project [HIllya51/ORE_TUBA_Chinese](https://github.com/HIllya51/ORE_TUBA_Chinese)).
* Third-party libraries: stb_vorbis, stb_image, stb_image_write (public domain / MIT), pl_mpeg (MIT), minimp3 (CC0). LPK / ELG format referenced GARbro (MIT). See [ThirdPartyNotices.txt](app/Resources/ThirdPartyNotices.txt) for related licenses and attributions, also included in the app bundle.

## Local Verification and Test Packages

```sh
make -C engine -j8 all tests
build/host/save_roundtrip /path/to/SET_UP
build/host/quick_save /path/to/SET_UP
build/host/command_input /path/to/SET_UP
build/host/native_coverage /path/to/SET_UP
build/host/native_coverage /path/to/SET_UP /path/to/Chinese-Patch-No-3
build/host/image_blend
build/host/movie_flow /path/to/SET_UP /path/to/Chinese-Patch-No-3
python3 tools/test_chinese_save.py /path/to/SET_UP /path/to/Chinese-Patch-No-3
python3 tools/test_quick_save.py /path/to/SET_UP /path/to/Chinese-Patch-No-3
python3 tools/test_history_toggle.py /path/to/SET_UP /path/to/Chinese-Patch-No-3
xcrun swiftc -module-cache-path build/swift-module-cache \
  app/Sources/App/FolderImport.swift app/Tests/FolderImportTests.swift \
  -o build/folder-import-tests
build/folder-import-tests
tools/apple/package.sh ios       # Unsigned IPA, for sideload tools to re-sign
tools/apple/package.sh visionos  # Unsigned native visionOS IPA
tools/apple/package.sh macos     # Local ad-hoc signed Mac Catalyst package
```

Build output is in `build/packages/` and does not include game data. An unsigned IPA cannot be installed directly via device installation tools; it must be signed with the user's own account. The Mac package has not been notarized yet.

Maintainer: galhack <galhack@protonmail.com>.

## Android

Min SDK 26 (Android 8+), target SDK 34, ABIs arm64-v8a + armeabi-v7a + x86_64.

The APK builds without game files. Install it, then open the app and import the `SET_UP` game folder.
