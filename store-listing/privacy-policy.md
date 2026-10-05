# Privacy Policy — Calendar Map

**Last updated: October 5, 2026** (now also covers the iPhone app)

Calendar Map ("the app") is developed by Arseniy Seroka ("we", "us"). It is
open source: https://github.com/jagajaga/calendar-map

## Summary

The app shows events from the calendars on your phone on a map. We run no
servers and receive no data from the app. There are no accounts, ads,
analytics or crash reporting. To draw maps, find places and plan routes,
the app sends a small amount of data directly from your phone to the
services listed below.

## Data the app reads on your device

- **Calendar events** (permission `READ_CALENDAR`): for each event in the
  time range you pick, its title, start and end time, location, description,
  calendar name and colour. These are shown in the app and are not sent
  anywhere, except the event's *location text* as described below.
- **Your location** (permissions `ACCESS_FINE_LOCATION` /
  `ACCESS_COARSE_LOCATION`, only while the app is open): used on your
  device to show where you are, to measure how far events are, and as the
  starting point of a route.

## Data sent over the internet, and to whom

| What | Sent to | When |
| --- | --- | --- |
| An event's location text (an address or place name) | The phone's geocoding service: on Android, the built-in one (on most phones, Google); on iPhone, Apple | The first time the app places that location on the map; the result is cached on your device |
| Which map area you are viewing (as map tile requests) and your IP address | On Android: OpenStreetMap Foundation tile servers (tile.openstreetmap.org). On iPhone: Apple Maps | Whenever map tiles are loaded |
| Coordinates of the events in a route, plus your current location if the route starts from it | OpenStreetMap routing service run by FOSSGIS e.V. (routing.openstreetmap.de) | Only when you build a route |

These services process the data to answer the request. Their own privacy
policies apply:
[Google](https://policies.google.com/privacy),
[Apple](https://www.apple.com/legal/privacy/),
[OpenStreetMap Foundation](https://osmfoundation.org/wiki/Privacy_Policy),
[FOSSGIS](https://www.fossgis.de/datenschutzerklaerung/).

When you tap **Open in Google Maps**, **Apple Maps** or **Calendar**, the app hands
that event's place or route to the app you open. From then on, that app's
privacy policy applies.

All network connections use HTTPS.

## Data stored on your device

- Your settings (time range, calendars, area mode, radius, route options)
- A cache of place lookups (location text → coordinates)
- A cache of map tiles

This data stays in the app's private storage. Uninstalling the app or
clearing its data in Android Settings deletes it.

## What the app does not do

- No accounts or sign-in
- No ads, no analytics, no tracking, no advertising ID
- No crash reports or diagnostics sent to us or anyone else
- No location access in the background
- Your calendar is never changed

## Children

The app is not directed at children and does not knowingly collect data
from anyone.

## Changes

If this policy changes, the new version will be posted at this address
with a new "Last updated" date. If the data the app sends changes, the app
shows its in-app explanation again.

## Contact

Arseniy Seroka — https://jagajaga.me ·
https://github.com/jagajaga/calendar-map/issues
