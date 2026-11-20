# App links

Canonical origin: `https://jwtc.nl`. Ordinary site pages stay in the browser. Only
`/app` and `/app/…` are registered in the Android manifest. A static directory index
serves each supported route; no application server or rewrite rules are required.

## URL format (version 1)

Shared content is UTF-8 percent-encoded in the fragment, never sent to the web
server. All generated routes end in `/` to avoid directory redirects.

| Route | Fragment | Behavior |
| --- | --- | --- |
| `/app/` | none | Home |
| `/app/position/` | `v=1&fen=…` | Replace analysis with the position |
| `/app/game/` | `v=1&pgn=…&path=0.1.0` | Replace analysis with the complete game at the selected node |
| `/app/join/` | `v=1&host=192.168.1.42` | Prefill a client connection dialog; Connect starts/replaces the connection |
| `/app/online/` | `v=1&user=alice` | Prefill a Lichess challenge, retaining the target through login |

`path` lists zero-based child indices from the game root, preserving selection
inside variations. An empty path selects the initial position. Variant information
is carried by the existing PGN/FEN formats. Shared variants without engine analysis
remain browsable. The local playable game's saved state is not overwritten.

The maximum encoded URL length is 8,000 characters. Oversized games offer PGN
file sharing instead, using a cache-backed FileProvider with temporary read permission
so large games do not hit Android intent payload limits. The parser rejects unknown routes, versions, parameters, duplicate
parameters, malformed paths and invalid IPv4/user IDs. FEN/PGN is validated by the
existing game loader. Host links contain an explicit IPv4 address and use local-WiFi
client mode, also when the network is a phone's hotspot. Both devices still need
network connectivity to each other; the link does not join Wi-Fi automatically.

Collection links and server-side game storage are outside this scope.

## Website and signing

Website files live in the sibling `jwtc.nl` repository under:

- `www/app/`: static fallback pages, CSS and JavaScript.
- `www/.well-known/assetlinks.json`: Play and F-Droid signing certificates.

The fallback page uses an explicit Android package intent for its Open in app
button, preserving the entire original fragment. It shows both installation
options; users return to the original link after installing. No deferred-link
backend or automatic continuation after installation is used.

Play fingerprint supplied from Play Console:
`DD:76:B7:24:61:C4:18:B3:BB:60:A4:C8:6B:43:7D:3E:DF:52:B0:A7:7A:BF:0F:35:CD:08:89:7E:4F:E1:42:EB`

F-Droid fingerprint extracted with Android SDK `apksigner verify --print-certs`
from `https://f-droid.org/repo/jwtc.android.chess_298.apk`:
`E0:C0:CB:DE:EB:E7:4A:7B:B6:5A:40:9C:50:9D:24:8B:87:5B:84:CA:D3:BD:59:C1:DE:BF:01:3F:30:03:7F:BA`

Debug certificates are intentionally absent from the public association file.

## Release checks

1. Deploy the website files using its existing deployment process.
2. Check `https://jwtc.nl/.well-known/assetlinks.json` returns HTTP 200 directly,
   over HTTPS, with `Content-Type: application/json`. It must not redirect to
   `www.jwtc.nl`; configure the web server to serve the canonical bare domain.
3. Release the Android build through each store. Old installations do not gain
   manifest link handling until updated.
4. On a device with the store-signed build:

```sh
adb shell pm verify-app-links --re-verify jwtc.android.chess
adb shell pm get-app-links jwtc.android.chess
adb shell am start -a android.intent.action.VIEW -d 'https://jwtc.nl/app/'
```

For URLs with `&`, ensure the URL is quoted for the device shell as well as the
local shell. For debug testing, explicitly target
`jwtc.android.chess/.links.LinkActivity`; this tests routing, not domain verification.

Manually check cold and warm opens, replacing analysis, variation/root selection,
Duck/Chess960 positions, local-game preservation, active-host invitation cancellation,
Connect switching sessions, Lichess login continuation, malformed/oversized URLs,
sharing controls, and the fallback page on Android with and without an installation.
