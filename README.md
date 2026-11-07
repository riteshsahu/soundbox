# SoundBox

SoundBox is a small, native Android app for announcing confidently identified incoming payments in Hindi and keeping a private three-month history on the device.

## Current milestone

The foundation milestone adds a runnable Kotlin app shell with three screens: home, setup, and payment history. The visible UI can be switched between Hindi and English, with Hindi as the default; the choice is saved locally. Payment announcements will remain Hindi-only regardless of the selected UI language. Navigation works. Payment listening, speech, filters, storage, and audio controls are not active yet; the UI labels those states so they are not mistaken for working features.

The app requests no Android permissions in this milestone. Android backup is disabled in the manifest in preparation for local payment history.

## Build

Open this folder in Android Studio or run:

```sh
./gradlew assembleDebug
```

The project uses Android Gradle Plugin 9.5.0 with its built-in Kotlin support, Gradle 9.6.0, Java 17, compile SDK 37, and target SDK 35. The minimum Android version is Android 6.0 (API 23); the target Redmi Note 8 Pro runs Android 11 (API 30).
