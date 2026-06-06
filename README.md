# Accept Assist

Accept Assist is a small native Android app for testing your own app flows. It uses Android Accessibility to find a visible, enabled node matching your configured text or view id, then clicks it after a configurable 50-100 ms delay.

Use it only with apps you own or have permission to automate.

## Build

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on a real device or emulator.

## Device Setup

1. Install and open Accept Assist.
2. Tap **Open accessibility settings**.
3. Enable **Accept Assist Clicker**.
4. Return to Accept Assist.
5. Enter your custom app package name, for example `com.example.myapp`.
6. Set the target text, usually `Accept`.
7. Set delay between `50` and `100` ms.
8. Turn on **Enable auto-click** and tap **Save settings**.

## Test

Tap **Use this app as test target**, then **Show test popup**. If Accessibility is enabled, the service should click the popup's **Accept** button after the configured delay.
