/*
 * The walkthrough: every OpenPlay screen built from library components only.
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
    iphone: { title: "Choose iPhone", icon: "connection", note: "OpenPlay connects to this iPhone when it is nearby.", options: ["Device Name", "Work iPhone"] },
    connectionMode: { title: "Default connection mode", icon: "connection", note: "Wireless joins the car hotspot. USB uses the cable and charges the iPhone.", options: ["Wi-Fi", "USB"], reconnect: true },
    charging: { title: "iPhone charging", icon: "connection", note: "Lower current keeps older head units stable.", options: ["Normal 2.4 A", "Reduced 1.5 A", "Low 0.5 A"], reconnect: true },
    nightMode: { title: "CarPlay night mode", icon: "night", note: "System follows the car's headlights signal.", options: ["System", "Ambient light", "Day", "Night", "Schedule"] },
    carplaySize: { title: "CarPlay size", icon: "display", note: "Applies at the next connection.", options: ["Full screen", "Widescreen", "Standard"], reconnect: true },
    musicBuffer: { title: "Music buffer · wireless", icon: "audio", note: "A larger buffer rides out Wi-Fi drops but starts later.", options: ["Low latency", "Balanced", "Smooth"] },
    fingers: { title: "Quick menu swipe-down fingers", icon: "controls", note: "How many fingers open the OpenPlay menu inside CarPlay.", options: ["2 fingers", "3 fingers", "Off"] },
    siriKey: { title: "Siri key", icon: "car", note: "The steering-wheel key that starts Siri.", options: ["Voice button", "Phone button", "None"] },
    language: { title: "App language", icon: "language", note: "CarPlay itself follows the iPhone's language.", options: ["English", "العربية", "Italiano", "Русский", "简体中文", "繁體中文", "日本語"] },
  };

  const DEFAULTS = {
    iphone: "Device Name",
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
          row.toggle("connectOnOpen", "Connect when OpenPlay opens"),
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
          row.toggle("connectOnOpen", "Connect when OpenPlay opens"),
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
      { header: "OpenPlay", rows: [row.info("Version", "0.2.21 · public preview"), row.link("Check for updates", "toast:OpenPlay is up to date"), row.toggle("backgroundUpdates", "Check in the background")] },
      { header: "Settings", rows: [row.link("Back up settings", "toast:Scan the QR code to save a backup")] },
    ],
  };

  const STAGES = ["Getting CarPlay ready…", "Looking for your paired iPhone…", "Joining the car hotspot…", "Opening CarPlay…"];

  const SETUP = [
    { title: "Your car", body: "OpenPlay couldn’t read the DiLink version. Choose it below, or keep Not sure.", key: "car", options: ["DiLink 3", "DiLink 4", "DiLink 5", "Not sure"] },
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
        ["connectOnOpen", "Connect when OpenPlay opens", "Tested"],
        ["openAfterStart", "Open after the car starts", "Tested"],
        ["reportLocation", "Report location to iPhone", "Tested"],
        ["splitScreen", "Split screen (experimental)", "Experimental"],
      ],
    },
    { title: "You're ready", body: "OpenPlay is set up. Connect your iPhone now, or change anything later in Settings.", done: true },
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
          <span class="dp-status dp-status--${st.tone}">${st.short}</span></button>`;
      }
    }
    return "";
  }

  // One source for the connection state; the device card and Overview both read it.
  function statusOf(s) {
    if (s.connecting) return { tone: "connecting", text: "Connecting…", short: "Connecting" };
    if (s.connected && s.reconnectNeeded) return { tone: "warning", text: "Reconnect to apply changes", short: "Reconnect", connected: true };
    if (s.connected) return { tone: "connected", text: "CarPlay connected", short: "Connected", connected: true };
    if (s.failed) return { tone: "failed", text: "Connection interrupted", short: "Failed" };
    return { tone: "waiting", text: "Ready to connect", short: "Ready" };
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

  /* ---------- Brand stage: splash and loading screen ---------- */

  function brandStage(s, { filled, tone, text, loading }) {
    const hint = s.connectionMode === "USB" ? "Keep the cable plugged in and the iPhone unlocked." : `Keep ${esc(s.iphone)} unlocked and nearby.`;
    return `
      <div class="scr-brand">
        <div class="dp-splash${loading ? " is-loading" : ""}" data-dp-splash="${filled ? "filled" : ""}">
          <div class="dp-splash__after dp-wordmark">
            <div class="dp-wordmark__lockup">
              <div class="dp-wordmark__name">OpenPlay</div>
              <div class="dp-wordmark__fork">(a fork of DiPlay)</div>
            </div>
            <div class="dp-wordmark__tagline">CarPlay, that you own.</div>
          </div>
        </div>
      </div>
      <div class="scr-corner">
        <span class="dp-statusline" data-tone="${tone}" role="status"><span class="dp-dot"></span><span class="dp-statusline__text">${esc(text)}</span></span>
        <span class="scr-corner__hint" ${loading ? "" : "hidden"}>${hint}</span>
      </div>
      <div class="scr-corner scr-corner--right" ${loading ? "" : "hidden"}>
        <button class="dp-btn dp-btn--ghost" data-act="cancel-connect" data-fid="cancel" data-dp-focusable ${loading ? "data-autofocus" : ""}>Cancel</button>
      </div>`;
  }

  function setStatus(el, tone, text) {
    const line = el.querySelector(".dp-statusline");
    const label = line.querySelector(".dp-statusline__text");
    line.dataset.tone = tone;
    if (label.textContent === text) return;
    label.textContent = text;
    label.classList.remove("is-changing");
    void label.offsetWidth;
    label.classList.add("is-changing");
  }

  // Runs the connection on a brand stage: stages tick through the corner status while the
  // light runs round the mark; then the mark pops and Home takes over, or it shakes and
  // the failure screen follows.
  function startLoading(el, ctx, p) {
    ctx.state.failed = false;
    const splash = el.querySelector(".dp-splash");
    splash.classList.add("is-loading");
    el.querySelectorAll(".scr-corner[hidden], .scr-corner__hint[hidden]").forEach((c) => c.removeAttribute("hidden"));
    const cancel = el.querySelector('[data-act="cancel-connect"]');
    if (DP.focus.scope === el) DP.focus.set(cancel);
    let i = 0;
    setStatus(el, "connecting", STAGES[0]);
    const next = () => {
      i++;
      if (i < STAGES.length) {
        setStatus(el, "connecting", STAGES[i]);
        return ctx.later(next, 900);
      }
      splash.classList.remove("is-loading");
      el.querySelector(".scr-corner--right").setAttribute("hidden", "");
      el.querySelector(".scr-corner__hint").setAttribute("hidden", "");
      if (p.fail) {
        splash.classList.add("is-failed");
        setStatus(el, "failed", "Connection interrupted");
        return ctx.later(() => ctx.replace("failed"), 1200);
      }
      splash.classList.add("is-done");
      setStatus(el, "connected", "CarPlay connected");
      ctx.state.connected = true;
      ctx.later(() => {
        ctx.morphHome();
        DP.toast("CarPlay connected", { host: ctx.host });
      }, 1200);
    };
    ctx.later(next, 900);
  }

  /* ---------- Screens ---------- */

  const SCREENS = {
    // The splash trace, then the wordmark rises and Home takes over.
    // Boot: the trace plays with "Starting OpenPlay…" in the corner. With "Connect when
    // OpenPlay opens" on, the same screen turns into the loading screen; otherwise it
    // settles on "Ready to connect" and morphs into Home.
    splash: {
      html: (s) => brandStage(s, { filled: false, tone: "waiting", text: "Starting OpenPlay…", loading: false }),
      mount(el, ctx) {
        ctx.later(() => {
          if (ctx.state.connectOnOpen && !ctx.state.connected) return startLoading(el, ctx, {});
          setStatus(el, "waiting", "Ready to connect");
          ctx.later(() => ctx.morphHome(), 600);
        }, 2600);
      },
    },

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
            <div class="dp-card__item dp-card__item--status" data-tone="${st.tone}" role="status">
              <span class="dp-card__label">Status</span>
              <span class="dp-card__value dp-card__status"><span class="dp-card__dot"></span>${esc(st.text)}</span>
            </div>
          </div>
          <button class="dp-row dp-row--slate" data-act="go:settings" data-fid="home:settings" data-dp-focusable>
            <span class="dp-row__body"><span class="dp-row__label">Settings</span></span>${chevron}
          </button>
        </div>
        <div class="scr-home__rule"></div>
        <div class="scr-home__right">
          <div class="dp-wordmark">
            <i data-dp-mark class="dp-wordmark__mark"></i>
            <div class="dp-wordmark__lockup">
              <div class="dp-wordmark__name">OpenPlay</div>
              <div class="dp-wordmark__fork">(a fork of DiPlay)</div>
            </div>
            <div class="dp-wordmark__tagline">CarPlay, that you own.</div>
          </div>
          <div class="scr-home__cta">
            <button class="dp-btn dp-btn--primary dp-btn--lg" data-act="${s.connected ? "toast:Opening CarPlay" : "go:connecting"}" data-fid="home:connect" data-dp-focusable data-autofocus>
              ${s.connected ? "Open CarPlay" : "Connect phone"}
            </button>
          </div>
        </div>`;
      },
    },

    // Connecting happens on the splash: same layout, the mark already drawn, a light
    // running round its outline and the stage in the bottom-left status line.
    connecting: {
      html: (s) => brandStage(s, { filled: true, tone: "connecting", text: STAGES[0], loading: true }),
      mount(el, ctx, p) {
        startLoading(el, ctx, p);
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
            <button class="dp-btn dp-btn--ghost dp-btn--lg" data-act="home" data-dp-focusable>Back to OpenPlay</button>
          </div>
        </div>`,
      // Home's device card shows the failure in red until the next attempt.
      mount(el, ctx) {
        ctx.state.failed = true;
        ctx.state.connected = false;
      },
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

  let hosts = 0;

  function mount(host, { interactive = true, start = "home" } = {}) {
    // Each frame is its own View Transition group, so only the screen slides, not the page.
    host.style.setProperty("view-transition-name", `dp-hu-${++hosts}`);
    host.style.setProperty("view-transition-class", "dp-hu");
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
      morphHome: () => morphHome(),
      setCategory(cat) {
        state.cat = cat;
        const pane = current.querySelector(".scr-pane");
        current.querySelectorAll(".dp-rail__item").forEach((b) => b.dataset.cat === cat ? b.setAttribute("aria-current", "page") : b.removeAttribute("aria-current"));
        pane.scrollTop = 0;
        pane.innerHTML = renderPane(cat, state);
        DP.init(pane);
      },
    };

    // `keep` stays on screen above the new one (for the splash morph); `enter: false`
    // skips the push animation because the caller animates the screen itself.
    //
    // With the View Transitions API the browser snapshots the old screen and slides it out
    // while the new one slides in (CSS in screens.css). The DOM swap then happens in the
    // transition's callback, so render() returns a promise that settles once it has run.
    function render(name, params, { back = false, restore = null, keep = null, enter = true, vt = true } = {}) {
      timers.forEach(clearTimeout);
      timers = [];
      const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
      const useVT = vt && enter && !keep && !reduce && DP.libs.viewTransitions && host.offsetParent !== null && !!current;
      const swap = () => {
        host.querySelectorAll(":scope > .scr").forEach((el) => el !== keep && el.remove());
        const el = document.createElement("div");
        const anim = !enter ? " is-morphing" : useVT ? "" : " dp-screen";
        el.className = `scr scr-${name}${anim}${back ? " is-back" : ""}`;
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
      };
      if (!useVT) {
        swap();
        return Promise.resolve();
      }
      document.documentElement.dataset.dpVt = back ? "back" : "push";
      return document.startViewTransition(swap).updateCallbackDone;
    }

    /*
     * Splash -> Home as one motion (FLIP). Home is rendered under the splash, then the
     * mark, name and tagline on Home start at their splash positions and sizes and glide
     * to their own; the splash is removed in the same frame, so nothing jumps.
     * The rest of Home fades up behind them.
     */
    const MORPH = { duration: 820, easing: "cubic-bezier(0.32, 0.72, 0, 1)" };

    function morphHome() {
      const from = current;
      stack.length = 0;
      stack.push({ name: "home", params: {} });
      const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (reduce || !from?.querySelector(".dp-splash")) return render("home", {});
      if (DP.libs.gsap) return morphHomeGsap(from);

      render("home", {}, { keep: from, enter: false });
      const to = current;
      // Rects are in screen pixels; transforms inside the scaled frame are not.
      const k = host.getBoundingClientRect().width / host.offsetWidth || 1;
      const pairs = [
        [".dp-splash__fill", ".dp-wordmark__mark"],
        [".dp-wordmark__name", ".dp-wordmark__name"],
        [".dp-wordmark__fork", ".dp-wordmark__fork"],
        [".dp-wordmark__tagline", ".dp-wordmark__tagline"],
      ];
      for (const [a, b] of pairs) {
        const src = from.querySelector(a);
        const dst = to.querySelector(b);
        if (!src || !dst) continue;
        const r1 = src.getBoundingClientRect();
        const r2 = dst.getBoundingClientRect();
        const dx = (r1.left + r1.width / 2 - (r2.left + r2.width / 2)) / k;
        const dy = (r1.top + r1.height / 2 - (r2.top + r2.height / 2)) / k;
        const s = r1.width / r2.width;
        const color = getComputedStyle(src).color;
        dst.animate(
          [
            { transform: `translate(${dx}px, ${dy}px) scale(${s})`, color, filter: "drop-shadow(0 0 0 transparent)" },
            { transform: "none" },
          ],
          { ...MORPH, fill: "backwards" },
        );
      }
      retire(from);

      // Everything that is new on Home arrives after the shared elements start moving.
      const arriving = [...to.querySelectorAll(".scr-home__left > *, .scr-home__rule, .scr-home__cta > *")];
      arriving.forEach((el, i) =>
        el.animate(
          [{ opacity: 0, transform: "translateY(14px)" }, { opacity: 1, transform: "none" }],
          { duration: 560, delay: 300 + i * 70, easing: "cubic-bezier(0.22, 1, 0.36, 1)", fill: "backwards" },
        ),
      );
      try {
        to.querySelector(".scr-home__right").animate([{ opacity: 0 }, { opacity: 0.45 }], { duration: 900, delay: 200, fill: "backwards", pseudoElement: "::before" });
      } catch {
        // pseudoElement animation is not supported everywhere; the glow then just appears.
      }
      setTimeout(() => to.classList.remove("is-morphing"), MORPH.duration + 600);
    }

    /*
     * The same morph on GSAP. Flip moves the three text parts: it matches them across the
     * two screens by data-flip-id and handles the scaled head-unit frame itself. The mark
     * starts as an SVG <path> on the splash, which Flip cannot measure, so it gets the
     * measured transform from above as a GSAP tween. Returns the timeline (kept on
     * app.morph for the self-test).
     */
    function morphHomeGsap(from) {
      const gsap = DP.libs.gsap;
      const Flip = window.Flip;
      const text = [".dp-wordmark__name", ".dp-wordmark__fork", ".dp-wordmark__tagline"];
      text.forEach((sel, i) => (from.querySelector(sel).dataset.flipId = `wm-${i}`));
      const flipState = Flip.getState(from.querySelectorAll("[data-flip-id]"));
      const markFrom = from.querySelector(".dp-splash__fill").getBoundingClientRect();

      render("home", {}, { keep: from, enter: false, vt: false });
      const to = current;
      text.forEach((sel, i) => (to.querySelector(sel).dataset.flipId = `wm-${i}`));
      const mark = to.querySelector(".dp-wordmark__mark");
      const markTo = mark.getBoundingClientRect();
      const k = host.getBoundingClientRect().width / host.offsetWidth || 1;
      retire(from);

      const tl = gsap.timeline({
        onComplete: () => {
          to.classList.remove("is-morphing");
          gsap.set([mark, ...to.querySelectorAll("[data-flip-id]")], { clearProps: "transform,filter" });
        },
      });
      tl.add(Flip.from(flipState, { targets: to.querySelectorAll("[data-flip-id]"), duration: 0.9, ease: "expo.out", scale: true }), 0);
      tl.fromTo(
        mark,
        {
          x: (markFrom.left + markFrom.width / 2 - (markTo.left + markTo.width / 2)) / k,
          y: (markFrom.top + markFrom.height / 2 - (markTo.top + markTo.height / 2)) / k,
          scale: markFrom.width / markTo.width,
          filter: "drop-shadow(0px 8px 28px rgba(61, 255, 110, 0))",
        },
        { x: 0, y: 0, scale: 1, filter: "drop-shadow(0px 8px 28px rgba(61, 255, 110, 0.28))", duration: 0.9, ease: "expo.out" },
        0,
      );
      const arriving = to.querySelectorAll(".scr-home__left > *, .scr-home__rule, .scr-home__cta > *");
      tl.from(arriving, { opacity: 0, y: 14, duration: 0.6, stagger: 0.07, ease: "expo.out" }, 0.3);
      tl.fromTo(to.querySelector(".scr-home__right"), { "--glow-o": 0 }, { "--glow-o": 0.45, duration: 0.9, ease: "power1.out" }, 0.2);
      api.morph = tl;
      return tl;
    }

    // The screen being left: its shared parts hide at once (their twins are flying), the
    // rest fades out over 300ms instead of vanishing, then it is removed.
    const SHARED = ".dp-splash__svg, .dp-wordmark__mark, .dp-wordmark__lockup > *, .dp-wordmark__tagline";
    function retire(el) {
      el.classList.add("is-retiring");
      delete el.dataset.dpFocusScope;
      el.querySelectorAll(SHARED).forEach((s) => (s.style.visibility = "hidden"));
      const done = () => el.remove();
      if (matchMedia("(prefers-reduced-motion: reduce)").matches) return done();
      el.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 300, easing: "ease-out", fill: "forwards" }).finished.then(done, done);
    }

    /*
     * Home -> loading screen: the reverse of the splash morph. The wordmark on the loading
     * screen starts where it sits on Home and glides to the centre; the mark grows from its
     * Home size. The device card and button fade with Home. Needs GSAP (Flip); without it
     * the loading screen just slides in.
     */
    function morphToLoading(params) {
      const from = current;
      const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (stack.length) stack[stack.length - 1].focus = DP.focus.current?.dataset?.fid;
      stack.push({ name: "connecting", params });
      if (reduce || !DP.libs.gsap || !from?.matches(".scr-home")) return render("connecting", params);

      const gsap = DP.libs.gsap;
      const Flip = window.Flip;
      const text = [".dp-wordmark__name", ".dp-wordmark__fork", ".dp-wordmark__tagline"];
      text.forEach((sel, i) => (from.querySelector(sel).dataset.flipId = `wm-${i}`));
      const flipState = Flip.getState(from.querySelectorAll("[data-flip-id]"));
      const markFrom = from.querySelector(".dp-wordmark__mark").getBoundingClientRect();

      render("connecting", params, { keep: from, enter: false, vt: false });
      const to = current;
      text.forEach((sel, i) => (to.querySelector(sel).dataset.flipId = `wm-${i}`));
      const svg = to.querySelector(".dp-splash__svg");
      // The splash SVG is padded evenly round the mark, so its centre is the mark's centre.
      const markTo = to.querySelector(".dp-splash__fill").getBoundingClientRect();
      const k = host.getBoundingClientRect().width / host.offsetWidth || 1;
      retire(from);

      const tl = gsap.timeline({
        onComplete: () => {
          to.classList.remove("is-morphing");
          gsap.set([svg, ...to.querySelectorAll("[data-flip-id]")], { clearProps: "transform" });
        },
      });
      tl.add(Flip.from(flipState, { targets: to.querySelectorAll("[data-flip-id]"), duration: 0.85, ease: "expo.out", scale: true }), 0);
      tl.fromTo(
        svg,
        {
          x: (markFrom.left + markFrom.width / 2 - (markTo.left + markTo.width / 2)) / k,
          y: (markFrom.top + markFrom.height / 2 - (markTo.top + markTo.height / 2)) / k,
          scale: markFrom.width / markTo.width,
        },
        { x: 0, y: 0, scale: 1, duration: 0.85, ease: "expo.out" },
        0,
      );
      tl.from(to.querySelectorAll(".scr-corner:not([hidden])"), { opacity: 0, y: 12, duration: 0.5, stagger: 0.08, ease: "expo.out" }, 0.35);
      api.morph = tl;
      return tl;
    }

    function go(name, params = {}) {
      if (stack.length) stack[stack.length - 1].focus = DP.focus.current?.dataset?.fid;
      stack.push({ name, params });
      return render(name, params);
    }

    function back() {
      // Leaving the loading screen glides the wordmark back to Home.
      if (current?.matches(".scr-connecting")) return Promise.resolve(morphHome());
      if (stack.length <= 1) return Promise.resolve();
      stack.pop();
      const top = stack[stack.length - 1];
      return render(top.name, top.params, { back: true, restore: top.focus });
    }

    async function choose(key, value) {
      const changed = state[key] !== value;
      state[key] = value;
      if (changed && PICKERS[key]?.reconnect && state.connected) state.reconnectNeeded = true;
      await back();
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
        case "go":
          if (arg === "connecting") return morphToLoading({ fail: host.dataset.fail === "1" });
          return go(arg);
        case "cancel-connect": return morphHome();
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
            body: "OpenPlay forgets the CarPlay Wi-Fi group and connects again. CarPlay stops for a few seconds.",
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

    const api = {
      morph: null, // the last splash -> Home GSAP timeline
      go: (name) => {
        if (!SCREENS[name]) return Promise.resolve();
        stack.length = 0;
        if (name !== "home" && name !== "splash") stack.push({ name: "home", params: {} });
        if (name === "failed") state.connected = false;
        stack.push({ name, params: {} });
        return render(name, {});
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
    return api;
  }

  window.DPPrototype = { mount, CATEGORIES };
})();
