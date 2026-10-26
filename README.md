# Calm

A personal Android app that intercepts notifications, lets urgent ones through immediately and
holds everything else for scheduled digests. Android only: iOS doesn't let apps read or suppress
other apps' notifications.

Flutter renders the UI. The whole pipeline (interception → classification → storage) runs natively
in a Kotlin `NotificationListenerService`, so it works without the Flutter engine running.

**Status:** milestone 1, interception plus a live log. Nothing is silenced yet.

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
| Do Not Disturb access | Keep non-urgent notifications from buzzing | 2 |
| Exact alarms | Deliver digests on time | 2 |
| Post notifications | Re-post urgent notifications and digest summaries | 2 |
| Battery optimization exemption | Keep the service alive on aggressive OEMs | 4 |

## API key

AI classification arrives in milestone 3. The key will be entered in Settings and stored encrypted
on the device. Without a key, the app runs on rules only.

## Tests

```sh
flutter test                              # Dart
(cd android && ./gradlew :app:testDebugUnitTest)   # Kotlin
```

## Privacy

There are no accounts, no backend and no analytics. Release builds write nothing to logcat, and
debug builds log only metadata (package names, counts), never notification content.
