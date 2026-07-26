# NetSwitcher

🇬🇧 **English** | [🇷🇺 Русский](README.ru.md)

Quick switching between home Wi-Fi networks, mobile data (with SIM selection), and
Ethernet — from the app itself, a home-screen shortcut, a widget, or the Quick Settings
shade.

Built for Pixel (Android 12+, tested on Android 17).

## Installation

[![Latest release](https://img.shields.io/github/v/release/nd4y/netswitcher?label=release&sort=semver)](https://github.com/nd4y/netswitcher/releases/latest)

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Install via Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/nd4y/netswitcher)

The button opens [Obtainium](https://github.com/ImranR98/Obtainium) and adds the app
with GitHub-release tracking — the link isn't pinned to a version number, it always
grabs the latest release and then picks up updates on its own. (GitHub strips
`obtainium://` links out of rendered Markdown, so the button actually goes through
Obtainium's own https redirect page, which immediately hands off to the app.) If the
button doesn't do anything, add the app manually in Obtainium by pasting the repository
URL: `https://github.com/nd4y/netswitcher`.

Or just download the APK from the [releases page](https://github.com/nd4y/netswitcher/releases).

**What's new in v1.10:** the "Networks" and "Profiles" tabs have been merged — editing,
sharing, and adding networks now happens right on the "Networks" tab's cards; fixed a
crash in the panel tile's over-the-shade mode; the widget now shrinks correctly (compact
cells) and shows up to 10 rows; the Wi-Fi password no longer ends up in the command log.
Earlier: the panel tile in the shade (v1.1), "share network" via QR/NFC (v1.2), NFC
tag-emulation mode (v1.3), the panel over the (non-collapsing) shade (v1.6). Details are
in the [Share a network](#share-a-network) and [Panel in the shade](#panel-in-the-shade)
sections.

## Why Shizuku

Starting with Android 10, a regular app **cannot** turn Wi-Fi on/off or connect to a
specific saved network:

* `WifiManager.setWifiEnabled()` and `enableNetwork()` are no-ops for third-party apps;
* `WifiNetworkSpecifier` binds the network to the requesting app only, not to the device;
* `WifiNetworkSuggestion` merely suggests a network to the system — the system decides
  if and when to connect.

The working approach without flashing anything or rooting is
**[Shizuku](https://shizuku.rikka.app/)**: it grants the app access to adb's shell
privileges, and NetSwitcher runs exactly the commands you'd type over `adb shell`:

| Action | Command |
|---|---|
| Turn Wi-Fi on/off | `cmd wifi set-wifi-enabled enabled\|disabled` |
| Connect to a network | `cmd wifi connect-network <ssid> wpa2 <pass> [-h] [-b <bssid>]` |
| Turn the radio on only | `cmd wifi set-wifi-enabled enabled` — the network is picked by auto-connect |
| Airplane mode | `cmd connectivity airplane-mode enable\|disable` → `settings put global airplane_mode_on` + broadcast |
| Mobile data | `svc data enable\|disable` |
| Default data SIM | `ISub.setDefaultDataSubId()` → `cmd phone set-default-data-sub` → `settings put global multi_sim_data_call` |
| Bring up Ethernet | `ip link set eth0 up` (root only) |

Three privilege sources are supported, switchable in Settings:

* **Shizuku** — the primary one, no root needed;
* **Root** (`su -c`) — if the phone is rooted;
* **None** — degraded mode: the app registers a Wi-Fi suggestion and opens the system
  network panel, then it's manual from there.

### Setting up Shizuku

1. Install Shizuku from Google Play or from [shizuku.rikka.app](https://shizuku.rikka.app/).
2. Enable "Wireless debugging" in Developer options and start Shizuku (works without a
   computer on Android 11+; after a phone reboot, Shizuku needs to be started again).
3. Open NetSwitcher → Settings → "Grant permission".

## Share a network

On the "Networks" tab, every Wi-Fi card has a "Share" button, offering three ways to do it:

* **QR code** — the standard `WIFI:T:WPA;S:<ssid>;P:<pass>;;` format, read by the stock
  camera app on Android and iOS, which immediately offers to connect. Special characters
  in the SSID and password are escaped per the format's rules.
* **NFC tag** — writes the credentials to a rewritable tag using the
  `application/vnd.wfa.wsc` format (Wi-Fi Simple Configuration) — the same payload
  Android itself writes for "share Wi-Fi via NFC." Any phone tapped against the tag gets
  a prompt to connect.
* **Tag emulation (NFC HCE)** — the phone itself acts as an NFC tag via Host Card
  Emulation: it implements an NFC Forum Type 4 Tag (AID `D2760000850101`) and hands the
  same Wi-Fi NDEF record to the reading device. This replaces Android Beam (removed in
  Android 10) for the phone-to-phone case. It's fussier than the other methods — the
  sending phone's screen has to be on and unlocked, both devices need NFC, and results
  vary by firmware (most reliable Pixel↔Pixel) — which is why QR remains the primary
  method.
* **System "Share" menu** — send as text (SSID + password + a `WIFI:` string) or as a
  QR-code image through the regular share sheet.

The QR code is generated in-app (ZXing core), the image is served via `FileProvider`.
Requires the `NFC` permission (declared in the manifest) and, for writing, NFC turned on
plus a rewritable tag.

## Wi-Fi passwords

`cmd wifi connect-network` can't select an already-saved network — it adds the network
anew every time, so for WPA2/WPA3 the profile needs the password on file. Passwords
live in the app's private storage (DataStore) and are never sent anywhere.

## Buttons

Five surfaces, all configurable on the "Buttons" tab:

1. **In the app** — the "Networks" tab, a button for every profile.
2. **App shortcut long-press** — dynamic shortcuts; the Pixel launcher shows the top
   4–5, ordered with the arrows.
3. **Widget** — a Glance widget, a grid of buttons, column count is configurable.
4. **Shade / Quick Settings** — eight `NetSwitcher 1…8` tiles; each is assigned a
   profile, and the "Add to shade" button triggers the system's add-tile dialog
   (Android 13+).
5. **Panel tile** — a separate `NetSwitcher: panel` tile opens a popup menu with all the
   toggles and Wi-Fi networks from the home screen (see below).

## Panel in the shade

The system "Internet" tile opens a menu with Wi-Fi/mobile-network toggles, but it's slow
and only shows "discovered networks" — it kicks off a scan every time. NetSwitcher has
its own `NetSwitcher: panel` tile: tapping it collapses the shade and instantly raises a
popup panel with the same set as the home screen — toggles on top, Wi-Fi networks below.
Nothing gets scanned; it just draws the ready-made list of your profiles, so the panel
appears with no delay.

The tile itself is live too, and mirrors the system "Internet" tile: the title stays
constant ("NetSwitcher"), while the current network shows up in the subtitle — the
profile's name if its SSID matches a configured one, otherwise the raw SSID. While Wi-Fi
is on, the tile stays lit; when it's off, it dims (`STATE_INACTIVE`) like a regular
inactive tile.

**Without collapsing the shade.** By default, Android requires a third-party tile to
collapse the shade before showing any UI. The system "Internet" tile sidesteps this
because it draws its panel not as a separate window but right inside the System UI
process — third-party apps have no access there. The closest legal workaround is showing
the panel as an overlay (`TYPE_APPLICATION_OVERLAY`) on top of every window, including
the shade itself. Settings → "Panel without collapsing the shade" → "Grant permission"
(`SYSTEM_ALERT_WINDOW`, "Display over other apps") — after that the panel slides right
over the open shade, just like screenshots of the system "Internet" tile, and the shade
doesn't close. Without the permission nothing breaks — it just falls back to the old
behavior: the shade collapses, then the panel opens.

Tapping an item triggers the action through the same mechanism as the widget and
shortcuts (the work runs in the background and survives the panel closing; the start is
confirmed with a notification). The panel closes on a tap outside the card, the close
button, or the Back button — in all three cases the card smoothly slides back down
instead of vanishing abruptly.

The slide-in/slide-out animation is custom (Compose); the system's activity-window
animation is disabled for this screen — on some firmware (MIUI, for instance) it's
noticeably heavier, and layered on top of ours it looked janky.

Technically this isn't a menu "built into" the shade — detailed tile panels remain a
private System UI API that's off-limits to third-party tiles. The panel is raised as a
separate translucent window on top of the screen: visually almost the same, but not
actually inside the shade itself.

The panel's contents are the home-screen set (`home` in the config): what to show and in
what order is configured on the "Buttons" tab → "App home screen," and in YAML. To add
the tile itself to the shade: "Buttons" → "Panel tile" → "Add to shade" (Android 13+),
or manually through the tile editor.

## Feedback on tap

A tap is acknowledged immediately, before the privileged commands even run. This matters
for the widget, the shortcut, and the shade: the app's process may have been unloaded
there, and without an explicit response there's no way to tell whether the tap did
anything at all.

1. A short vibration — before anything else.
2. A "Switching: …" notification with a progress indicator. Settings lets you choose
   whether it sits quietly **in the shade** or slides out as a **heads-up banner**.
   Either way it's always silent and vibration-free: the channels are created with
   `setSound(null, null)` and `enableVibration(false)`.
3. On Android 16+, the same notification is additionally promoted into a **status bar
   chip** (`NotificationCompat.ProgressStyle` + `setRequestPromotedOngoing`), with
   `setShortCriticalText()` putting the network's name in the chip.
4. On completion the notification closes itself, and the result arrives as a pop-up
   message.

The widget button lights up and shows "switching…" for the duration of the work, and the
tile's subtitle changes — the feedback shows up right where the tap happened. Inside the
app, controls give a light haptic response.

Requires the `POST_NOTIFICATIONS` permission (requested on first launch),
`POST_PROMOTED_NOTIFICATIONS`, and `VIBRATE` (declared in the manifest), plus
`androidx.core` 1.17+.

Separately, "Location" (`ACCESS_FINE_LOCATION`) is requested on first launch: without it,
Android hides the current network's SSID, and the "connected" highlight on cards, the
widget, and the panel tile won't work. The SSID never goes anywhere except the screen.

## Toggles

The home screen's first row is four toggles: **Wi-Fi**, **LTE** (mobile data),
**Ethernet**, and **Airplane mode**. Each shows its current state ("on"/"off",
highlighted) and flips it on tap. These can also be placed on the widget, a shortcut, or
a tile.

Above the toggles sits a status card: which network you're connected to, over which
transport, and which privileges are available.

Below the toggles come **Wi-Fi networks only**. Cards are reordered by press-and-drag;
each one has "share" and "edit" icons (the editor includes delete), and the "+" button
in the header adds a new network. One-shot actions (`Wi-Fi on`, `LTE only`,
`Ethernet only`) aren't shown as home-screen buttons — they belong on the widget, a
shortcut, or a tile; they're edited in the "Other profiles" section at the bottom of the
same tab. There's no separate "Profiles" tab anymore — all profile management now lives
on "Networks."

The home screen's contents are configurable: "Buttons" → "App home screen" — any toggle
or profile can be removed and brought back. The "Reset button layout" button in Settings
restores the home screen, shortcuts, widget, and tiles to their original layout without
touching profiles or passwords; a full reset means clearing the app's data in Android's
settings.

Wi-Fi profiles for specific networks also work as toggles: tapping a network you're
already connected to disconnects from it. There's no dedicated "disconnect" shell
command, so this is done by turning the radio off — this behavior can be turned off
with the "Tapping again disconnects" flag on the profile.

## Profiles out of the box

Toggles for `Wi-Fi` / `LTE` / `Ethernet` / `Airplane mode`, networks `Home`, `Home 5G`,
`Guest`, `IoT` (placeholder SSIDs — replace with your own), plus the one-shot
`LTE only`, `Wi-Fi on`, `Wi-Fi off`, `Ethernet only`. Passwords are filled in through the
profile editor (the pencil icon on the card).

## YAML configuration

Settings → "Export to file" / "Import from file". The format is human-readable and
commented, with an example at [examples/example-config.yaml](examples/example-config.yaml).
Import fully replaces the profiles and button assignments; a broken file is rejected
outright rather than half-overwriting the configuration.

The `home` / `shortcuts` / `widget` / `tiles` lists set what shows up on the home
screen, in shortcuts, on the widget, and in the tiles. A missing `home` means "show
everything," while an explicit `home: []` means an empty home screen. References to
nonexistent ids are silently dropped, so you don't end up with buttons that do nothing.

One gotcha: in YAML 1.1, bare `off` / `on` / `yes` / `no` are parsed as booleans, so
always quote `id` values. The exporter does this automatically.

## Automated deployment via adb

For automating "install the new version → push the config," there's a headless YAML
import with zero taps on the phone (needs USB or Wi-Fi debugging enabled):

```bash
adb install -r netswitcher.apk
adb shell am start -n icu.nd4y.netswitcher/.ui.MainActivity
adb shell am broadcast -n icu.nd4y.netswitcher/.action.ConfigImportReceiver \
    -a icu.nd4y.netswitcher.action.IMPORT_CONFIG \
    --es yaml_base64 "$(base64 -w0 my-config.yaml)"
```

The broadcast returns `result=0` on success (`resultData` holds the profile count), and
a toast shows up on the phone. The YAML is passed as base64 to survive shell quoting.
The semantics match in-app import: a full replace, and a broken file is rejected
outright.

The receiver is exported but locked behind the `android.permission.DUMP` permission —
adb shell has it, but a regular third-party app can't obtain it, so the endpoint is
only reachable by whoever already has adb access to the device.

## Ethernet

The Ethernet profile turns off Wi-Fi and mobile data so the wired adapter (USB-C ↔
Ethernet) becomes the default route. Bringing the interface itself up
(`ip link set eth0 up`) only works with root — without it, this just "clears the way"
for a link that's already up.
