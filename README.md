# Trippy for Android

The Android client for [Trippy](https://github.com/sakethramanujam/trippy), a self-hostable,
Flighty-style trip tracker. Flights, stays (hotels and Airbnbs), rental cars and journeys,
shared with the people you travel with, against a server you run yourself.

Kotlin, Jetpack Compose and Material 3, one activity, no DI framework.

## Build

Requirements: Android Studio (or JDK 17+) and an Android SDK with platform 37.

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # data layer and mapper tests
```

## Point it at a server

On first launch, tap **Server** and enter your Trippy server URL, for example
`https://trippy.example.com` or `http://192.168.1.20:8000`.

Plain `http` is only allowed for local hosts (see
`app/src/main/res/xml/network_security_config.xml`). The emulator reaches your computer at
`10.0.2.2`, so a server started with `uvicorn app.main:app --port 8000` on your machine is
`http://10.0.2.2:8000` from the emulator. If your LAN server uses a different private IP,
add it to that file.

### Android 17: the Nearby devices permission

Android 17 treats connections to the local network (private IPs such as `192.168.x.x`,
`10.x.x.x` and `172.16-31.x.x`, and `.local` names) as a runtime permission. Trippy
declares `ACCESS_LOCAL_NETWORK`, which Android shows as **Nearby devices**, and asks for it
the first time you point the app at a LAN server. If you deny it, requests to that server
fail with a "can't reach your server" error; turn it back on in **Settings > Apps > Trippy >
Permissions > Nearby devices**. Public servers and Tailscale (`100.x.x.x`) addresses don't
need it. The emulator's `10.0.2.2` is a `10.x` address, so the app asks there too; to skip
the prompt while testing, run
`adb shell pm grant samalabs.trippy android.permission.ACCESS_LOCAL_NETWORK`.

## Try it with fake data

`scripts/seed_demo.py` fills an empty Trippy database with made-up trips so you can try the
app without your own data:

```sh
cd /path/to/trippy
export TRIPPY_DATABASE_URL=sqlite:////tmp/trippy-demo.db TRIPPY_SECRET_FILE=/tmp/trippy-demo.jwt
uvicorn app.main:app --port 8000 &
python /path/to/trippy-android/scripts/seed_demo.py http://localhost:8000
```

Then log in as `alex@example.com` / `demo-password-123!`.

## On the device

- **Offline:** the last trips, rentals and journeys you loaded are kept in a local Room
  database (per account, wiped on sign-out) and shown with an "Offline · synced …" banner
  when the server can't be reached.
- **Reminders:** turn on **Trip reminders** in your profile. Trippy schedules local
  notifications for check-in opening, 3 hours before each flight and the morning of hotel
  check-in, for the next 14 days. They use WorkManager, so Android may deliver them a few
  minutes late under battery saving.
- **Widget:** **Add home-screen widget** in your profile (or the launcher's widget picker)
  shows your next trip and next flight.
- **Live status:** flight pages show gates, delays and baggage belts when your server's admin
  has configured a flight data provider.
- **Google Maps Timeline:** **Import → Google Maps Timeline** takes the `Timeline.json` you
  export from Google Maps (Settings → Location & privacy → Export Timeline data). The phone
  strips the raw location signals before uploading, and by default only drives during your
  trips are offered; you pick which ones to keep. Drives show up as driving days on trips,
  journeys and stories (with the route drawn), and in the Driver's Log, which can also
  suggest the drive that was your rental pickup or drop-off. Drives are private to you.
- **Photos:** a trip's **Photos** section adds pictures with the system Photo Picker, which
  needs no permission. **Suggest from trip dates** asks for photo access, then offers photos
  taken during the trip and near its places (from the photo's date and GPS); you keep or skip
  each one. On Android 14+ you can give access to selected photos only. Full photos never leave
  the phone: Trippy uploads only a small thumbnail (512 px at most) and the photo's on-device
  id. Photos show on the trip, in its story, and as an optional share-card background.
  Removing a photo from a trip never deletes it from the phone. Because this build is
  sideloaded, it declares `READ_MEDIA_IMAGES` for suggestions, a permission Google Play
  limits.

## License

MIT, see [LICENSE](LICENSE).
