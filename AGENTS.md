# Repository Guidelines

## Project Structure

This repository contains the SoundBox product requirements and a small native Kotlin Android app. Keep production code under `app/src/main/`, resources under `app/src/main/res/`, local unit tests under `app/src/test/`, and Android instrumentation tests under `app/src/androidTest/`. Separate notification listening, conservative payment parsing, speech/audio, local storage, and UI responsibilities. Keep the provider package allowlist in one clearly named location.

## Build, Test, and Development

Use the checked-in Gradle wrapper rather than a separately installed Gradle:

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew test` runs local unit tests.
- `./gradlew connectedAndroidTest` runs instrumented tests on a connected emulator/device.

Do not install an APK on a personal device or change phone settings without explicit authorization. Follow `PROJECT_BRIEF.md` before implementing features.

## Kotlin Style and Privacy

Follow Kotlin conventions: four-space indentation, `UpperCamelCase` for types, `lowerCamelCase` for functions/properties, and descriptive names. Treat `PROJECT_BRIEF.md` as the product specification. Preserve these requirements in all changes:

- Use `NotificationListenerService`; immediately ignore every package except the explicitly allowlisted Google Pay, PhonePe, and Paytm packages. Never access old notification history or bypass Android notification settings.
- Announce and record only confidently identified successful incoming credits. Ambiguous, debit, pending, failed, reversed, amount-only, and unrelated notices must be ignored. Notifications are convenience signals, not proof of settlement.
- Speak a short Hindi phrase using installed on-device Hindi TTS. Provide readiness status and test speech. Queue announcements politely, handle audio focus, and deduplicate without retaining transaction identifiers.
- Store locally only amount, received timestamp, and source app. Prune after three months; disable backup. Do not retain or log notification text, payer/account details, UPI IDs, phone numbers, or reference IDs. No network permission, sync, analytics, ads, remote TTS, or cloud services.
- Request only notification access and, if needed for the app’s foreground notification, `POST_NOTIFICATIONS`. Never add SMS, contacts, call log, accessibility, location, broad storage/package, internet, or `WRITE_SETTINGS` permissions.
- Today’s collection sums unique records for the device’s local date, independent of history filters. Include history filters, reset, empty state, and a confirmed clear-history action.
- Loudness boost is optional, bounded, app-owned, and may be unavailable. Do not change global volume or promise output beyond hardware limits; handle unsupported audio effects gracefully.

Design for older adults: Hindi-first labels, large readable text, strong contrast, generous touch targets, screen-reader support, and explicit permission/TTS/error states. Avoid unrelated features; ask before expanding scope.

## Testing

Add focused synthetic tests for parser decisions, including debit, failed, pending, reversed, ambiguous, missing-amount, unrelated, and non-allowlisted cases. Cover deduplication, three-month pruning, and today’s total in device-local time. Do not use captured financial notifications as fixtures. Validate each provider with synthetic tests first and real notifications on the target phone only with explicit authorization. Never claim provider support until validated on that phone; explain when notification content is insufficient. Do not build/install on a personal device, change phone settings, or access real notifications without explicit authorization.

## Commits and Pull Requests

There are no existing commits to establish a message convention. Use short imperative summaries, such as `Add conservative payment parser`. Pull requests should explain the behavior and privacy impact, list changed permissions and stored fields, include relevant test results, and provide screenshots for UI changes. Link related issues when available.
