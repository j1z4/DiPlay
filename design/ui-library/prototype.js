/*
 * The walkthrough: every DiPlay screen built from library components only.
 * Copy comes from common/src/main/res/values/strings.xml where the app has it.
 *
 *   const app = DPPrototype.mount(hostElement, { interactive: true });
 *   app.go("settings");
 */
(() => {
  "use strict";

  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const chevron = '<i data-dp-icon="chevron" class="dp-row__chevron"></i>';

  /* ---------- Settings model ---------- */

  // Categories and placement follow SettingsInformationArchitecture in DiPlayActivity.kt.
  const CATEGORIES = [
    { id: "overview", icon: "overview", title: "Overview" },
    { id: "connection", icon: "connection", title: "Connection" },
    { id: "display", icon: "display", title: "Display" },
    { id: "audio", icon: "audio", title: "Audio" },
    { id: "navigation", icon: "navigation", title: "Navigation" },
    { id: "vehicle", icon: "car", title: "Vehicle" },
    { id: "diagnostics", icon: "diagnostics", title: "Diagnostics" },
    { id: "advanced", icon: "advanced", title: "Advanced" },
    { id: "about", icon: "about", title: "About" },
  ];

  const PICKERS = {
    iphone: { title: "Choose iPhone", icon: "connection", note: "DiPlay connects to this iPhone when it is nearby.", options: ["My iPhone", "Work iPhone"] },
    connectionMode: { title: "Default connection mode", icon: "connection", note: "Wireless joins the car hotspot. USB uses the cable and charges the iPhone.", options: ["Wi-Fi", "USB"], reconnect: true },
    charging: { title: "iPhone charging", icon: "connection", note: "Lower current keeps older head units stable.", options: ["Normal 2.4 A", "Reduced 1.5 A", "Low 0.5 A"], reconnect: true },
    nightMode: { title: "CarPlay night mode", icon: "night", note: "System follows the car's headlights signal.", options: ["System", "Ambient light", "Day", "Night", "Schedule"] },
    carplaySize: { title: "CarPlay size", icon: "display", note: "Applies at the next connection.", options: ["Full screen", "Widescreen", "Standard"], reconnect: true },
    musicBuffer: { title: "Music buffer · wireless", icon: "audio", note: "A larger buffer rides out Wi-Fi drops but starts later.", options: ["Low latency", "Balanced", "Smooth"] },
    fingers: { title: "Quick menu swipe-down fingers", icon: "controls", note: "How many fingers open the DiPlay menu inside CarPlay.", options: ["2 fingers", "3 fingers", "Off"] },
    siriKey: { title: "Siri key", icon: "car", note: "The steering-wheel key that starts Siri.", options: ["Voice button", "Phone button", "None"] },
    language: { title: "App language", icon: "language", note: "CarPlay itself follows the iPhone's language.", options: ["English", "العربية", "Italiano", "Русский", "简体中文", "繁體中文", "日本語"] },
  };

  const DEFAULTS = {
    iphone: "My iPhone",
    connectionMode: "Wi-Fi",
    charging: "Normal 2.4 A",
    nightMode: "System",
    carplaySize: "Full screen",
    musicBuffer: "Balanced",
    fingers: "3 fingers",
    siriKey: "Voice button",
    language: "English",
    appearance: "Dark",
    fps: "60 fps",
    connectOnOpen: true,
    reconnectOnReturn: true,
    openAfterStart: false,
    autoUsb: true,
    fullScreen: true,
    reportLocation: true,
    hideTop: false,
    hideBottom: true,
    speakerCalls: false,
    hud: true,
    rhd: false,
    fpsCounter: false,
    splitScreen: false,
    hevc: false,
    backgroundUpdates: true,
    navVolume: 3,
    interfaceSize: 100,
  };

  const row = {
    toggle: (key, label, hint) => ({ kind: "toggle", key, label, hint }),
    pick: (key, label) => ({ kind: "pick", key, label }),
    link: (label, act, opts = {}) => ({ kind: "link", label, act, ...opts }),
    danger: (label, act) => ({ kind: "danger", label, act }),
    info: (label, value) => ({ kind: "info", label, value }),
    slider: (key, label, min, max, step, format) => ({ kind: "slider", key, label, min, max, step, format }),
    segment: (key, label, options) => ({ kind: "segment", key, label, options }),
  };

  const PANES = {
    overview: [
      { header: "Status", rows: [{ kind: "status" }] },
      {
        header: "Quick settings",
        rows: [
          row.toggle("connectOnOpen", "Connect when DiPlay opens"),
          row.pick("nightMode", "CarPlay night mode"),
          row.toggle("fullScreen", "Full screen"),
          row.toggle("reportLocation", "Report location to iPhone"),
        ],
      },
      { header: "Help", rows: [row.link("Setup guide", "go:setup", { icon: "automation" }), row.pick("language", "App language")] },
    ],
    connection: [
      {
        header: "Automatic connection",
        rows: [
          row.toggle("connectOnOpen", "Connect when DiPlay opens"),
          row.toggle("reconnectOnReturn", "Connect when the selected iPhone reconnects"),
          row.pick("connectionMode", "Default connection mode"),
          row.toggle("openAfterStart", "Open after the car starts"),
          row.pick("iphone", "Choose iPhone"),
        ],
      },
      { header: "USB connection", rows: [row.toggle("autoUsb", "Auto-confirm USB permission"), row.pick("charging", "iPhone charging")] },
      {
        header: "Reset",
        rows: [row.danger("Reset CarPlay Wi-Fi", "dialog:reset")],
        footer: "Forgets the CarPlay Wi-Fi group on this head unit. Your iPhone stays paired.",
      },
    ],
    display: [
      { header: "Appearance", rows: [row.segment("appearance", "App appearance", ["Dark", "Light", "Auto"]), row.pick("nightMode", "CarPlay night mode")] },
      {
        header: "CarPlay picture",
        rows: [
          row.pick("carplaySize", "CarPlay size"),
          row.segment("fps", "Frame rate", ["30 fps", "60 fps"]),
          row.toggle("hideTop", "Hide top bar"),
          row.toggle("hideBottom", "Hide bottom bar"),
        ],
      },
      { header: "Interface", rows: [row.slider("interfaceSize", "Interface size", 80, 130, 5, "{v}%")] },
    ],
    audio: [
      {
        header: "Audio routing",
        rows: [
          row.slider("navVolume", "Navigation guidance volume", -12, 12, 1, "{v} dB"),
          row.pick("musicBuffer", "Music buffer · wireless"),
          row.toggle("speakerCalls", "Calls on the loudspeaker", "Route phone calls to the car speakers"),
        ],
      },
    ],
    navigation: [
      { header: "Location", rows: [row.toggle("reportLocation", "Report location to iPhone", "Uses the car's GPS for Maps")] },
      { header: "BYD navigation", rows: [row.toggle("hud", "Turn-by-turn on HUD and dashboard")] },
    ],
    vehicle: [
      { header: "CarPlay controls", rows: [row.pick("fingers", "Quick menu swipe-down fingers"), row.toggle("rhd", "Right-hand drive")] },
      { header: "Wheel keys", rows: [row.pick("siriKey", "Siri key")] },
    ],
    diagnostics: [
      {
        header: "Reports",
        rows: [row.link("Save diagnostic report", "toast:Report saved"), row.link("Get the report on a phone", "toast:Scan the QR code on your phone"), row.toggle("fpsCounter", "FPS counter")],
        footer: "Reports leave out phone numbers, names and locations.",
      },
    ],
    advanced: [
      { banner: "Change with care. These options depend on firmware and can break sound, video or the connection." },
      {
        header: "Display (experimental)",
        rows: [row.toggle("splitScreen", "Split screen (experimental)"), row.toggle("hevc", "HEVC video (experimental)", "Applies at the next connection")],
      },
    ],
    about: [
      { header: "DiPlay", rows: [row.info("Version", "0.2.21 · public preview"), row.link("Check for updates", "toast:DiPlay is up to date"), row.toggle("backgroundUpdates", "Check in the background")] },
      { header: "Settings", rows: [row.link("Back up settings", "toast:Scan the QR code to save a backup")] },
    ],
  };

  const STAGES = ["Getting CarPlay ready…", "Looking for your paired iPhone…", "Joining the car hotspot…", "Opening CarPlay…"];

  const SETUP = [
    { title: "Your car", body: "DiPlay couldn’t read the DiLink version. Choose it below, or keep Not sure.", key: "car", options: ["DiLink 3", "DiLink 4", "DiLink 5", "Not sure"] },
    {
      title: "Connection",
      body: "Choose how the iPhone reaches the car. Most cars work best with the car’s own hotspot on 5 GHz.",
      key: "link",
      options: ["Car hotspot", "Wi-Fi Direct", "Existing Wi-Fi"],
      hints: ["Recommended · 5 GHz", "No hotspot needed · may use 2.4 GHz", "Car and iPhone on the same network"],
    },
    { title: "Pair your iPhone", body: "On your iPhone, open Settings › Bluetooth and choose this car. Then tap Use CarPlay.", pair: true },
    {
      title: "Features",
      body: "Turn on what you want now. Everything stays in Settings and can be changed later.",
      toggles: [
        ["connectOnOpen", "Connect when DiPlay opens", "Tested"],
        ["openAfterStart", "Open after the car starts", "Tested"],
        ["reportLocation", "Report location to iPhone", "Tested"],
        ["splitScreen", "Split screen (experimental)", "Experimental"],
      ],
    },
    { title: "You're ready", body: "DiPlay is set up. Connect your iPhone now, or change anything later in Settings.", done: true },
  ];

  /* ---------- Rendering helpers ---------- */

  function renderRow(r, s) {
    const fid = `${r.kind}:${r.key || r.label}`;
    switch (r.kind) {
      case "toggle":
        return `<button class="dp-row" role="switch" aria-checked="${!!s[r.key]}" data-key="${r.key}" data-fid="${fid}" data-dp-focusable>
          <span class="dp-row__body"><span class="dp-row__label">${esc(r.label)}</span>${r.hint ? `<span class="dp-row__hint">${esc(r.hint)}</span>` : ""}</span>
          <span class="dp-switch"></span></button>`;
      case "pick":
        return `<button class="dp-row" data-act="pick:${r.key}" data-fid="${fid}" data-dp-focusable>
          <span class="dp-row__body"><span class="dp-row__label">${esc(r.label)}</span></span>
          <span class="dp-row__value" data-value-of="${r.key}">${esc(s[r.key])}</span>${chevron}</button>`;
      case "link":
        return `<button class="dp-row" data-act="${r.act}" data-fid="${fid}" data-dp-focusable>
          ${r.icon ? `<span class="dp-row__icon"><i data-dp-icon="${r.icon}"></i></span>` : ""}
          <span class="dp-row__body"><span class="dp-row__label">${esc(r.label)}</span></span>${chevron}</button>`;
      case "danger":
        return `<button class="dp-row dp-row--danger" data-act="${r.act}" data-fid="${fid}" data-dp-focusable>
          <span class="dp-row__body"><span class="dp-row__label">${esc(r.label)}</span></span></button>`;
      case "info":
        return `<div class="dp-row dp-row--info"><span class="dp-row__body"><span class="dp-row__label">${esc(r.label)}</span></span>
          <span class="dp-row__value">${esc(r.value)}</span></div>`;
      case "slider":
        return `<div class="dp-row scr-slider-row" data-fid="${fid}" data-dp-focusable>
          <label class="dp-slider"><span class="dp-slider__head">${esc(r.label)} <span class="dp-slider__value"></span></span>
          <input type="range" tabindex="-1" min="${r.min}" max="${r.max}" step="${r.step}" value="${s[r.key]}" data-key="${r.key}" data-format="${r.format}"></label></div>`;
      case "segment":
        return `<div class="dp-row scr-segment-row"><span class="dp-row__body"><span class="dp-row__label">${esc(r.label)}</span></span>
          <div class="dp-segmented dp-segmented--compact" role="radiogroup" data-key="${r.key}"><span class="dp-segmented__thumb"></span>
          ${r.options.map((o) => `<button class="dp-segmented__option" role="radio" aria-checked="${s[r.key] === o}" data-value="${esc(o)}" data-fid="seg:${r.key}:${esc(o)}" data-dp-focusable>${esc(o)}</button>`).join("")}
          </div></div>`;
      case "status": {
        const st = statusOf(s);
        return `<button class="dp-row" style="--h:76px" data-act="${s.connected ? "toast:CarPlay is running" : "go:connecting"}" data-fid="status" data-dp-focusable>
          <span class="dp-row__icon"><i data-dp-icon="connection"></i></span>
          <span class="dp-row__body"><span class="dp-row__label">CarPlay</span><span class="dp-row__hint">${esc(st.text)}</span></span>
          <span class="dp-status dp-status--${st.tone}">${st.connected ? "Connected" : "Ready"}</span></button>`;
      }
    }
    return "";
  }

  function statusOf(s) {
    if (s.connected) return { tone: "connected", text: "CarPlay connected", connected: true };
    return { tone: "waiting", text: `Ready for ${s.iphone}` };
  }

  function renderPane(cat, s) {
    const groups = PANES[cat]
      .map((g) => {
        if (g.banner) return `<div class="dp-banner dp-banner--warning"><span class="dp-banner__text">${esc(g.banner)}</span></div>`;
        return `<div class="dp-group">
          ${g.header ? `<div class="dp-group__header">${esc(g.header)}</div>` : ""}
          ${g.rows.map((r) => renderRow(r, s)).join("")}
          ${g.footer ? `<div class="dp-group__footer">${esc(g.footer)}</div>` : ""}
        </div>`;
      })
      .join("");
    const reconnect = s.reconnectNeeded
      ? `<div class="dp-banner"><span class="dp-banner__text">Saved. Some changes apply when CarPlay reconnects.</span>
         <button class="dp-btn dp-btn--accent dp-btn--sm" data-act="reconnect" data-fid="reconnect" data-dp-focusable>Reconnect now</button></div>`
      : "";
    return `<div class="scr-pane__inner dp-stagger">${reconnect}${groups}</div>`;
  }

  /* ---------- Screens ---------- */

  const SCREENS = {
    home: {
      html: (s) => {
        const st = statusOf(s);
        return `
        <div class="scr-home__left dp-stagger">
          <div class="dp-card">
            <button class="dp-card__item" data-act="pick:iphone" data-fid="home:iphone" data-dp-focusable>
              <span class="dp-card__label">Choose this Device</span>
              <span class="dp-card__value" data-value-of="iphone">${esc(s.iphone)}</span>
            </button>
            <button class="dp-card__item" data-act="pick:connectionMode" data-fid="home:mode" data-dp-focusable>
              <span class="dp-card__label">Connection</span>
              <span class="dp-card__value" data-value-of="connectionMode">${esc(s.connectionMode)}</span>
            </button>
          </div>
          <button class="dp-row dp-row--slate" data-act="go:settings" data-fid="home:settings" data-dp-focusable>
            <span class="dp-row__body"><span class="dp-row__label">Settings</span></span>${chevron}
          </button>
        </div>
        <div class="scr-home__rule"></div>
        <div class="scr-home__right">
          <div class="dp-wordmark">
            <div class="dp-wordmark__name">DiPlay</div>
            <div class="dp-wordmark__tagline">CarPlay, that you own.</div>
          </div>
          <div class="scr-home__cta">
            <span class="dp-status dp-status--${st.tone}">${esc(st.text)}</span>
            <button class="dp-btn dp-btn--primary dp-btn--lg" data-act="${s.connected ? "toast:Opening CarPlay" : "go:connecting"}" data-fid="home:connect" data-dp-focusable data-autofocus>
              ${s.connected ? "Open CarPlay" : "Connect phone"}
            </button>
          </div>
        </div>`;
      },
    },

    connecting: {
      html: (s, p) => `
        <div class="scr-center dp-stagger">
          <div class="dp-connecting" id="conn"><span class="dp-connecting__ring"></span><span class="dp-connecting__ring"></span><span class="dp-connecting__ring"></span>
            <span class="dp-connecting__core"><i data-dp-icon="connection"></i></span></div>
          <h1 class="scr-title" id="conn-stage">${STAGES[0]}</h1>
          <p class="scr-sub">${s.connectionMode === "USB" ? "Keep the cable plugged in and the iPhone unlocked." : `Keep ${esc(s.iphone)} unlocked and nearby, with Wi-Fi and Bluetooth on.`}</p>
          <button class="dp-btn dp-btn--ghost" data-act="back" data-dp-focusable data-autofocus>Cancel</button>
        </div>`,
      mount(el, ctx, p) {
        let i = 0;
        const stage = el.querySelector("#conn-stage");
        const next = () => {
          i++;
          if (i < STAGES.length) {
            stage.textContent = STAGES[i];
            stage.classList.remove("is-changing");
            void stage.offsetWidth;
            stage.classList.add("is-changing");
            ctx.later(next, 900);
          } else if (p.fail) {
            ctx.replace("failed");
          } else {
            el.querySelector("#conn").className = "dp-connecting dp-connecting--done";
            el.querySelector("#conn .dp-connecting__core").innerHTML = '<i data-dp-icon="permissions"></i>';
            DP.init(el);
            stage.textContent = "CarPlay connected";
            ctx.state.connected = true;
            ctx.later(() => {
              ctx.home();
              DP.toast("CarPlay connected", { host: ctx.host });
            }, 1200);
          }
        };
        ctx.later(next, 900);
      },
    },

    failed: {
      html: () => `
        <div class="scr-center dp-stagger">
          <span class="dp-status dp-status--failed">Connection interrupted</span>
          <h1 class="scr-title">CarPlay didn’t start</h1>
          <p class="scr-sub">An old Wi-Fi Direct group may block this connection. Select Reset CarPlay Wi-Fi below, then connect again.</p>
          <div class="scr-actions">
            <button class="dp-btn dp-btn--primary dp-btn--lg" data-act="replace:connecting" data-dp-focusable data-autofocus>Retry connection</button>
            <button class="dp-btn dp-btn--lg" data-act="dialog:reset" data-dp-focusable>Reset CarPlay Wi-Fi</button>
            <button class="dp-btn dp-btn--ghost dp-btn--lg" data-act="home" data-dp-focusable>Back to DiPlay</button>
          </div>
        </div>`,
    },

    settings: {
      html: (s, p) => `
        <header class="scr-head">
          <button class="dp-btn dp-btn--icon dp-btn--sm" aria-label="Back" data-act="back" data-fid="back" data-dp-focusable><i data-dp-icon="back"></i></button>
          <h1 class="scr-head__title">Settings</h1>
          <span class="scr-head__spacer"></span>
        </header>
        <div class="scr-settings__body">
          <nav class="dp-rail scr-rail">
            ${CATEGORIES.map(
              (c) => `<button class="dp-rail__item" data-cat="${c.id}" data-fid="cat:${c.id}" ${c.id === s.cat ? 'aria-current="page" data-autofocus' : ""} data-dp-focusable>
                <i data-dp-icon="${c.icon}"></i>${c.title}</button>`,
            ).join("")}
          </nav>
          <div class="scr-pane" data-dp-scroll>${renderPane(s.cat, s)}</div>
        </div>`,
      mount(el, ctx) {
        el.addEventListener("dp-focus", (e) => {
          const cat = e.target.dataset?.cat;
          if (cat && cat !== ctx.state.cat) ctx.setCategory(cat);
        });
        // Left from the pane returns to the current category, not the nearest one;
        // right from the rail enters the pane at the top. Same as tvOS.
        const onKey = (e) => {
          if (!el.isConnected) return document.removeEventListener("keydown", onKey, true);
          if (DP.focus.scope !== el) return;
          const cur = DP.focus.current;
          if (e.key === "ArrowLeft" && cur?.closest(".scr-pane") && !cur.querySelector("input[type=range]") && !cur.matches(".dp-segmented__option:not(:first-of-type)")) {
            DP.focus.set(el.querySelector('.dp-rail__item[aria-current="page"]'));
            e.preventDefault();
            e.stopImmediatePropagation();
          } else if (e.key === "ArrowRight" && cur?.matches(".dp-rail__item")) {
            const first = el.querySelector(".scr-pane [data-dp-focusable]");
            if (first) DP.focus.set(first);
            e.preventDefault();
            e.stopImmediatePropagation();
          }
        };
        document.addEventListener("keydown", onKey, true);
      },
    },

    picker: {
      html: (s, p) => {
        const def = PICKERS[p.key];
        return `
        <header class="scr-head">
          <button class="dp-btn dp-btn--icon dp-btn--sm" aria-label="Back" data-act="back" data-dp-focusable><i data-dp-icon="back"></i></button>
          <h1 class="scr-head__title">${esc(def.title)}</h1>
          <span class="scr-head__spacer"></span>
        </header>
        <div class="scr-split">
          <div class="scr-split__art"><span class="scr-art"><i data-dp-icon="${def.icon}"></i></span><p>${esc(def.note)}</p></div>
          <div class="scr-split__list dp-stagger" data-dp-scroll>
            ${def.options
              .map(
                (o) => `<button class="dp-row" role="radio" aria-checked="${s[p.key] === o}" data-act="choose:${p.key}" data-value="${esc(o)}" data-dp-focusable ${s[p.key] === o ? "data-autofocus" : ""}>
                <span class="dp-row__body"><span class="dp-row__label">${esc(o)}</span></span>
                ${s[p.key] === o ? '<i data-dp-icon="permissions" class="scr-check"></i>' : ""}</button>`,
              )
              .join("")}
          </div>
        </div>`;
      },
    },

    setup: {
      html: (s, p) => {
        const n = p.step || 0;
        const step = SETUP[n];
        const dots = SETUP.map((_, i) => `<span class="dp-steps__dot ${i < n ? "is-done" : ""} ${i === n ? "is-current" : ""}"></span>`).join("");
        let body = "";
        if (step.options) {
          body = step.options
            .map(
              (o, i) => `<button class="dp-row" role="radio" aria-checked="${s.setup[step.key] === o}" data-act="setup-choose:${step.key}" data-value="${esc(o)}" data-dp-focusable ${s.setup[step.key] === o || (!s.setup[step.key] && i === 0) ? "data-autofocus" : ""}>
              <span class="dp-row__body"><span class="dp-row__label">${esc(o)}</span>${step.hints ? `<span class="dp-row__hint">${esc(step.hints[i])}</span>` : ""}</span>
              ${s.setup[step.key] === o ? '<i data-dp-icon="permissions" class="scr-check"></i>' : ""}</button>`,
            )
            .join("");
        } else if (step.toggles) {
          body = step.toggles
            .map(
              ([key, label, badge]) => `<button class="dp-row" role="switch" aria-checked="${!!s[key]}" data-key="${key}" data-dp-focusable>
              <span class="dp-row__body"><span class="dp-row__label">${esc(label)}</span><span class="dp-row__hint">${badge}</span></span><span class="dp-switch"></span></button>`,
            )
            .join("");
        } else if (step.pair) {
          body = `<div class="scr-pair"><div class="dp-connecting" id="pair"><span class="dp-connecting__ring"></span><span class="dp-connecting__ring"></span><span class="dp-connecting__ring"></span>
            <span class="dp-connecting__core"><i data-dp-icon="connection"></i></span></div>
            <span class="dp-status dp-status--connecting" id="pair-status">Waiting for paired iPhone</span></div>`;
        } else if (step.done) {
          body = `<div class="scr-pair"><div class="dp-connecting dp-connecting--done"><span class="dp-connecting__core"><i data-dp-icon="permissions"></i></span></div></div>
            <button class="dp-btn dp-btn--primary dp-btn--lg dp-btn--block" data-act="go:connecting" data-dp-focusable data-autofocus>Connect phone</button>
            <button class="dp-btn dp-btn--lg dp-btn--block" data-act="settings-root" data-dp-focusable>Settings</button>`;
        }
        return `
        <header class="scr-head scr-head--setup">
          <div class="dp-steps" aria-label="Step ${n + 1} of ${SETUP.length}">${dots}</div>
          <span class="scr-head__step">STEP ${n + 1} OF ${SETUP.length}</span>
        </header>
        <div class="scr-split">
          <div class="scr-split__art scr-split__art--text">
            <h1 class="scr-title">${esc(step.title)}</h1>
            <p class="scr-sub">${esc(step.body)}</p>
          </div>
          <div class="scr-split__list dp-stagger" data-dp-scroll>${body}</div>
        </div>
        <footer class="scr-foot">
          ${step.done ? "" : `<button class="dp-btn dp-btn--ghost" data-act="home" data-dp-focusable>Skip setup</button>`}
          ${n > 0 ? `<button class="dp-btn" data-act="back" data-dp-focusable>Back</button>` : ""}
          <span class="scr-head__spacer"></span>
          <button class="dp-btn ${step.done ? "dp-btn--primary" : "dp-btn--primary"}" data-act="${step.done ? "home" : "setup-next"}" data-dp-focusable ${step.toggles || step.pair ? "data-autofocus" : ""}>${step.done ? "Done" : "Next"}</button>
        </footer>`;
      },
      mount(el, ctx, p) {
        if (!SETUP[p.step || 0].pair) return;
        ctx.later(() => {
          const pair = el.querySelector("#pair");
          pair.className = "dp-connecting dp-connecting--done";
          pair.innerHTML = '<span class="dp-connecting__core"><i data-dp-icon="permissions"></i></span>';
          DP.init(pair);
          const st = el.querySelector("#pair-status");
          st.className = "dp-status dp-status--connected";
          st.textContent = `Paired with ${ctx.state.iphone}`;
        }, 2200);
      },
    },
  };

  /* ---------- Controller ---------- */

  function mount(host, { interactive = true, start = "home" } = {}) {
    const state = { ...DEFAULTS, cat: "overview", connected: false, reconnectNeeded: false, setup: {} };
    const stack = [];
    let timers = [];
    let active = false;
    let current = null;

    const ctx = {
      host,
      state,
      later(fn, ms) {
        timers.push(setTimeout(fn, ms));
      },
      go: (name, params = {}) => go(name, params),
      replace: (name, params = {}) => {
        stack.pop();
        go(name, params);
      },
      home: () => {
        stack.length = 0;
        go("home");
      },
      setCategory(cat) {
        state.cat = cat;
        const pane = current.querySelector(".scr-pane");
        current.querySelectorAll(".dp-rail__item").forEach((b) => b.dataset.cat === cat ? b.setAttribute("aria-current", "page") : b.removeAttribute("aria-current"));
        pane.scrollTop = 0;
        pane.innerHTML = renderPane(cat, state);
        DP.init(pane);
      },
    };

    function render(name, params, { back = false, restore = null } = {}) {
      timers.forEach(clearTimeout);
      timers = [];
      host.querySelectorAll(":scope > .scr").forEach((el) => el.remove());
      const el = document.createElement("div");
      el.className = `scr scr-${name} dp-screen${back ? " is-back" : ""}`;
      el.dataset.dpFocusScope = "";
      el.innerHTML = SCREENS[name].html(state, params);
      host.prepend(el);
      current = el;
      DP.init(el);
      SCREENS[name].mount?.(el, ctx, params);
      if (active) {
        const target = (restore && el.querySelector(`[data-fid="${CSS.escape(restore)}"]`)) || el.querySelector("[data-autofocus]");
        DP.focus.reset(el, target);
      }
    }

    function go(name, params = {}) {
      if (stack.length) stack[stack.length - 1].focus = DP.focus.current?.dataset?.fid;
      stack.push({ name, params });
      render(name, params);
    }

    function back() {
      if (stack.length <= 1) return;
      stack.pop();
      const top = stack[stack.length - 1];
      render(top.name, top.params, { back: true, restore: top.focus });
    }

    function choose(key, value) {
      const changed = state[key] !== value;
      state[key] = value;
      if (changed && PICKERS[key]?.reconnect && state.connected) state.reconnectNeeded = true;
      back();
      // The value slides in on the row the driver came back to.
      if (changed) current.querySelectorAll(`[data-value-of="${key}"]`).forEach((v) => {
        v.classList.remove("is-changing");
        void v.offsetWidth;
        v.classList.add("is-changing");
      });
      if (changed && PICKERS[key]?.reconnect && !state.connected) DP.toast("Saved. Applies at the next connection", { host });
    }

    async function act(a, el) {
      const [verb, arg] = a.split(/:(.*)/s);
      switch (verb) {
        case "go": return go(arg, arg === "connecting" ? { fail: host.dataset.fail === "1" } : {});
        case "replace": return ctx.replace(arg);
        case "home": return ctx.home();
        case "back": return back();
        case "pick": return go("picker", { key: arg });
        case "choose": return choose(arg, el.dataset.value);
        case "toast": return DP.toast(arg, { host });
        case "settings-root":
          stack.length = 0;
          go("home");
          return go("settings");
        case "reconnect":
          state.reconnectNeeded = false;
          state.connected = false;
          return go("connecting");
        case "setup-choose":
          state.setup[arg] = el.dataset.value;
          el.parentElement.querySelectorAll('[role="radio"]').forEach((r) => {
            const on = r === el;
            r.setAttribute("aria-checked", String(on));
            r.querySelector(".scr-check")?.remove();
            if (on) r.insertAdjacentHTML("beforeend", '<i data-dp-icon="permissions" class="scr-check"></i>');
          });
          DP.init(el.parentElement);
          return;
        case "setup-next": {
          const step = (stack[stack.length - 1].params.step || 0) + 1;
          return go("setup", { step });
        }
        case "dialog": {
          const ok = await DP.dialog({
            host,
            title: "Reset CarPlay Wi-Fi?",
            body: "DiPlay forgets the CarPlay Wi-Fi group and connects again. CarPlay stops for a few seconds.",
            actions: [
              { label: "Cancel", value: false },
              { label: "Reset and connect", kind: "danger", value: true },
            ],
          });
          if (ok) {
            state.connected = false;
            go("connecting");
          }
        }
      }
    }

    host.addEventListener("click", (e) => {
      const el = e.target.closest("[data-act]");
      if (el && host.contains(el)) act(el.dataset.act, el);
      const cat = e.target.closest("[data-cat]");
      if (cat) {
        if (cat.dataset.cat !== state.cat) ctx.setCategory(cat.dataset.cat);
        if (active) DP.focus.set(current.querySelector(".scr-pane [data-dp-focusable]") || cat);
      }
    });
    host.addEventListener("dp-change", (e) => {
      const key = e.target.closest("[data-key]")?.dataset.key;
      if (key) state[key] = e.detail.value;
    });
    host.addEventListener("input", (e) => {
      if (e.target.dataset.key) state[e.target.dataset.key] = Number(e.target.value);
    });
    host.addEventListener("dp-back", back);

    go(start);

    return {
      go: (name) => {
        if (!SCREENS[name]) return;
        stack.length = 0;
        if (name !== "home") stack.push({ name: "home", params: {} });
        if (name === "failed") state.connected = false;
        stack.push({ name, params: {} });
        render(name, {});
      },
      activate() {
        active = interactive;
        if (active) DP.focus.reset(current, current.querySelector("[data-autofocus]"));
      },
      deactivate() {
        active = false;
        DP.focus.scopes = [];
        DP.focus.set(null);
      },
      state,
    };
  }

  window.DPPrototype = { mount, CATEGORIES };
})();
