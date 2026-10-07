# OpenPlay — wireless CarPlay receiver for Windows

OpenPlay turns a Windows PC (for example an in-car PC or tablet) into a wireless CarPlay
receiver. It is a desktop port of [DiPlay](https://github.com/j1z4/DiPlay): the iAP2, MFi,
AirPlay pairing/encryption and media code is DiPlay's own, compiled unchanged from `../shared`,
with Windows replacements for Bluetooth, networking, video, audio and touch.

## How it connects

1. Bluetooth (RFCOMM, iAP2): OpenPlay authenticates with the paired iPhone and hands it the
   Wi-Fi network both devices are on, plus this PC's AirPlay address.
2. The iPhone joins over Wi-Fi and opens an AirPlay session to OpenPlay (port 7000 by default).
3. Video (H.264), audio (AAC, Opus, PCM) and the iAP2 tunnel flow over Wi-Fi; Bluetooth is released.

## Requirements

- Windows 10/11 x64 with a Bluetooth adapter and Wi-Fi.
- The iPhone paired with the PC in **Settings > Bluetooth & devices**.
- PC and iPhone on the same Wi-Fi network (WPA2 or WPA2/WPA3 mixed; open networks also work).
- An accessory identity folder containing `identity.pk8` and `certificate.p7b` (not included).
- Allow OpenPlay through Windows Firewall on **private** networks when Windows asks.

## Settings

On first start OpenPlay creates `%APPDATA%\OpenPlay\settings.properties`:

```properties
iphoneAddress=AA:BB:CC:DD:EE:FF   # the iPhone's Bluetooth address
wifiPassphrase=                   # password of the shared Wi-Fi (blank for open networks)
mfiDirectory=C:\path\to\offline-mfi
width=1280
height=720
fps=60
fullscreen=false                  # true for a borderless kiosk on an in-car PC
clusterDisplay=false              # experimental: CarPlay's instrument cluster in a second "OpenPlay Cluster" window
vehicleData=false                 # experimental: simulated EV status, speed and location over the Wi-Fi tunnel
autoStart=false                   # start CarPlay when OpenPlay opens
```

The AirPlay identity and paired-iPhone keys are kept next to it (`identity.properties`,
`pairings.properties`); deleting them makes the iPhone pair again.

## Build and run

Requires JDK 21+ (25 recommended). From the repository root:

```powershell
.\gradlew.bat -p desktop test            # unit tests
.\gradlew.bat -p desktop installDist     # build\install\openplay\bin\openplay.bat
.\gradlew.bat -p desktop packageOpenPlay # build\package\OpenPlay\OpenPlay.exe (bundled runtime)
```

Copy `build\package\OpenPlay` anywhere and run `OpenPlay.exe`. Esc or closing the window quits.

## Status

| Feature | State |
|---|---|
| Bluetooth bootstrap, Wi-Fi handoff, AirPlay session | Working |
| Video (H.264 via FFmpeg), touch (mouse/single touch) | Working |
| Audio to the PC (AAC, Opus, PCM) | Working |
| Microphone for calls and Siri | Built, not yet verified |
| Instrument cluster display (second window, stream 111) | Experimental, not yet verified |
| Simulated vehicle data (EV status, speed, location) | Experimental, not yet verified |
| Settings dashboard (all settings, Start/Stop, live status) | Working |
| Wired USB CarPlay, multi-touch, HEVC | Not implemented |

## Notes

- Without microphone input formats advertised, iOS keeps all audio on the phone; OpenPlay
  always advertises the microphone.
- The accessory identity comes from DiPlay's experimental approach and can stop being accepted
  after an iOS update.
- Licensed under GPL-3.0, as DiPlay.
