# Google Play release procedure

HappyTalky is one Play listing with two separately delivered form factors:

- **Mobile:** `mobile-release.aab` on the normal mobile track.
- **Wear OS:** `wear-release.aab` on the dedicated Wear OS track.

Both artifacts use package `com.xldev.happytalky` and must ultimately be signed by the same Play app-signing identity. They are intentionally separate artifacts. AGP 9.x removed embedded Wear-app support, so the Wear application must not be bundled inside the phone AAB.

## Versioning contract

Mutable release metadata lives in root `gradle.properties`:

~~~properties
appVersionName=...
appVersionSequence=...
~~~

Increment `appVersionSequence` for every Play release. Both form factors share the version name but derive non-overlapping version-code ranges:

- Mobile: `10,000,000 + appVersionSequence`
- Wear OS: `20,000,000 + appVersionSequence`

This keeps version codes unique across form factors while preserving one release sequence.

## Packaging contract

The Phone manifest must not declare `android.hardware.type.watch`.

The Wear manifest must declare:

~~~xml
<uses-feature
    android:name="android.hardware.type.watch"
    android:required="true" />
~~~

HappyTalky currently depends on phone interaction for its core communication model, so the Wear application is explicitly non-standalone:

~~~xml
<meta-data
    android:name="com.google.android.wearable.standalone"
    android:value="false" />
~~~

Phone and Wear must keep the same package name and matching signatures for Wear OS Data Layer communication.

## Build production bundles

Configure the private signing material as described in [DEPLOY.md](DEPLOY.md), then run:

~~~text
gradle :mobile:bundleRelease :wear:bundleRelease
~~~

Expected outputs:

~~~text
mobile/build/outputs/bundle/release/mobile-release.aab
wear/build/outputs/bundle/release/wear-release.aab
~~~

Before upload, verify that both builds use the intended release certificate and that its SHA-256 matches the registered `com.xldev.happytalky` identity.

## Play Console setup

1. Create or open the single HappyTalky Play listing for `com.xldev.happytalky`.
2. Configure Play App Signing before the first open-testing or production rollout. If XL DEV intends the existing HappyTalky release key to remain the app-signing identity, choose the Play option to provide a copy of that key.
3. Upload the **mobile AAB** to the mobile internal/closed-testing track.
4. In **Test and release > Advanced settings > Form factors**, add **Wear OS**.
5. Provide the required Wear OS store-listing assets, including a Wear OS screenshot.
6. Enable the dedicated Wear OS release track.
7. Upload the **Wear AAB** to a Wear OS testing track and opt in to Wear OS distribution.
8. Test both installations from Google Play before promoting either form factor to production.

Do not upload the Wear bundle on the mobile release track.

## Store and review notes

The store listing should explicitly mention Wear OS because the listing serves both form factors.

The phone target SDK is 36. The Wear target SDK is also 36, which is above the current Wear OS Play minimum.

HappyTalky is a non-standalone Wear experience today. If core watch functionality later becomes fully usable without the companion phone, revisit the standalone declaration and the associated Play review requirements instead of merely flipping the manifest flag.

## CI boundary

Android CI builds both release AABs using the repository's cached debug keystore only as an **ephemeral release-configuration smoke test**. Those AABs are not production artifacts and are not uploaded to Google Play.

Actual Play bundles must be built with the private XL DEV release/upload signing configuration.
