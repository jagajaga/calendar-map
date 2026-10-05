# Google Play Console — submission checklist

Package name: **me.jagajaga.calendarmap**. This can never change after the
first upload.

## 1. Before the first upload

- [ ] **Developer account type.** Personal accounts created after
      13 Nov 2023 must run a **closed test with at least 12 testers opted in
      for 14 days in a row** before they can apply for production access.
      Start that early. Organisation accounts are exempt.
- [ ] **App signing — pick one:**
  - **Recommended: keep one key everywhere.** On "App integrity → App
    signing", choose *Use a different key / Export and upload a key from
    Java keystore* and upload the existing key at
    `~/.android-keys/calendar-map-release.jks` with Google's PEPK tool
    (Console shows the exact command). Play-installed builds and the APKs on
    GitHub Releases then have the same signature and can update each other.
  - *Alternative: Google-generated key.* Simpler, but builds from Play and
    from GitHub can't update over each other (users must uninstall to
    switch). The CI key then only acts as the upload key.
- [ ] **Back up the keystore and its password**
      (`~/.android-keys/calendar-map-release.{jks,txt}`) in a password
      manager. Losing it means you can't ship updates (or, with Google-managed
      signing, you have to request an upload-key reset).

## 2. The build to upload

- Every commit to `main` publishes `calendar-map-1.0.N.aab` (plus the APK)
  on GitHub Releases. Upload the `.aab` in Console.
- `versionCode` is the CI run number, so it always goes up.
- Targets API 36 (Android 16), which Play requires for new apps since
  31 Aug 2026.
- Later uploads can be automated: create a Google Cloud service account with
  release permissions in Console (Users and permissions), save its JSON key
  as the repository secret `PLAY_SERVICE_ACCOUNT_JSON`, then run the
  **Publish to Google Play** workflow (Actions tab), picking a release and
  a track. The very first upload must be manual.

## 3. Store listing (Grow → Store presence → Main store listing)

- Name, short and full description: `description-en.md`, `description-ru.md`
- App icon (512×512): `graphics/icon-512.png`
- Feature graphic (1024×500): `graphics/feature-graphic-1024x500.png`
- **Phone screenshots (2–8, required)**: take these on your phone. Use a
  demo calendar with public, made-up events, because real event titles and
  addresses become public. Good shots: (1) map with pins and times,
  (2) event details, (3) route on the map, (4) itinerary with free time,
  (5) settings with calendars.
- Category: Productivity. Contact email: required, use one you're fine
  being public. Website: https://jagajaga.me

## 4. App content (Policy → App content)

| Section | Answer |
| --- | --- |
| Privacy policy | https://jagajaga.me/calendarmap/ |
| Ads | No, the app contains no ads |
| App access | All functionality is available without special access. Add a note: *"The app shows events from the device's calendars. To review, add a calendar event with a real address (e.g. 1 Market St, San Francisco) in the next 7 days, then open the app and grant calendar and location permissions."* |
| Content rating | Questionnaire, category *Utility, productivity, communication or other*: no violence, sexual content, profanity, drugs or gambling. Users can't interact or share content; the app doesn't share the user's location with other users. Expected rating: Everyone / PEGI 3. |
| Target audience | 18+ (keeps it out of the Families programme; nothing here is for children) |
| News app | No |
| Data safety | See `data-safety.md` |
| Government app | No |
| Financial features | None |
| Health | No |
| Permissions | `READ_CALENDAR`, location: foreground only, no background location. No sensitive-permission declaration form is needed. |

## 5. Open source notes

- Publishing open source on Play is fine. The MIT licence allows it, and
  nothing secret is in the repo: signing secrets live only in GitHub
  Actions secrets and `~/.android-keys/`.
- Third-party services:
  - The OpenStreetMap tile servers allow apps that send an identifying
    User-Agent (the app does) and show attribution (on the map and in
    Settings), but they're not meant for heavy traffic. If the app gets
    popular, switch to a commercial tile provider.
  - The FOSSGIS routing server is the same: free and fair-use.
