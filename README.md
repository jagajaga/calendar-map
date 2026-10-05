# Calendar Map

An Android app that puts the events from your calendars on a map.

- Reads every calendar synced to the phone (Google, Exchange, local…) via the
  system calendar provider. No sign-in, no API keys.
- Shows the events in the part of the map you're looking at. Pins are only
  created for the visible area and update as you pan and zoom. Or switch to
  "within a radius of me" (default **50 km**, adjustable from 1 km up to
  20,000 km).
- Pick which calendars to include.
- Pick the time range: Today, Tomorrow, Next 7 days, Next 30 days, or any
  custom date range.
- Every pin shows the event's start and end time. Events at the same place
  share one pin.
- Tap a pin to see the event details, open the place in **Google Maps**, or
  open the event in your calendar app.
- **Plan route**: pick up to 10 events (tap pins or tick them in the list)
  and the app works out which ones you can actually attend and in what
  order. It respects each event's start and end time, a minimum stay
  (15 min, 30 min, 1 h, or the whole event), and travel by foot, bike, or
  car. You get the road route on the map, arrive and leave-by times, the
  free time between events ("you have 2 h 30 min free to do whatever you
  want"), the events that don't fit, and a button that opens the route in
  Google Maps.
  Travel times come from the OpenStreetMap routing service
  (routing.openstreetmap.de), with straight-line estimates when offline.
- The list view shows every event in the range, including ones farther away
  and ones without a recognizable place.

Event locations are turned into coordinates with Android's built-in geocoder
and cached on the device. Online-meeting locations (Zoom, Meet, Teams, URLs)
are skipped. The maps are from [OpenStreetMap](https://www.openstreetmap.org/copyright).

## Install

Every commit to `main` publishes a signed APK on the
[Releases page](../../releases). Download the latest `calendar-map-*.apk` on
your phone, open it, and allow installs from your browser when asked.
Every build is signed with the same key, so new builds install over the
old one.

APKs for every other commit and pull request are in the run's **Artifacts**
section under [Actions](../../actions). Artifacts download as a zip, and you
must be signed in to GitHub to get them.

## Google Play

Store listing text, graphics, the privacy policy, Data safety answers and a
submission checklist are in [`store-listing/`](store-listing/). Start with
[`store-listing/play-console.md`](store-listing/play-console.md). The
privacy policy is published at https://jagajaga.me/calendarmap/ (source:
`docs/calendarmap/index.html`). Each release has an `.aab` for Play next to
the `.apk`. Later uploads can go through the manual **Publish to Google
Play** workflow.

## Build locally

Requires JDK 17 and the Android SDK (platform 36).

```sh
./gradlew testReleaseUnitTest assembleRelease
```

Without the signing environment variables, local release builds are signed
with your debug key.

## CI signing

`.github/workflows/build.yml` reads these repository secrets:

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of the release `.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

If the secrets are missing (for example, in pull requests from forks), the
build falls back to a debug key, and that APK won't install over a release build.
