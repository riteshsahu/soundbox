# SoundBox

SoundBox is a small, native Android app for announcing confidently identified incoming payments in Hindi and keeping a private three-month history on the device.

## Milestones

**Milestone 1 — Foundation:** The runnable Kotlin app shell has Home, Setup, and Payment History screens. The visible UI can be switched between Hindi and English, with Hindi as the default; the choice is saved locally.

**Milestone 2 — Speech and listener:** The app checks OS notification-listener access, requires an installed offline Hindi TTS voice, offers Hindi test speech, and listens only to exact Google Pay, PhonePe, and Paytm package IDs. Announcements are off until explicitly enabled. The parser is deliberately conservative and uses synthetic tests. Speech stays Hindi regardless of UI language.

History storage, filtering, deduplication across restarts, and audio enhancement are not implemented yet. The listener suppresses short-window duplicates in memory only. The app labels incomplete features so they are not mistaken for working behavior.

Notification access is a user-enabled Android system setting; the app does not declare a `uses-permission` for it and does not request `POST_NOTIFICATIONS`. Android backup is disabled in the manifest in preparation for local payment history. The listener checks the exact package allowlist before accessing notification content, and it does not log notification text.

The allowlist is centralized in `PaymentProviderAllowlist.kt`. Current IDs are Google Pay `com.google.android.apps.nbu.paisa.user`, PhonePe `com.phonepe.app`, and Paytm `net.one97.paytm`. Confirm the installed app’s package ID on the target phone before changing an entry. Package IDs alone do not establish provider support; validate notifications on the Redmi only with explicit authorization.

To verify an ID, check the package name in the official app’s Google Play listing URL (`id=...`) and confirm that the phone has that same official app installed. If an ID changes, update only the matching enum entry in `PaymentProviderAllowlist.kt` and add/update synthetic parser cases before on-device validation. References: [Google Pay for India package documentation](https://developers.google.com/pay/india/api/android/in-app-payments), [PhonePe Google Play listing](https://play.google.com/store/apps/details?id=com.phonepe.app), [Paytm Google Play listing](https://play.google.com/store/apps/details?id=net.one97.paytm).

The parser rejects non-allowlisted packages, ambiguous direction, debit/pending/failure/reversal wording, missing or multiple amounts, and amounts without a currency marker. It does not store or log notification text. Test with `./gradlew test`; tests use synthetic text only.

## Build

Open this folder in Android Studio or run:

```sh
./gradlew assembleDebug
```

The project uses Android Gradle Plugin 9.5.0 with its built-in Kotlin support, Gradle 9.6.0, Java 17, compile SDK 37, and target SDK 35. The minimum Android version is Android 6.0 (API 23); the target Redmi Note 8 Pro runs Android 11 (API 30).
