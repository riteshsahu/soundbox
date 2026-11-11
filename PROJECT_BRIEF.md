# Codex project brief: SoundBox

**Project name:** SoundBox

## Paste this prompt into a new Codex project

Build **SoundBox**, a polished, trustworthy Android app for my father’s phone. When a successful incoming payment notification arrives from Google Pay, PhonePe, or Paytm, announce the received amount aloud in Hindi. Also keep a locally stored history of received payments for the last three months and show today’s collection.

This is for personal/family use. Prioritize clear behavior, a carefully designed, accessible UI, low permissions, and code that is easy to inspect. Keep all app data on the device. Payment announcements must be in Hindi only. Let the visible app UI switch between Hindi and English, with English as the default; do not offer other UI languages or add unrelated features. Do not add features beyond this brief without asking.

### First: inspect and plan

1. Inspect the existing project and any `AGENTS.md` instructions. If this is a new project, propose a minimal Android-native Kotlin setup.
2. Ask for the target phone’s Android version/model only if it is not available in the project context. Continue useful setup work that does not depend on this information.
3. Before implementation, give me a short plan and identify Android-version-specific constraints affecting notification access, background behavior, Hindi text-to-speech, audio enhancement, or local data protection.
4. Use these projects as references, not trusted dependencies:
   - UPI Payment Alert: https://github.com/official-arvind/UPIPaymentAlert — its source checkout was reviewed. Reuse ideas selectively, not its permission, storage, or forwarding design.
   - RupeeRing: https://github.com/MunasirPv/RupeeRing — product flow and notification-based approach.
   - UPI Alert System: https://github.com/Justin-Titus/UPI-Alert-System — secondary reference only.
5. The reviewed UPI Payment Alert checkout declares SMS read/receive/send, internet, location, write-settings, and broad package visibility permissions; has opt-in code that forwards full SMS/notification text to a configured phone number; stores full transaction bodies and latest message text; allows backup; and includes an external Google Fonts URL in exported HTML. No app-author server upload code was found in the inspected source, but do not claim the app is fully offline.
6. Its listener/parser, Room transaction model, and volume handling are not privacy-safe or reliable enough to copy as-is. In particular, it accepts broad/dynamic UPI app matching, uses heuristic parsing including bare-number fallback, stores debit and credit records, and temporarily changes global music volume. Reimplement these parts to this brief.
7. Reuse small code pieces only after reviewing the exact file, verifying MIT license compatibility, removing unnecessary behavior, and adding synthetic tests. Prefer a small implementation we can audit.

### Core behavior

- Use Android’s `NotificationListenerService` to observe notifications.
- Process notifications only from the installed Google Pay, PhonePe, and Paytm app package IDs. Keep the package allowlist in one clearly named place and explain how to verify/update it if app package IDs change.
- Announce and record only notifications that confidently indicate money was received/credited. Never announce or record sent, paid, debited, pending, failed, reversed, or ambiguous transactions.
- Extract amount, timestamp, and source app conservatively. Do not infer a successful payment from a generic amount-only message. If direction or amount is ambiguous, do not speak or add a transaction.
- Speak a short Hindi phrase with the amount, such as “पाँच सौ रुपये प्राप्त हुए”. Use Android text-to-speech. Provide a setup check and test-speech button so I can confirm a Hindi voice is available on the phone.
- Add basic controls: enable/disable announcements, test speech, loudness controls, and a clear status showing whether notification access and Hindi TTS are ready.
- Avoid duplicate announcements and duplicate transaction records for the same payment where practical. Deduplicate using notification metadata and conservative amount/source/time matching; do not retain UPI IDs or reference numbers just for deduplication.
- Handle audio focus and queue announcements politely so they do not get cut off or overlap.

### Loudness Boost

- Add an optional **Loudness Boost** control specifically for the app’s spoken payment message. Provide an off/normal/boosted setting or a simple, accessible slider with a sensible default and a test button. The reviewed reference uses global media-volume adjustment plus `LoudnessEnhancer`; do not copy that implementation.
- First use the phone’s normal media/speech volume. Where supported, investigate Android’s `LoudnessEnhancer` attached to the app’s own audio session, or another supported app-owned audio processing path. The Android API describes gain relative to the signal; device and OEM behavior can differ. Do not promise sound louder than the phone’s hardware maximum.
- Prefer not to change the phone’s global volume at all. Do not copy the reference’s global stream volume override. If a supported design absolutely requires temporary adjustment, save and restore the exact prior value in all success/error/interruption/service-destruction paths, and explain this behavior.
- Keep boost bounded and user controlled. Handle unsupported effects gracefully, avoid clipping/distortion, and test on the target phone at low, normal, and high settings. Clearly show when boost is unavailable or has no measurable effect.
- Do not claim this can safely exceed the phone’s hardware output limit. If the phone is still too quiet, suggest an external speaker as an optional practical fallback.

### Local transaction history and today’s collection

- Store only confidently detected **received payments** for the most recent three months. Automatically prune records older than three months.
- Keep records on-device only. Store the minimum needed for the history and totals: amount, received timestamp, and source app. Do not store raw notification text, payer names, account details, UPI IDs, phone numbers, or transaction/reference IDs.
- Use a local database such as Room. No network permission or sync. Consider encrypting locally stored payment data with Android Keystore-backed keys if this can be done reliably without making recovery fragile; explain the tradeoffs and never add a cloud backup.
- Provide a history screen showing received payments with date/time, amount, and source app. Include practical filters such as date range (within the retained three months), source app, and amount range. Make it easy to reset filters and show an empty state.
- Show **Today’s collection** as the sum of today’s unique received-payment records. State the date used (device local date). Ensure filter choices do not silently change this headline total; if a filtered subtotal is also shown, label it clearly.
- Provide a clear “Clear local history” action with confirmation. Do not export or share transaction records in the first version.

