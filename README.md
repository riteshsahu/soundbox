# SoundBox

SoundBox is a small native Android app that can announce confidently identified incoming UPI payment notifications in Hindi and keep a private, on-device payment history.

## What it does

- Listens only to notifications from the exact Google Pay, PhonePe, and Paytm package IDs in [`PaymentProviderAllowlist.kt`](app/src/main/java/com/soundbox/notifications/PaymentProviderAllowlist.kt).
- Announces and records only payments the conservative parser identifies as successful incoming credits. Ambiguous, debit, pending, failed, reversed, amount-only, unrelated, and non-allowlisted notifications are ignored.
- Uses the installed Hindi text-to-speech voice. Announcements are off until enabled by the user.
- Stores only the amount, received timestamp, and source app. History is kept locally, pruned after three months, and excluded from Android backup.
- Calculates today's total using the device's local date, independently of history filters.
- Offers an optional, bounded speech loudness boost when Android supports it. It applies to SoundBox's audio only and cannot exceed the device's hardware limits.

Notifications are convenience signals, not proof that funds have settled. Provider package IDs and synthetic parser tests do not establish that a provider's current notification format is supported. Validate real notifications on the intended device before relying on them; do not use real notification text as a test fixture.

## Privacy

The app has no internet permission, account, backend, analytics, ads, or remote speech service. Android notification access is granted by the user in system settings and may expose notifications beyond these payment apps to the listener service; SoundBox immediately ignores every package outside its exact allowlist. It does not retain or log notification text, payer/account details, UPI IDs, phone numbers, or transaction/reference IDs.

The manifest disables Android backup. Local history uses private app storage and device protection; it does not use a separate database-encryption layer. See [`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) for the detailed product and privacy requirements.

## Build and test

Use the checked-in Gradle wrapper and Java 17:

```sh
./gradlew assembleDebug
./gradlew test
```

Open the project in Android Studio to install it on a development device. The app requires Android 6.0 (API 23) or newer. It does not request `POST_NOTIFICATIONS`; notification-listener access must be enabled by the user in Android settings. Hindi speech requires an installed on-device Hindi TTS voice.

Unit tests use synthetic text only. Validate real notifications only on a device with its owner's explicit authorization.

## Current status

The repository includes the app foundation, setup/home/history screens, conservative notification parsing, local history rules, Hindi speech, and optional app-owned loudness enhancement. Provider notification formats, speech behavior, and loudness boost still require validation on each intended device. SoundBox does not claim bank verification or universal provider compatibility.

## License

The SoundBox source code is available under the MIT License; see [`LICENSE`](LICENSE). Bundled provider logos are third-party brand assets and are not relicensed by the MIT license. Their source notes are in [`provider-icon-sources.txt`](app/src/main/assets/provider-icon-sources.txt); the marks remain subject to their owners' rights and applicable source terms.
