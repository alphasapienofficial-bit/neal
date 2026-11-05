# Neal App

A workout timer, recipes and daily food guide, packaged for Android with Capacitor.

- The app is one page: `www/index.html`.
- Each push to `main` builds an APK with GitHub Actions. Download it from the run's **Neal-App-apk** artifact.
- `android-debug.keystore` is a debug key that keeps the signature the same, so new builds install over the old app. It is not a Play Store key.
