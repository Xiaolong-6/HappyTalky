# Experimental: Wear OS captive portal proof of concept

Originally developed on: `experiment/wear-captive-portal-poc`; now integrated into the mainline codebase as a debug-only PoC. Current validation status is tracked in [VALIDATION.md](VALIDATION.md).

## Goal

Test whether HappyTalky can bridge a gap in Wear OS for simple public Wi-Fi captive portals without embedding a WebView.

The PoC deliberately does **not** appear in the child-facing CALL/TALK UI. It is compiled only in the Watch debug variant.

## What it does

1. User opens the Watch Wi-Fi settings and joins an open public network.
2. The debug activity searches Android's active network set for a Wi-Fi network that is captive or otherwise not validated.
3. HTTP requests are opened through that exact `Network`, so Bluetooth/cellular/default routing cannot silently handle the probe instead.
4. The client follows ordinary HTTP redirects and meta refreshes and keeps cookies with `java.net.CookieManager`.
5. HTML is parsed locally. Hidden fields and simple terms/accept checkboxes are retained.
6. If the page only needs a simple confirmation, the Watch shows one explicit confirmation button.
7. Only after the user taps that button is the GET/POST form submitted.
8. The Watch then repeats the Android connectivity 204 probe and reports success only when that probe succeeds.

## Safety boundary

The PoC does not auto-accept venue terms.

Visible text, email, password, radio, select, textarea, or unrecognised checkbox fields make the portal unsupported instead of guessing values. JavaScript-only portals, OAuth/SSO, CAPTCHA, and payment flows are out of scope.

Cleartext HTTP is allowed only by the debug manifest because captive portals commonly begin on HTTP.

## Build

```text
gradle :wear:testDebugUnitTest :wear:assembleDebug
```

## Launch on a Watch

Install the debug Watch APK and run:

```text
adb shell am start -n com.xldev.happytalky/com.xldev.happytalky.wear.PublicWifiDebugActivity
```

Then:

1. Tap **Wi-Fi settings**.
2. Join the public/open SSID.
3. Return to the PoC.
4. Tap **Check public Wi-Fi**.
5. If a supported confirmation form is detected, review the venue terms through the venue-provided page/signage and tap the shown confirmation action.
6. Success means the Watch itself can reach the 204 probe through that Wi-Fi network.

## Hardware gate

A CI build can validate compilation and HTML parser behavior. It cannot establish whether the target Wear OS device keeps an unvalidated captive Wi-Fi network attached long enough for an ordinary application to complete the transaction.

That physical-device behavior remains the main go/no-go question. The current status belongs in [VALIDATION.md](VALIDATION.md) so this experimental design note does not become a stale hardware claim.
