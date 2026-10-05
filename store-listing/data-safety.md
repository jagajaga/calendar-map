# Google Play — Data safety form (draft answers)

These are drafted from what the code does (see the privacy policy). You are
the one declaring them to Google, so check them against the current Play
definitions when you fill in the form.

## Overview questions

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (all requests use HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **No** (the developer keeps no user data; uninstalling deletes everything on the device) |

## Data types

Play counts data as **collected** when the app sends it off the device,
even to a third-party service that only processes it to answer the request.
Mark both as **processed ephemerally**.

### Location → Approximate location and Precise location
- Collected: **Yes**. Shared: **No**.
- Processed ephemerally: **Yes**.
- Required or optional: **Optional** (only sent when a route starts from
  your location; "Start from my location" can be turned off).
- Purpose: **App functionality**.
- Why: the route planner sends your coordinates to routing.openstreetmap.de
  to get travel times and the road route.

### Calendar → Calendar events
- Collected: **Yes**. Shared: **No**.
- Processed ephemerally: **Yes**.
- Required or optional: **Required** (needed to place events on the map).
- Purpose: **App functionality**.
- Why: an event's location text is sent to the device's geocoding service
  (usually Google), and the coordinates of events in a route are sent to the
  routing service. Titles, descriptions and guests never leave the device.

### Everything else
Not collected: personal info, financial info, health, messages, photos,
audio, files, contacts, app activity, web browsing, app info and
performance, device or other IDs.

## Why "Shared: No"
Play does not count it as sharing when data goes to a service that
processes it on the app's behalf, or when the user starts the transfer.
Opening Google Maps or the calendar app from an event is user-initiated.
