# Maik Kiosk prototype

Android kiosk/WebView prototype for Galaxy Tab S9.

## Current behavior
- Opens https://www.google.de by default.
- Fullscreen immersive WebView.
- Keeps the Android display awake while the app runs.
- After 60 seconds without detected camera motion, shows a black overlay.
- Uses the front camera via CameraX and wakes the UI when average image luminance changes enough.
- Camera frames are analyzed in memory only; they are not saved or transmitted by the motion detector.

## Build
Open the MaikKiosk folder in a recent Android Studio, let Gradle sync, then use Build > Build APK(s).
On first launch grant camera permission.

## Important prototype limitation
The black screen is an app overlay; this version deliberately does NOT physically switch off the Android display. That allows the front camera analyzer to remain active without Device Owner / privileged kiosk configuration.

## URL
The URL is stored under the SharedPreferences key `url`. Version 0.1 does not yet include the admin settings UI; the default is Google.
