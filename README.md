# Calm

A personal Android app that intercepts notifications, lets urgent ones through immediately and
holds everything else for scheduled digests. Android only: iOS doesn't let apps read or suppress
other apps' notifications.

Flutter renders the UI. The whole pipeline (interception → classification → storage) runs natively
in a Kotlin `NotificationListenerService`, so it works without the Flutter engine running.

**Status:** milestone 3: storage, rules, Do Not Disturb silencing, scheduled digests and optional AI
classification for messages no rule covers.

## How it works

1. The listener extracts every notification and keeps only *new* messages: messaging apps repost
   the whole conversation on each message, so messages are deduplicated by key, sender, time and text.
2. Each new message is classified: always-urgent (calls, alarms, calendar, 2FA codes, VIPs) →
   your custom rules → always-hold (hold list, muted groups, promo/social) → AI → fallback: urgent.
   Messages that reach the AI wait about 3 s per conversation, so a burst becomes one AI call.
3. Held messages are stored and the original notification is cancelled. Urgent ones are re-posted
   on Calm's "Urgent" channel, which rings through DND. The copy removes itself after a minute and
   the original (with reply buttons) stays in the shade.
4. At each digest time Calm posts one summary ("12 held notifications from 5 apps") that opens the
   digest screen.

While Calm is on, it keeps its own Do Not Disturb mode active so nothing else can buzz before Calm
has decided. Calls, repeat callers, alarms and media still come through. On Android 15+ this is a
separate "Calm" mode that leaves your own DND setup alone. On older versions Calm saves your DND
settings and restores them when you turn Calm off. When Calm is off, it does nothing and records
nothing.

## Setup

Requirements: Flutter (stable), Android SDK 36, JDK 17+, an Android 8.0+ phone (API 26).

```sh
flutter pub get
flutter run            # debug build on a connected phone
flutter build apk      # release APK in build/app/outputs/flutter-apk/
```

Install via `flutter run` or `adb install`. If you sideload the APK from a file manager or browser
instead, Android 13+ blocks notification access for it ("Restricted setting"). To unblock it, open
App info → ⋮ → *Allow restricted settings*, then grant access.

## Permissions

| Permission | Why | Milestone |
|---|---|---|
| Notification access | Read incoming notifications | 1 |
| Query all packages | Show app names and open the source app | 1 |
| Post notifications | Re-post urgent notifications and digest summaries | 2 |
| Do Not Disturb access | Keep non-urgent notifications from buzzing | 2 |
| "Override Do Not Disturb" on the Urgent channel | Let Calm's own alerts ring | 2 |
| Exact alarms (optional) | Deliver digests on time, otherwise up to 10 min late | 2 |
| Battery optimization exemption | Keep the service alive on aggressive OEMs | 4 |

## API key (optional)

1. Create a key at [console.anthropic.com](https://console.anthropic.com) → API Keys.
2. In Calm, open Home → ⚙ Settings → *Anthropic API key*, paste it and save.
3. Use *Test classification* in the same screen to check that it works ("AI" in the result line).

The key is encrypted with an Android Keystore key (AES-GCM) and only ever sent to Anthropic. The
model defaults to `claude-haiku-5-5` and can be changed in Settings. Without a key, or with AI
switched off, Calm runs on rules only and lets anything unknown through.

**What is sent:** app name, conversation name, sender name, whether it's a group, the message
text and the current local time. Phone numbers and email addresses are replaced first, and a sender
that is only a phone number becomes "unknown contact". Package names and contact details are never
sent.

**Fail open:** if the API takes longer than 5 s, returns an error, refuses, answers with invalid
JSON or reports a confidence below 0.6, the message is let through. The log shows these as
"Fallback" with the reason.

## Tests

```sh
flutter test                              # Dart
(cd android && ./gradlew :app:testDebugUnitTest)   # Kotlin
```

## Privacy

There are no accounts, no backend and no analytics. Release builds write nothing to logcat, and
debug builds log only metadata (package names, counts), never notification content.
