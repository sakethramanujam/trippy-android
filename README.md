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

## Try it with fake data

`scripts/seed_demo.py` fills an empty Trippy database with made-up trips so you can try the
app without your own data:

```sh
cd /path/to/trippy
export TRIPPY_DATABASE_URL=sqlite:////tmp/trippy-demo.db TRIPPY_SECRET_FILE=/tmp/trippy-demo.jwt
python /path/to/trippy-android/scripts/seed_demo.py
uvicorn app.main:app --port 8000
```

Then log in as `alex@example.com` / `demo-password-123!`.

## License

MIT, see [LICENSE](LICENSE).