### UI/UX direction

- Design the UI deliberately for an older adult: simple navigation, large readable text, strong contrast, generous touch targets, a clear Hindi/English language choice (English by default), and no dense dashboards. Changing the UI language must not change the Hindi-only payment announcement voice.
- Make the home screen answer three things quickly: Is listening enabled? What is today’s collection? What was the latest received payment?
- Give the main actions clear visual priority: enable/setup listening, test speech, and view history. Avoid clutter, decorative charts, tiny icons without labels, and hidden gestures.
- Use a calm, high-contrast visual system and consistent spacing. Support larger system font/display settings and screen readers. Do not rely on color alone for status.
- Provide useful loading, permission-needed, TTS-unavailable, no-transactions, and error states with direct recovery steps.
- Before implementation, describe the proposed screen structure and visual hierarchy. After implementation, run the app or render screens where the environment allows and refine obvious layout/accessibility issues.

### Privacy and permission boundaries

- Request notification access only, plus `POST_NOTIFICATIONS` if needed for the app’s own foreground-service status notification. Do not request SMS, internet, location, `WRITE_SETTINGS`, `QUERY_ALL_PACKAGES`, or permissions for unrelated features. Explain that notification access is a sensitive Android setting and why this app needs it.
- Do not request SMS, contacts, call log, accessibility, microphone, location, or broad storage permissions.
- Do not add internet permission, analytics, advertising, crash reporting, cloud/backend services, account sign-in, remote configuration, or network-based TTS. Do not embed remote fonts, images, scripts, or other URLs in generated exports or screens. Speech must use an already installed on-device Hindi voice; if one is unavailable, explain how the user can install voice data through Android settings, without silently downloading it from the app.
- Keep payment parsing on-device. Persist only the minimal transaction fields listed above. Discard raw notification content immediately after parsing and never log it, even in debug builds. Disable Android backup for the app/database so transaction history is not copied to cloud/device backups.
- The notification listener may be granted broad notification visibility by Android. Immediately ignore non-allowlisted packages and never store, transmit, or log their content. Do not access old notification history unless strictly needed for the stated behavior.
- Make clear in the app that transaction history is local and can be cleared from the app. Do not claim Android notification access is limited to the three payment apps if the OS setting grants broader visibility.

### Reliability and safety

- Treat notifications as a convenience signal, not proof that funds settled. Do not describe a notification as bank-verified.
- Respect Android notification visibility and app notification settings; never try to bypass them.
- Do not use `AccessibilityService`, screen scraping, hidden APIs, or automation to open/read payment apps.
- Handle listener disconnect/reconnect and app restarts without making unsupported “unkillable” claims.
- Make parser rules explicit and conservative. Use synthetic examples only; never commit real financial data or notification samples to the repository.
- Provide safe development diagnostics that report only non-sensitive outcomes such as source package category and which parsing rule matched, never raw text or extracted personal data.

### Suggested milestones

1. **Foundation and design:** Minimal native Kotlin project, screen flow, visual system, and accessible setup/home/history mock UI.
2. **Speech and listener:** Notification access status, Hindi TTS check/test, package allowlist, and conservative parser.
3. **History and totals:** Local database, three-month retention, deduplication, today’s collection, history and filters, clear-history flow.
4. **Loudness Boost:** Optional per-app audio enhancement, graceful fallback, and target-device tuning.
5. **On-device validation:** Test with synthetic parser fixtures, then small real incoming payments on my father’s phone. Do not put captured real notification text in source control.

Before claiming support for Google Pay, PhonePe, or Paytm, validate against real notifications on the target phone. If a provider’s notification does not expose enough reliable information, explain the limitation instead of guessing.

### Engineering expectations

- Use current stable Android/Kotlin tooling already present in the project; if creating a project, choose a minimal supported setup and explain the choice.
- Keep listener, parser, speech output/audio enhancement, local data, and UI responsibilities separate.
- Add tests for parser decisions using synthetic examples. Include incoming credits and negative cases for debits, failures, pending/reversed transactions, unrelated notifications, missing amounts, and ambiguous wording. Test deduplication, three-month pruning, and today’s collection calculation.
- Do not add tests unrelated to this scope.
- Do not build or install an APK on a personal device, change phone settings, or access real notifications unless I explicitly ask.
- After implementation, report changed files, declared permissions, locally persisted fields and retention, privacy-sensitive data paths, audio boost behavior/limits, and what still needs on-device validation.

### Definition of done

- The app builds successfully and has a polished, accessible home/setup/history experience.
- The setup screen accurately shows whether notification access and Hindi TTS are available.
- A synthetic high-confidence incoming-payment notification from an allowlisted package would be parsed, spoken in Hindi, and saved once. Debit, failed, pending, reversed, ambiguous, and non-allowlisted notifications would not be spoken or saved.
- The history contains only received payments from the last three months, supports the stated filters, and can be cleared. Today’s collection correctly sums today’s unique received transactions in local time.
- Loudness Boost is optional, bounded, user controlled, gracefully unavailable when unsupported, and tested on the target device. The app does not claim to exceed hardware limits or leave system volume altered.
- No raw notification text, payer details, or transaction identifiers are persisted or logged. All app data remains local; the manifest contains no internet, SMS, contacts, accessibility, or storage permission.
- I receive clear instructions for testing on the target phone and the limitations of notification-based detection.
