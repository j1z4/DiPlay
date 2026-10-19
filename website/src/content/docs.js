// Documentation page content for the OpenPlay website.
// Facts come from README.md, AGENTS.md and docs/*.md in the repository.

export const DOCS = [
  // ---------------------------------------------------------------- Start here
  {
    slug: "getting-started",
    group: "Start here",
    title: "Getting started",
    summary: "What OpenPlay is, what you need to run it, and what it is not.",
    sections: [
      {
        id: "what-it-is",
        heading: "What OpenPlay is",
        blocks: [
          {
            type: "p",
            text: "OpenPlay is a fork of DiPlay: **CarPlay for compatible BYD Android head units**, wired and wireless, with the familiar DiAuto interface. You install it on the **car**, not on the iPhone.",
          },
          {
            type: "list",
            items: [
              "Wired USB and wireless CarPlay with local authentication.",
              "Wireless through Wi-Fi Direct, the car's built-in hotspot, or Existing Wi-Fi / Same LAN.",
              "BYD HUD navigation with arrows, distance and street names on verified firmware.",
              "Icon/text size, resolution and frame rate settings; applying a display change reconnects CarPlay.",
              "Local diagnostic export. Reports are sent only if you choose to share them.",
            ],
          },
          {
            type: "note",
            tone: "info",
            title: "BYD support scope",
            text: "The project focuses on BYD cars. It may work on other brands, but other brands are unsupported and there are no plans to add support or fix brand-specific incompatibilities.",
          },
        ],
      },
      {
        id: "what-you-need",
        heading: "What you need",
        blocks: [
          {
            type: "list",
            items: [
              "An Android head unit running **Android 7.0 or newer** (API 24+) that permits APK installation. Android 7.0–8.1 support is not yet confirmed on a vehicle; Android 6.0 and older are unsupported.",
              "A standard, non-jailbroken iPhone with CarPlay enabled. Device and iOS compatibility varies.",
              "For USB: a USB **data** port and a data-capable cable. Charge-only ports and cables cannot work.",
            ],
          },
          {
            type: "p",
            text: "No jailbreak, dongle, Mac, account or authentication server is required. Core CarPlay does not require ADB; optional dashboard, battery, wheel-speed and parked-video features do.",
          },
        ],
      },
      {
        id: "what-it-is-not",
        heading: "What it is not",
        blocks: [
          {
            type: "note",
            tone: "warning",
            title: "Not an Apple-certified product",
            text: "This is an independent receiver, not an Apple-certified CarPlay accessory. Release APKs bundle an **experimental accessory identity** recovered from public Carlinkit firmware, not a newly provisioned MFi identity. The bundled private key is extractable. Acceptance after future iOS updates, reliability across head units and suitability of that identity for general distribution are unresolved.",
          },
          {
            type: "list",
            items: [
              "It is not a guarantee of universal compatibility; community reports across DiLink generations are mixed and there is no certified model support list.",
              "It is not affiliated with or endorsed by Apple or BYD. CarPlay and its icon belong to Apple Inc.",
              "It installs separately alongside DiAuto. Run one projection app at a time.",
            ],
          },
        ],
      },
    ],
  },
  {
    slug: "install",
    group: "Start here",
    title: "Install",
    summary: "Install the APK on the car's head unit and grant the permissions it needs.",
    sections: [
      {
        id: "install-on-the-car",
        heading: "Install on the car",
        blocks: [
          {
            type: "steps",
            items: [
              "Park the car and download the APK from the official release page.",
              "Install it on the Android head unit using its supported APK installation method. Do not install it on the iPhone.",
              "Open the app. Grant the permissions requested for the features you use: Bluetooth/Nearby devices, Wi-Fi/Location on older Android, and microphone for Siri and calls. Allow notifications for connection controls.",
              "Close other phone-projection apps before connecting.",
            ],
          },
          {
            type: "p",
            text: "A first launch with no saved iPhone opens the **Setup guide**. It reads the DiLink version, lets you correct it and only offers features that apply to that generation. You can skip it and reopen it from **Settings → Overview**.",
          },
        ],
      },
      {
        id: "updating",
        heading: "Updating",
        blocks: [
          {
            type: "p",
            text: "Update in place rather than uninstalling. An in-place update needs the same package/variant and a matching signing certificate; it then preserves settings and pairing records. A different signing certificate cannot update the installed app.",
          },
          {
            type: "p",
            text: "The app checks GitHub's public release list for updates once a day and shows **Update available** on Home. **About** downloads it on your tap and can turn the background check off. Android handles the APK installation.",
          },
          {
            type: "note",
            tone: "warning",
            text: "Do not uninstall until you have saved any diagnostic reports you need. Uninstalling removes app-private data.",
          },
        ],
      },
      {
        id: "install-with-adb",
        heading: "Install with ADB (optional)",
        blocks: [
          {
            type: "p",
            text: "APK installation restrictions are controlled by your car's firmware. ADB is optional if your car supports it; it is not an app runtime requirement. Only use a trusted computer.",
          },
          { type: "code", lang: "sh", code: "adb install -r DiPlay-0.2.21.apk" },
        ],
      },
    ],
  },
  {
    slug: "connect",
    group: "Start here",
    title: "Connect your iPhone",
    summary: "Make the first connection over the car hotspot, Wi-Fi Direct, Existing Wi-Fi / Same LAN or USB.",
    sections: [
      {
        id: "car-hotspot",
        heading: "Built-in car hotspot (default)",
        blocks: [
          {
            type: "p",
            text: "Built-in car hotspot is the default and recommended wireless mode. You do not need to join the hotspot on the iPhone first: the app sends its details over Bluetooth so the phone can join automatically. ADB and a car internet plan are not required.",
          },
          {
            type: "steps",
            items: [
              "In the car's settings, turn on its built-in Wi-Fi hotspot. Select 5 GHz if available, and note the exact name and password.",
              "Open **Settings → Connection → Connection setup → Built-in car hotspot**. Tap **Save hotspot details and use this mode** (or **Edit saved hotspot**), enter the name and password, and save. Leave the car hotspot on.",
              "On the iPhone, turn on Bluetooth and Wi-Fi and pair it with the car's Bluetooth. Allow the permissions requested on the car.",
              "Tap **Connect phone** and select your iPhone when asked.",
              "Accept the CarPlay prompts on the iPhone.",
            ],
          },
          {
            type: "note",
            tone: "info",
            text: "Use the car's hotspot, not the iPhone's Personal Hotspot. If you change the car hotspot name or password, update it in the app too.",
          },
        ],
      },
      {
        id: "wifi-direct",
        heading: "Wi-Fi Direct",
        blocks: [
          {
            type: "p",
            text: "Wi-Fi Direct is an alternative on Android 7.0+ when the head unit implements the needed firmware APIs. Android 7.0–9 uses generated group credentials and cannot verify the requested frequency; Android 10+ verifies the negotiated frequency. See [Android 9 Wi-Fi Direct](https://github.com/j1z4/DiPlay/blob/main/docs/ANDROID9_WIFI_DIRECT.md) for the legacy limits.",
          },
          {
            type: "p",
            text: "Under **Settings → Connection → Connection setup → Wi-Fi Direct → Preferred channel**, **Auto** is the default. **Auto · 5 GHz** and **Auto · 2.4 GHz** constrain automatic attempts to one band where supported. You can also save a 5 GHz channel (36, 40, 44, 48, 149, 153, 157, 161 or 165) or a 2.4 GHz channel (1–11); it applies at the next Wi-Fi Direct connection. If the car rejects it, choose Auto or another channel and reconnect.",
          },
        ],
      },
      {
        id: "existing-wifi",
        heading: "Existing Wi-Fi / Same LAN",
        blocks: [
          {
            type: "p",
            text: "This mode keeps the iPhone and head unit on an external router or portable Wi-Fi. No car hotspot or Wi-Fi Direct group is created, and the app does not join the network or change the default route for you.",
          },
          {
            type: "steps",
            items: [
              "In system settings, connect the car and the iPhone to the same router or portable Wi-Fi.",
              "Disable router client isolation and allow multicast DNS. Prefer 5 GHz when supported.",
              "In **Settings → Connection → Connection setup**, select **Existing Wi-Fi / Same LAN** and enter the network's exact name (SSID) and WPA2 password. For an open network, leave the password empty.",
              "Keep Bluetooth enabled and the iPhone paired with the car, then connect as usual.",
            ],
          },
          {
            type: "note",
            tone: "info",
            text: "Use WPA2-Personal or WPA2/WPA3 mixed mode. WPA3-only, enterprise authentication and captive portals are outside this mode's current scope. See [Existing Wi-Fi / Same LAN](https://github.com/j1z4/DiPlay/blob/main/docs/EXISTING_WIFI.md).",
          },
        ],
      },
      {
        id: "usb",
        heading: "USB",
        blocks: [
          {
            type: "steps",
            items: [
              "Connect the iPhone to a USB **data** port with a data-capable cable.",
              "Choose **Connect with USB**.",
              "Approve USB access, Trust/CarPlay and the local VPN permission if requested.",
            ],
          },
          {
            type: "p",
            text: "The local VPN carries the USB network link; it is not an internet VPN service. If wired CarPlay drops every few seconds and the iPhone reconnects by itself, try **Settings → Connection → USB connection → iPhone charging → Reduced**, then **Low**; the iPhone then charges more slowly.",
          },
        ],
      },
      {
        id: "choose-iphone",
        heading: "Choosing the iPhone",
        blocks: [
          {
            type: "p",
            text: "When you first tap **Connect phone**, select your iPhone when asked. Use **Choose iPhone** later to change the selected paired device.",
          },
        ],
      },
    ],
  },

  // ----------------------------------------------------------- Using OpenPlay
  {
    slug: "settings",
    group: "Using OpenPlay",
    title: "Settings tour",
    summary: "Where each setting lives: the Settings screen is grouped by what the driver wants to do.",
    sections: [
      {
        id: "how-settings-work",
        heading: "How settings apply",
        blocks: [
          {
            type: "p",
            text: "Follow each setting's description: some apply live, while others take effect at the next connection. A change that applies at the next connection shows a **Reconnect now** bar; it does not drop a running session without your consent.",
          },
          {
            type: "p",
            text: "Icon/text size, resolution and frame rate use **Apply and reconnect** during an active session. A selection alone does not apply, and Cancel keeps the old setting. When disconnected, **Save** applies to the next connection.",
          },
          {
            type: "p",
            text: "In CarPlay, swipe down with the configured number of fingers (two, three or four; three by default) to open the quick menu. The quick menu asks before Back or the full-settings link discards changes you have not applied.",
          },
        ],
      },
      {
        id: "categories",
        heading: "Categories",
        blocks: [
          {
            type: "table",
            head: ["Category", "What it holds"],
            rows: [
              ["Connection", "How the iPhone connects and how the app starts: connection setup, connect on open, start with the car, USB permissions, car hotspot automation, iPhone choice and Android permissions."],
              ["Display", "How CarPlay looks on the head-unit screen: day/night mode, picture adjustments, icon/text size, resolution, frame rate, dock position, system bars and **Interface size**."],
              ["Audio", "What the driver hears: media and navigation audio routing and the music-buffer choice."],
              ["Navigation", "Location reporting to the iPhone and the BYD navigation card (HUD and cluster guidance) on supported head units."],
              ["Vehicle", "How CarPlay fits the car: driving side, wheel keys (Siri, BYD joystick and map zoom), the car button and CarPlay gestures."],
              ["Diagnostics", "Troubleshooting evidence: **Save diagnostic report** and the **FPS counter**."],
              ["Advanced", "Experimental, firmware-specific or risky behavior: dashboard map, split screen, screen rotation, side panel, HEVC video, audio focus, audio channel mapping, buffered music and vehicle data."],
            ],
          },
          {
            type: "p",
            text: "Experimental controls carry the suffix **(experimental)** and are off by default.",
          },
        ],
      },
      {
        id: "display-tips",
        heading: "Display starting points",
        blocks: [
          {
            type: "list",
            items: [
              "Start with 30 fps and Default icon/text size.",
              "Leave **Efficient video** (HEVC) off under **Settings → Advanced → Video and audio**.",
              "Try 80% or 60% resolution for a slower head unit.",
              "**Interface size** changes the app's own controls, not CarPlay's picture.",
              "**App appearance** selects Light, Dark or Auto for the app itself, separately from CarPlay day/night mode.",
            ],
          },
        ],
      },
      {
        id: "advanced",
        heading: "Advanced",
        blocks: [
          {
            type: "list",
            items: [
              "**Settings → Advanced → Video and audio** holds **Smooth video (experimental)**, **Buffered music (experimental)**, **Call echo cancellation (experimental)**, **Clearer call voices (experimental)**, **Low-latency decoding (experimental)** and **Direct video output (experimental)**. All are off by default.",
              "Changing Smooth video or Buffered music reconnects an active session; call echo cancellation, voice filtering and low-latency decoding apply at the next connection.",
              "**Settings → Advanced → Display (experimental)** holds square-canvas rotation, split-screen areas and the side panel, all off by default. Larger canvases increase decoder/GPU work.",
              "**Settings → Advanced → Advanced vehicle data** holds the vehicle-data mode and battery, wheel-speed and parked-video controls, which are off by default.",
            ],
          },
        ],
      },
      {
        id: "overview-language-about",
        heading: "Overview, Language and About",
        blocks: [
          {
            type: "p",
            text: "**Overview** shows the connection status, links to every category, a few quick settings, **Language** and **About**. The Setup guide can be reopened from here.",
          },
          {
            type: "list",
            items: [
              "**Language**: choose the app language. On Android 13+ it stays synchronized with Android's per-app language setting.",
              "**About**: downloads an available update on your tap and can turn the daily background update check off.",
            ],
          },
        ],
      },
    ],
  },
  {
    slug: "smooth-wireless",
    group: "Using OpenPlay",
    title: "Smooth wireless CarPlay",
    summary: "Connection mode, Wi-Fi channel and picture size choices that affect wireless smoothness.",
    sections: [
      {
        id: "use-car-hotspot",
        heading: "Use the built-in car hotspot",
        blocks: [
          {
            type: "p",
            text: "Wi-Fi Direct needs the car's Wi-Fi switch on, and while the car is not joined to a network it searches for one (every 10 s in reports from DiLink 4.0 and 5.0 cars). Each search takes the car's single radio off the CarPlay channel, so music and navigation voice stutter on that rhythm whatever channel you choose. A larger music buffer cannot bring the lost audio back.",
          },
          {
            type: "p",
            text: "The car hotspot runs with the car's Wi-Fi client off, and on the same cars it lost no audio, or almost none. With network ADB already approved, the app pauses this search during Wi-Fi Direct sessions on Android 7.1 and later.",
          },
        ],
      },
      {
        id: "wifi-channel",
        heading: "Wi-Fi Direct channel",
        blocks: [
          {
            type: "p",
            text: "On a 2024 BYD Tang, running Wi-Fi Direct on a different 5 GHz channel from the car's joined Wi-Fi network caused the picture to stop for 0.6–2 s at a time and music to stutter. A 2.4 GHz Wi-Fi Direct channel alongside the 5 GHz network was smooth in that test.",
          },
          {
            type: "list",
            items: [
              "**Auto** (the default) can reuse a compatible saved configuration and tries 2.4 GHz before other 5 GHz attempts beside a 5 GHz station connection. It cannot guarantee a band.",
              "If you choose a channel by hand and the car joins a 5 GHz network, try a supported 2.4 GHz channel (1–11), or go back to Auto.",
            ],
          },
        ],
      },
      {
        id: "picture-size",
        heading: "Picture size and rotation",
        blocks: [
          {
            type: "p",
            text: "Larger pictures can increase encoding, transmission and decoding work. Measured on the Tang (2560×1440 screen) while scrolling:",
          },
          {
            type: "table",
            head: ["Setting", "Picture", "Frames per second"],
            rows: [
              ["Rotation off", "2560×1440", "45–57"],
              ["Rotation on, Smoother (1920)", "1920×1920 square", "49–57"],
              ["Rotation on, Sharper (the screen's size)", "2560×2560 square", "32–44"],
            ],
          },
          {
            type: "list",
            items: [
              "If your screen never or rarely turns, try leaving rotation **off**.",
              "If you turn the screen often, turn rotation on and keep **Smoother (1920)**, the default.",
            ],
          },
        ],
      },
      {
        id: "if-it-still-stutters",
        heading: "If it still stutters",
        blocks: [
          {
            type: "p",
            text: "Try Default icons, 30 fps and a lower resolution, then attach a diagnostic report. **Smooth video (experimental)** under **Settings → Advanced → Video and audio** paces frames to the iPhone's timing; it can delay touch response and makes picture adjustments unavailable. **Settings → Diagnostics → FPS counter** shows frames shown and received per second and the decode time.",
          },
          {
            type: "p",
            text: "Results come from one car and may differ on other cars, firmware and phones. See [Smooth wireless CarPlay](https://github.com/j1z4/DiPlay/blob/main/docs/SMOOTH_WIRELESS.md) for the full measurements.",
          },
        ],
      },
    ],
  },
  {
    slug: "byd-navigation",
    group: "Using OpenPlay",
    title: "BYD navigation displays",
    summary: "Turn-by-turn guidance on BYD HUD and instrument-cluster displays.",
    sections: [
      {
        id: "hud-guidance",
        heading: "HUD and cluster guidance",
        blocks: [
          {
            type: "p",
            text: "Phone navigation arrows, next-turn distance and street names can appear on supported BYD displays. Ordinary operation requires no ADB, root, laptop or helper process. The map app must provide structured navigation metadata, so compatibility is not guaranteed for every map app or version.",
          },
          {
            type: "p",
            text: "Live windshield guidance and street names were physically confirmed on DiLink5.1 / Android 13 on one specific firmware; other firmware is not implicitly enabled. The BYD navigation card is under **Settings → Navigation** and is enabled by default when available.",
          },
        ],
      },
      {
        id: "dashboard-map",
        heading: "CarPlay map on the dashboard (experimental)",
        blocks: [
          {
            type: "p",
            text: "The app can ask the iPhone for CarPlay's instrument-cluster screen and show it in the BYD cluster's map area. Turn on **CarPlay map on dashboard** under BYD navigation; the switch appears only when a cluster projection display is visible to the app. Then choose **Full screen navi** or **Small screen navi** in the cluster's own steering-wheel menu.",
          },
          {
            type: "p",
            text: "This was validated on DiLink5.0 / Android 12 with Apple Maps. Other map apps and other firmware are untested.",
          },
        ],
      },
      {
        id: "limits",
        heading: "Limits",
        blocks: [
          {
            type: "list",
            items: [
              "DiLink 3.0 cluster guidance and map are experimental and need ADB. No HUD guidance is claimed on DiLink 3.",
              "Guidance in the FCE cluster (experimental) is off by default and has not yet run in a car with CarPlay.",
              "Force-stopping the app may leave the last instruction visible until it opens again.",
              "Run only one projection app at a time.",
            ],
          },
          {
            type: "p",
            text: "See [BYD navigation](https://github.com/j1z4/DiPlay/blob/main/docs/BYD_NAVIGATION.md) for the exact firmware scope, settings and cleanup behavior.",
          },
        ],
      },
    ],
  },

  // ---------------------------------------------------------------------- Help
  {
    slug: "troubleshooting",
    group: "Help",
    title: "Troubleshooting",
    summary: "Compatibility scope and fixes for common connection, picture and audio problems.",
    sections: [
      {
        id: "compatibility",
        heading: "Compatibility",
        blocks: [
          {
            type: "table",
            head: ["Area", "Current scope"],
            rows: [
              ["Head unit", "Android 7.0+ (API 24+). Android 7.0–8.1 not yet confirmed on a vehicle; Android 6.0 and older unsupported."],
              ["Phone", "Standard, non-jailbroken iPhone with CarPlay enabled; device/iOS compatibility varies."],
              ["Other cars", "Mixed community reports across DiLink generations; not a certified model support list."],
              ["Video", "Default H.264 / 30 fps; 60 fps and HEVC increase device-specific demands."],
            ],
          },
        ],
      },
      {
        id: "wireless-problems",
        heading: "Wireless problems",
        blocks: [
          {
            type: "list",
            items: [
              "**iPhone leaves its Wi-Fi but does not join the car hotspot:** check that Auto-Join is enabled for that hotspot. If selecting it manually starts CarPlay, record that in the diagnostic report.",
              "**Old Wi-Fi Direct group after reinstalling:** close other projection apps, then use **Settings → Connection → Wireless connection help → Reset CarPlay Wi-Fi**.",
              "**Wi-Fi Direct stutters every few seconds:** the car's Wi-Fi client is searching for networks. Use the built-in car hotspot; see [Smooth wireless](https://github.com/j1z4/DiPlay/blob/main/docs/SMOOTH_WIRELESS.md).",
              "**Wireless never starts on a unit that keeps calls and music on the maker's own Bluetooth module:** wireless CarPlay cannot work there; the app stops with the cause. Use USB.",
            ],
          },
        ],
      },
      {
        id: "usb-problems",
        heading: "USB problems",
        blocks: [
          {
            type: "list",
            items: [
              "USB requires a data port and correct host/device-role behavior. A charge-only port or cable cannot work.",
              "If wired CarPlay drops every few seconds, set **Settings → Connection → USB connection → iPhone charging** to **Reduced**, then **Low**.",
            ],
          },
        ],
      },
      {
        id: "picture-problems",
        heading: "Picture and display problems",
        blocks: [
          {
            type: "list",
            items: [
              "**Stutter under load:** try Default icons, 30 fps and a lower resolution, then attach a report.",
              "**Icon/text size has no effect:** some iOS/head-unit combinations do not visibly apply it.",
              "**Wallpaper shows through rounded corners:** open the in-CarPlay settings menu and choose **Safe area → Black rounded corners**, then pick the smallest radius that covers it.",
              "**Artifacts or freezes** with **Low-latency decoding** or **Direct video output** on: turn them off.",
            ],
          },
          {
            type: "p",
            text: "Automatic startup depends on the car's firmware and startup permissions. Calls, Siri, background reconnection, long journeys and future iOS releases need broader testing. See [Compatibility](https://github.com/j1z4/DiPlay/blob/main/docs/COMPATIBILITY.md).",
          },
        ],
      },
    ],
  },
  {
    slug: "diagnostics-privacy",
    group: "Help",
    title: "Diagnostics and privacy",
    summary: "Save a diagnostic report, know what it contains, and how the app handles your data.",
    sections: [
      {
        id: "save-a-report",
        heading: "Save a diagnostic report",
        blocks: [
          {
            type: "steps",
            items: [
              "Reproduce the problem on the current release, from the first connection attempt through the failure. For boot or auto-start problems, reboot, then open the app manually.",
              "Open **Settings → Diagnostics → Save diagnostic report**.",
              "Android 10+ normally saves to **Downloads/DiPlay**; Android 7.0–9 asks for storage access and saves there too. **Choose save location** is also available.",
              "If storage is unavailable, the confirmation shows the fallback path and offers **View report** and **Share**.",
              "Review the `.txt` file, then attach it to a GitHub issue.",
            ],
          },
          {
            type: "p",
            text: "Include the car/head-unit model, exact DiLink/Android/firmware versions, iPhone/iOS, connection mode (USB, built-in hotspot, Wi-Fi Direct or Same LAN), relevant settings, steps and the approximate failure time.",
          },
          {
            type: "note",
            tone: "warning",
            text: "Never include your hotspot password. A GitHub issue is public.",
          },
        ],
      },
      {
        id: "whats-in-a-report",
        heading: "What a report contains",
        blocks: [
          {
            type: "p",
            text: "Reports include app/device versions, display settings and negotiation, connection transitions, Wi-Fi band/channel and state, and decoder recovery events. With the optional wheel key service on, they also list the last hardware keys it received.",
          },
          {
            type: "p",
            text: "The exporter filters protocol payloads, credential-bearing lines and common identifiers; Wi-Fi credentials are excluded. Redaction cannot recognize every vendor-specific string, so review reports before posting them publicly.",
          },
        ],
      },
      {
        id: "privacy",
        heading: "Privacy",
        blocks: [
          {
            type: "list",
            items: [
              "The app uses local authentication and a direct USB/Wi-Fi connection to the iPhone. No account, remote authentication service or automatic diagnostic upload is used.",
              "Preferences, paired-device selections, pairing data and bounded logs stay in app storage. Authentication and pairing material are kept out of Android backup.",
              "Microphone access supports Siri and calls. Bluetooth/Nearby devices and Wi-Fi/Location support discovery and transport. The optional local VPN supports the USB link only.",
              "The daily update check sends only the app version in its user agent; GitHub sees the head unit's IP address. Nothing is downloaded without your tap.",
              "The iPhone's CarPlay apps have their own internet and privacy behavior.",
            ],
          },
          {
            type: "p",
            text: "See [Privacy and diagnostics](https://github.com/j1z4/DiPlay/blob/main/docs/PRIVACY.md) for storage locations and the optional Usage Access permission.",
          },
        ],
      },
    ],
  },

  // ------------------------------------------------------------------- Develop
  {
    slug: "build-from-source",
    group: "Develop",
    title: "Build from source",
    summary: "Set up the toolchain, run the CI checks and build a car-test APK.",
    sections: [
      {
        id: "modules",
        heading: "Modules",
        blocks: [
          {
            type: "table",
            head: ["Gradle module", "Purpose"],
            rows: [
              ["`:mobile`", "The main app for Android head units. Build this one."],
              ["`:maphost`", "Sample application that displays a map from the app."],
              ["`:home`", "Optional Home launcher."],
              ["`:automotive`", "Separate Android Automotive application inherited from xcertplay."],
              ["`:common`, `:shared`", "Libraries used by the applications."],
            ],
          },
          {
            type: "note",
            tone: "info",
            text: "If the installed app is **DiPlay map host**, you built `:maphost`. Use the module name in each build command; `./gradlew assembleDebug` can build more than one application.",
          },
        ],
      },
      {
        id: "environment",
        heading: "Prepare the environment",
        blocks: [
          {
            type: "list",
            items: [
              "JDK 25.",
              "Android SDK Platform 37 (`platforms;android-37.0`).",
              "Android SDK Build-Tools 36.0.0 and Platform-Tools.",
              "Android NDK 28.2.13676358.",
            ],
          },
          {
            type: "p",
            text: "Use the included Gradle wrapper. Set `JAVA_HOME` to JDK 25 and `ANDROID_HOME` to your SDK, or set `sdk.dir` in the root `local.properties` (keep it outside Git). The first build needs an internet connection.",
          },
        ],
      },
      {
        id: "ci-checks",
        heading: "Build and run the CI checks",
        blocks: [
          {
            type: "code",
            lang: "sh",
            code: "./gradlew :mobile:assembleDebug",
          },
          {
            type: "p",
            text: "The output is `mobile/build/outputs/apk/debug/mobile-debug.apk`. Before reporting a change as done, run the CI command:",
          },
          {
            type: "code",
            lang: "sh",
            code: "./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :home:testDebugUnitTest \\\n  :mobile:lintDebug :home:lintDebug :maphost:lintDebug \\\n  :mobile:assembleDebug :home:assembleDebug :maphost:assembleDebug",
          },
        ],
      },
      {
        id: "car-test-build",
        heading: "Build an APK for the car",
        blocks: [
          {
            type: "p",
            text: "The repository ships the runtime authentication assets in `mobile/auth-assets/offline-mfi/`: the experimental identity from the DiPlay 0.2.21 release. Every source build includes them, so this APK can connect to an iPhone:",
          },
          {
            type: "code",
            lang: "sh",
            code: "./gradlew :mobile:assembleDebug\n# mobile/build/outputs/apk/debug/mobile-debug.apk",
          },
          {
            type: "p",
            text: "To build with a different identity, point `DIPLAY_AUTH_ASSETS_DIR` at a directory with the same `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b` layout and run `:mobile:assembleStandaloneDebug`, which stops if either file is absent or empty.",
          },
          {
            type: "p",
            text: "Update an existing test app with the same signing key to keep its settings. See [Build](https://github.com/j1z4/DiPlay/blob/main/docs/BUILD.md) for release builds and troubleshooting.",
          },
        ],
      },
    ],
  },
];
