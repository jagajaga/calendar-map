# App Store (iOS): setup and submission checklist

Bundle ID: **me.jagajaga.calendarmap**, the same identity as on Android.

## 1. One-time setup (after joining the Apple Developer Program)

1. **Register the bundle ID** at developer.apple.com → Certificates, IDs &
   Profiles → Identifiers → "+" → App IDs → App. Bundle ID (explicit):
   `me.jagajaga.calendarmap`. No extra capabilities are needed.
2. **Create the app** in App Store Connect → Apps → "+" → New App: platform
   iOS, name **Calendar Map**, primary language English, bundle ID above,
   SKU `calendarmap`.
3. **Create an API key for CI**: App Store Connect → Users and Access →
   Integrations → App Store Connect API → Team Keys → "+". Role: **App
   Manager** (or Admin). Download the `.p8` file (you can only download it
   once) and note the **Key ID** and **Issuer ID**.
4. **Find your Team ID**: developer.apple.com → Membership details.
5. **Add four repository secrets** (GitHub → Settings → Secrets and
   variables → Actions):

   | Secret | Value |
   | --- | --- |
   | `ASC_KEY_ID` | the key's Key ID |
   | `ASC_ISSUER_ID` | the Issuer ID |
   | `ASC_KEY_P8` | the full text of the `.p8` file |
   | `APPLE_TEAM_ID` | your 10-character Team ID |

   With these set, every commit to `main` is archived, signed (Xcode
   cloud-managed signing via the API key) and uploaded to App Store Connect.
   It shows up in **TestFlight** after Apple's processing (about 5–30 min).
6. In TestFlight, add yourself as an **internal tester** and install the
   TestFlight app on your iPhone. New builds then arrive automatically.

## 2. Store listing

| Field | Value |
| --- | --- |
| Name (30) | Calendar Map |
| Subtitle (30) | Your events on a map |
| Category | Productivity (secondary: Navigation) |
| Keywords (100) | calendar,events,map,route,planner,itinerary,schedule,conference,agenda,meetup |
| Promotional text (170) | See where your events are and plan a route to make as many as you can, with free time between them worked out for you. |
| Support URL | https://github.com/jagajaga/calendar-map/issues |
| Marketing URL | https://jagajaga.me |
| Privacy policy URL | https://jagajaga.me/calendarmap/ |
| Copyright | 2026 Arseniy Seroka |

Description: use `description-en.md` (full description). Two changes for
iOS: say "calendars on your iPhone (iCloud, Google, Exchange…)" instead of
"on your phone", and "Apple Maps or Google Maps" for the hand-off.

**Screenshots** (6.9" iPhone, 1320×2868 or 1290×2796, at least 3): the iOS CI
job saves demo-mode screenshots (made-up San Francisco events) as the
`ios-screenshots-N` artifact on every run. Use those, or take your own.

## 3. App Privacy ("nutrition label")

| Question | Answer |
| --- | --- |
| Do you or your third-party partners collect data from this app? | **Yes** |
| Location → Precise location | Collected; used for **App Functionality**; **not linked** to identity; **not used for tracking**. (Your coordinates go to the routing service when a route starts from your location.) |
| Other data types | Not collected |

Calendar data isn't one of Apple's listed types. Event location text goes to
Apple's own geocoder, and titles and notes never leave the device. The
privacy manifest (`ios/CalendarMap/PrivacyInfo.xcprivacy`) declares the same.

## 4. App Review information

- Sign-in required: **No**.
- Notes: *"Calendar Map shows events from the device's calendars on a map.
  To review, add a calendar event with a real address (for example
  '1 Ferry Building, San Francisco') in the next 7 days, open the app, tap
  Continue, and allow calendar and location access. To try route planning,
  add 2–3 events at different addresses, tap Plan route, pick them, then
  Build route."*
- Export compliance: the app uses only standard HTTPS
  (`ITSAppUsesNonExemptEncryption = NO` is already set), so no documents are
  needed.
- Age rating: answer "None" to everything → 4+.

## 5. Release

Pick a TestFlight-tested build on the version page → Add for Review →
Submit. Unlike Google Play, there's no 14-day closed-test requirement.
