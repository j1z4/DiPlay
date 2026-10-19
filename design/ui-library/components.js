/*
 * DiPlay component behavior. No dependencies; exposes `window.DP`.
 *
 * Markup carries state in ARIA attributes (aria-checked, aria-current), so the
 * same HTML works for touch, mouse, keyboard and the D-pad focus engine below.
 */
(() => {
  "use strict";

  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)");

  /*
   * Optional libraries, loaded from the CDN in index.html. Each one has a plain fallback,
   * so the page still works offline or with ?libs=off (used by the before/after page):
   *   GSAP + Flip + DrawSVG  the splash trace and the splash -> Home morph
   *   Motion                 spring physics for focus and press
   *   View Transitions       screen push / pop (browser API, used in prototype.js)
   *   Phosphor               one consistent icon family
   */
  const libsOff = new URLSearchParams(location.search).get("libs") === "off";
  const libs = {
    off: libsOff,
    gsap: !libsOff && window.gsap && window.Flip && window.DrawSVGPlugin ? window.gsap : null,
    motion: !libsOff && window.Motion?.animate ? window.Motion : null,
    phosphor: !libsOff && !!document.querySelector("link[data-phosphor]"),
    viewTransitions: !libsOff && typeof document.startViewTransition === "function",
  };
  if (libs.gsap) libs.gsap.registerPlugin(window.Flip, window.DrawSVGPlugin);
  document.documentElement.classList.toggle("dp-libs", !libsOff);

  const icon = (name) => {
    const paths = {
      chevron: '<path d="M9 5l7 7-7 7"/>',
      check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
      back: '<path d="M15 5l-7 7 7 7"/>',
    };
    return `<svg class="dp-icon" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || ""}</svg>`;
  };

  /* ---------- Switch ---------- */

  function toggle(el, force) {
    const on = force ?? el.getAttribute("aria-checked") !== "true";
    el.setAttribute("aria-checked", String(on));
    el.dispatchEvent(new CustomEvent("dp-change", { bubbles: true, detail: { value: on } }));
    return on;
  }

  /* ---------- Segmented ---------- */

  function placeThumb(group) {
    const thumb = group.querySelector(".dp-segmented__thumb");
    const current = group.querySelector('[aria-checked="true"]');
    if (!thumb || !current || !current.offsetWidth) return;
    // Snap on first layout; glide only on later changes.
    const first = !thumb.dataset.placed;
    if (first) thumb.style.transition = "none";
    thumb.style.width = `${current.offsetWidth}px`;
    thumb.style.transform = `translateX(${current.offsetLeft - 4}px)`;
    if (first) {
      void thumb.offsetWidth;
      thumb.style.transition = "";
      thumb.dataset.placed = "1";
    }
  }

  function select(option) {
    const group = option.closest(".dp-segmented");
    group.querySelectorAll(".dp-segmented__option").forEach((o) => o.setAttribute("aria-checked", String(o === option)));
    placeThumb(group);
    group.dispatchEvent(new CustomEvent("dp-change", { bubbles: true, detail: { value: option.dataset.value ?? option.textContent } }));
  }

  /* ---------- Slider ---------- */

  function paintSlider(input) {
    const pct = ((input.value - input.min) / (input.max - input.min)) * 100;
    input.style.setProperty("--pct", `${pct}%`);
    const out = input.closest(".dp-slider")?.querySelector(".dp-slider__value");
    if (out) out.textContent = (input.dataset.format || "{v}").replace("{v}", input.value);
  }

  /* ---------- Row value ---------- */

  function setValue(row, text) {
    const value = row.querySelector(".dp-row__value");
    if (!value || value.textContent === text) return;
    value.textContent = text;
    value.classList.remove("is-changing");
    void value.offsetWidth; // restart the animation
    value.classList.add("is-changing");
  }

  /* ---------- Toast ---------- */

  function toast(text, { host = document.body, ms = 2200 } = {}) {
    let stack = host.querySelector(":scope > .dp-toast-stack");
    if (!stack) {
      stack = document.createElement("div");
      stack.className = "dp-toast-stack";
      host.append(stack);
    }
    const el = document.createElement("div");
    el.className = "dp-toast";
    el.setAttribute("role", "status");
    el.innerHTML = `<span class="dp-toast__icon">${icon("check")}</span><span></span>`;
    el.lastChild.textContent = text;
    stack.append(el);
    setTimeout(() => {
      el.classList.add("is-leaving");
      el.addEventListener("animationend", () => el.remove(), { once: true });
      setTimeout(() => el.remove(), reduceMotion.matches ? 0 : 300);
    }, ms);
  }

  /* ---------- Dialog ---------- */

  /**
   * dialog({ title, body, actions: [{ label, kind, value }] }) -> Promise<value | null>
   * Escape or a tap on the scrim resolves null. The last action gets first focus,
   * so the safe choice should go first and the confirming one last.
   */
  function dialog({ title, body, actions = [{ label: "OK", kind: "primary", value: true }], host = document.body }) {
    return new Promise((resolve) => {
      const scrim = document.createElement("div");
      scrim.className = "dp-scrim";
      scrim.innerHTML = `
        <div class="dp-dialog" role="dialog" aria-modal="true" data-dp-focus-scope>
          <h2 class="dp-dialog__title"></h2>
          <p class="dp-dialog__body"></p>
          <div class="dp-dialog__actions"></div>
        </div>`;
      scrim.querySelector(".dp-dialog__title").textContent = title;
      scrim.querySelector(".dp-dialog__body").textContent = body;
      const row = scrim.querySelector(".dp-dialog__actions");
      for (const a of actions) {
        const b = document.createElement("button");
        b.className = `dp-btn dp-btn--block ${a.kind ? `dp-btn--${a.kind}` : ""}`;
        b.dataset.dpFocusable = "";
        b.textContent = a.label;
        b.addEventListener("click", () => close(a.value ?? a.label));
        row.append(b);
      }
      const previous = focus.current;
      let closed = false;
      const close = (value) => {
        if (closed) return;
        closed = true;
        scrim.classList.add("is-leaving");
        let finished = false;
        const done = () => {
          if (finished) return;
          finished = true;
          scrim.remove();
          focus.pop(previous);
          resolve(value);
        };
        if (reduceMotion.matches) return done();
        scrim.addEventListener("animationend", done, { once: true });
        setTimeout(done, 260); // animationend never fires in background tabs
      };
      scrim.addEventListener("click", (e) => e.target === scrim && close(null));
      scrim._dpCancel = () => close(null);
      host.append(scrim);
      const dlg = scrim.querySelector(".dp-dialog");
      focus.push(dlg, row.lastElementChild);
    });
  }

  /* ---------- D-pad focus engine ---------- */

  /*
   * Arrow keys move focus to the nearest focusable in that direction inside the
   * active scope, the way a rotary knob or remote moves on a head unit.
   * Enter or Space presses; Escape or Backspace goes back.
   */
  const focus = {
    scopes: [],
    current: null,

    get scope() {
      return this.scopes[this.scopes.length - 1] || null;
    },

    items(scope = this.scope) {
      if (!scope) return [];
      // Skip items that belong to a nested scope, and hidden or disabled ones.
      return [...scope.querySelectorAll("[data-dp-focusable]")].filter(
        (el) => el.closest("[data-dp-focus-scope]") === scope && el.offsetParent !== null && !el.disabled,
      );
    },

    set(el, { scroll = true } = {}) {
      if (this.current === el) return;
      if (this.current) {
        this.current.classList.remove("is-focused");
        spring(this.current, 1, SPRING.rest);
      }
      this.current = el;
      if (!el) return;
      el.classList.add("is-focused");
      spring(el, focusScale(el), SPRING.focus);
      el.dispatchEvent(new CustomEvent("dp-focus", { bubbles: true }));
      if (scroll) reveal(el);
    },

    push(scope, first) {
      this.scopes.push(scope);
      this.set(first || this.items(scope)[0] || null);
    },

    pop(restore) {
      this.scopes.pop();
      this.set(restore && document.contains(restore) ? restore : this.items()[0] || null);
    },

    reset(scope, first) {
      this.scopes = [scope];
      this.current?.classList.remove("is-focused");
      this.current = null;
      this.set(first || this.items(scope)[0] || null, { scroll: false });
    },

    move(dir) {
      const items = this.items();
      if (!items.length) return;
      if (!this.current || !items.includes(this.current)) return this.set(items[0]);
      const from = this.current.getBoundingClientRect();
      const fx = from.left + from.width / 2;
      const fy = from.top + from.height / 2;
      let best = null;
      let bestScore = Infinity;
      for (const el of items) {
        if (el === this.current) continue;
        const r = el.getBoundingClientRect();
        const x = r.left + r.width / 2;
        const y = r.top + r.height / 2;
        const dx = x - fx;
        const dy = y - fy;
        const along = { up: -dy, down: dy, left: -dx, right: dx }[dir];
        if (along <= 1) continue;
        const across = dir === "up" || dir === "down" ? Math.abs(dx) : Math.abs(dy);
        const score = along + across * 2.5;
        if (score < bestScore) {
          bestScore = score;
          best = el;
        }
      }
      if (best) this.set(best);
      else nudge(this.current, dir);
    },
  };

  // Scrolls only the nearest [data-dp-scroll] pane, never the page around it.
  // Works inside a scaled frame: rects are in screen pixels, scrollTop is not.
  function reveal(el, pad = 32) {
    const pane = el.closest("[data-dp-scroll]");
    if (!pane) return;
    const p = pane.getBoundingClientRect();
    const r = el.getBoundingClientRect();
    const k = pane.clientHeight / p.height || 1;
    const top = (r.top - p.top) * k;
    const bottom = (r.bottom - p.top) * k;
    const behavior = reduceMotion.matches ? "auto" : "smooth";
    if (top < pad) pane.scrollBy({ top: top - pad, behavior });
    else if (bottom > pane.clientHeight - pad) pane.scrollBy({ top: bottom - pane.clientHeight + pad, behavior });
  }

  // A small bump tells the driver they hit the edge.
  function nudge(el, dir) {
    if (reduceMotion.matches) return;
    const d = { up: [0, -4], down: [0, 4], left: [-4, 0], right: [4, 0] }[dir];
    el.animate(
      [{ translate: "0 0" }, { translate: `${d[0]}px ${d[1]}px` }, { translate: "0 0" }],
      { duration: 220, easing: "cubic-bezier(0.34, 1.4, 0.64, 1)" },
    );
  }

  function press(el) {
    if (!el) return;
    el.classList.add("is-pressed");
    spring(el, 0.96, SPRING.press);
    setTimeout(() => {
      el.classList.remove("is-pressed");
      spring(el, el.classList.contains("is-focused") ? focusScale(el) : 1, SPRING.focus);
    }, 120);
    el.click();
  }

  // Mouse and touch presses, and keyboard focus outside the D-pad engine, get springs too.
  document.addEventListener("pointerdown", (e) => spring(e.target.closest?.(SPRING_TARGETS), 0.96, SPRING.press));
  const release = (e) => {
    const el = e.target.closest?.(SPRING_TARGETS);
    if (el) spring(el, el.classList.contains("is-focused") || el.matches(":focus-visible") ? focusScale(el) : 1, SPRING.focus);
  };
  document.addEventListener("pointerup", release);
  document.addEventListener("pointercancel", release);
  document.addEventListener("focusin", (e) => e.target.matches?.(":focus-visible") && spring(e.target, focusScale(e.target), SPRING.focus));
  document.addEventListener("focusout", (e) => !e.target.classList?.contains("is-focused") && spring(e.target, 1, SPRING.rest));

  /* ---------- Wiring ---------- */

  // App icon name -> Phosphor (bold). One family, one stroke weight, readable at arm's length.
  const PHOSPHOR = {
    about: "info", advanced: "gear-six", audio: "speaker-high", automation: "lightning",
    back: "arrow-left", car: "car", chevron: "caret-right", connection: "wifi-high",
    controls: "sliders-horizontal", dashboard: "gauge", day: "sun", diagnostics: "file-text",
    display: "monitor", home: "house", language: "globe", navigation: "navigation-arrow",
    night: "moon", overview: "squares-four", permissions: "check-circle", search: "magnifying-glass",
    vehicle: "car-profile",
  };

  // <i data-dp-icon="name"> becomes a Phosphor glyph, or the app's own vector from icons.js
  // when Phosphor is off or the element asks for data-dp-icon-set="app".
  function hydrateIcons(root) {
    root.querySelectorAll("i[data-dp-icon]").forEach((i) => {
      const ph = PHOSPHOR[i.dataset.dpIcon];
      if (libs.phosphor && ph && i.dataset.dpIconSet !== "app") {
        const glyph = document.createElement("i");
        glyph.className = `ph-bold ph-${ph} dp-glyph dp-ph ${i.className}`.trim();
        glyph.setAttribute("aria-hidden", "true");
        i.replaceWith(glyph);
        return;
      }
      const paths = window.DP_ICONS?.[i.dataset.dpIcon];
      if (!paths) return;
      const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.setAttribute("viewBox", "0 0 24 24");
      svg.setAttribute("aria-hidden", "true");
      svg.setAttribute("class", `dp-glyph ${i.className}`.trim());
      svg.innerHTML = paths;
      i.replaceWith(svg);
    });
  }

  /* ---------- Logo mark and splash trace ---------- */

  // Same path as brand/diplay-mark.svg (viewBox 0 0 150 148).
  const MARK_PATH =
    "M58.0615 0C54.0815 0 50.8035 3.28714 50.8035 7.2785V38.1089C50.8035 38.9159 50.406 39.6654 49.7407 40.1181C49.0746 40.5708 48.2344 40.6627 47.4867 40.3641L22.9912 30.186C22.0721 29.8037 21.113 29.6314 20.1702 29.6412C17.3425 29.6704 14.65 31.3633 13.5073 34.1287L0.54667 65.5079C-0.976196 69.1951 0.794 73.4982 4.47372 75.0189L34.1829 87.3013C34.8644 87.5843 35.3763 88.1591 35.5815 88.87C35.7867 89.5804 35.6571 90.3365 35.2316 90.9406L15.7831 115.624C13.3605 118.79 13.9632 123.402 17.1202 125.831L43.9929 146.511C47.1496 148.94 51.7489 148.331 54.1711 145.165L73.0819 121.159C73.5407 120.559 74.2461 120.211 75.0002 120.211C75.7538 120.211 76.4592 120.559 76.9179 121.159L95.8288 145.165C98.2514 148.331 102.849 148.94 106.007 146.511L132.88 125.831C136.037 123.402 136.64 118.791 134.217 115.624L114.768 90.9406C114.343 90.3374 114.214 89.5804 114.418 88.87C114.624 88.1591 115.136 87.5843 115.817 87.3013L145.526 75.0189C149.206 73.4982 150.976 69.1963 149.453 65.5079L136.491 34.1287C135.349 31.3635 132.656 29.6706 129.829 29.6412C128.886 29.6315 127.927 29.8048 127.008 30.186L102.512 40.3641C101.765 40.6631 100.924 40.5708 100.258 40.1181C99.5892 39.6654 99.1917 38.9159 99.1917 38.1089V7.2785C99.1917 3.28714 95.9141 0 91.9341 0H58.0615Z";
  let markId = 0;

  function gradient(id) {
    return `<linearGradient id="${id}" x1="75" y1="0" x2="75" y2="148" gradientUnits="userSpaceOnUse">
      <stop stop-color="#3DFF6E"/><stop offset="0.55" stop-color="#0E9E3E"/><stop offset="1" stop-color="#021A0C"/></linearGradient>`;
  }

  /** The mark as an inline SVG string. `mono` fills with currentColor. */
  function mark({ mono = false, className = "" } = {}) {
    const id = `dp-mark-${++markId}`;
    return `<svg class="dp-mark ${className}" viewBox="0 0 150 148" aria-hidden="true">
      ${mono ? "" : `<defs>${gradient(id)}</defs>`}
      <path fill="${mono ? "currentColor" : `url(#${id})`}" fill-rule="evenodd" d="${MARK_PATH}"/></svg>`;
  }

  /** The splash trace (see brand/diplay-splash-trace.svg); components.css animates it. */
  function splash() {
    const id = `dp-splash-${++markId}`;
    // Real <path>s, not <use>, so GSAP's DrawSVG can measure them. pathLength="1" serves the
    // CSS fallback and is removed when GSAP runs (DrawSVG works in real lengths).
    return `<svg class="dp-splash__svg" viewBox="-15 -15 180 178" aria-hidden="true">
      <defs>${gradient(`${id}-f`)}
        <linearGradient id="${id}-s" x1="75" y1="0" x2="75" y2="148" gradientUnits="userSpaceOnUse"><stop stop-color="#fff"/><stop offset="1" stop-color="#3DFF6E"/></linearGradient>
        <filter id="${id}-b" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="3"/></filter></defs>
      <path class="dp-splash__fill" fill="url(#${id}-f)" fill-rule="evenodd" d="${MARK_PATH}"/>
      <path class="dp-splash__glow" pathLength="1" stroke="#3DFF6E" stroke-width="6" stroke-linejoin="round" filter="url(#${id}-b)" d="${MARK_PATH}"/>
      <path class="dp-splash__trace" pathLength="1" stroke="url(#${id}-s)" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round" d="${MARK_PATH}"/>
      <path class="dp-splash__orbit" pathLength="1" stroke="#3DFF6E" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" filter="url(#${id}-b)" d="${MARK_PATH}"/>
      <path class="dp-splash__orbit" pathLength="1" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" d="${MARK_PATH}"/></svg>`;
  }

  /*
   * Same 2.6 s timeline as brand/diplay-splash-trace.svg. With GSAP the glow trails the
   * stroke by 80 ms like light following a pen, and the fill settles on an expo curve.
   * Returns the GSAP timeline (or null when the CSS fallback runs).
   */
  function replaySplash(el, { filled = false } = {}) {
    const gsap = libs.gsap;
    if (filled) {
      // Entering from Home: the mark is already drawn, so skip the trace.
      el._dpTimeline?.kill();
      el.classList.remove("is-playing", "is-gsap");
      el.classList.add("is-filled");
      return null;
    }
    el.classList.remove("is-filled");
    if (!gsap || reduceMotion.matches) {
      el.classList.remove("is-playing", "is-gsap");
      void el.offsetWidth;
      el.classList.add("is-playing");
      return null;
    }
    el._dpTimeline?.kill();
    el.classList.remove("is-playing");
    el.classList.add("is-gsap");
    const trace = el.querySelector(".dp-splash__trace");
    const glow = el.querySelector(".dp-splash__glow");
    const fill = el.querySelector(".dp-splash__fill");
    const after = el.querySelectorAll(".dp-splash__after");
    [trace, glow].forEach((p) => p.removeAttribute("pathLength"));
    const tl = gsap.timeline();
    tl.set([trace, glow], { opacity: 1 })
      .fromTo(trace, { drawSVG: "0% 0%" }, { drawSVG: "0% 100%", duration: 1.6, ease: "power2.inOut" }, 0)
      .fromTo(glow, { drawSVG: "0% 0%" }, { drawSVG: "0% 100%", duration: 1.6, ease: "power2.inOut" }, 0.08)
      .fromTo(fill, { opacity: 0, scale: 0.96, transformOrigin: "50% 50%" }, { opacity: 1, scale: 1, duration: 0.8, ease: "expo.out" }, 1.25)
      .to([trace, glow], { opacity: 0, duration: 0.8, ease: "power1.in" }, 1.8);
    if (after.length) tl.set(after, { opacity: 1, y: 0 }, 0); // the wordmark is in place from the start
    el._dpTimeline = tl;
    return tl;
  }

  /* ---------- Springs (Motion) ---------- */

  /*
   * With Motion loaded, focus and press use real spring physics instead of a cubic-bezier,
   * so an interrupted move (a fast D-pad sweep) carries its velocity instead of snapping.
   * CSS still draws everything else; html.dp-springs only hands the scale to Motion.
   */
  const SPRING_TARGETS = ".dp-btn, .dp-row:not(.dp-row--info):not(.scr-segment-row), .dp-card__item, .dp-rail__item";
  const SPRING = {
    focus: { type: "spring", stiffness: 420, damping: 24, mass: 0.9 },
    rest: { type: "spring", stiffness: 380, damping: 30 },
    press: { type: "spring", stiffness: 900, damping: 38 },
  };

  function spring(el, scale, opts) {
    if (!libs.motion || reduceMotion.matches || !el?.matches?.(SPRING_TARGETS)) return;
    try {
      libs.motion.animate(el, { scale }, opts);
    } catch {
      // A Motion API change should never break focus; CSS takes over.
    }
  }
  if (libs.motion) document.documentElement.classList.add("dp-springs");
  const focusScale = (el) => (el.matches(".dp-card__item") ? 1.02 : el.matches(".dp-rail__item, .dp-btn, .dp-row") ? 1.035 : 1);

  function hydrateBrand(root) {
    root.querySelectorAll("i[data-dp-mark]").forEach((i) => {
      i.insertAdjacentHTML("afterend", mark({ mono: i.dataset.dpMark === "mono", className: i.className }));
      const style = i.getAttribute("style");
      if (style) i.nextElementSibling.setAttribute("style", style);
      i.remove();
    });
    root.querySelectorAll("[data-dp-splash]:not([data-ready])").forEach((el) => {
      el.dataset.ready = "";
      el.insertAdjacentHTML("afterbegin", splash());
      replaySplash(el, { filled: el.dataset.dpSplash === "filled" });
    });
  }

  function init(root = document) {
    hydrateIcons(root);
    hydrateBrand(root);
    root.querySelectorAll(".dp-segmented").forEach(placeThumb);
    root.querySelectorAll(".dp-slider input[type=range]").forEach(paintSlider);
  }

  document.addEventListener("click", (e) => {
    const sw = e.target.closest('[role="switch"]');
    if (sw && !sw.hasAttribute("data-dp-manual")) toggle(sw);
    const seg = e.target.closest(".dp-segmented__option");
    if (seg) select(seg);
  });

  document.addEventListener("input", (e) => {
    if (e.target.matches(".dp-slider input[type=range]")) paintSlider(e.target);
  });

  // Pointer movement inside a D-pad scope moves focus too, as on Apple TV.
  document.addEventListener("pointerover", (e) => {
    if (e.pointerType !== "mouse") return;
    const el = e.target.closest("[data-dp-focusable]");
    if (el && focus.items().includes(el)) focus.set(el, { scroll: false });
  });

  document.addEventListener("keydown", (e) => {
    if (!focus.scope || !document.contains(focus.scope)) return;
    if (e.target.matches?.("input, textarea, select") && !e.target.matches("[type=range]")) return;
    const dir = { ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right" }[e.key];
    if (dir) {
      // A focused slider takes left/right as value changes.
      const range = focus.current?.querySelector?.("input[type=range]");
      const stepper = focus.current?.matches?.("[data-dp-stepper]");
      if (stepper && (dir === "left" || dir === "right")) {
        // A focused number stepper takes left/right as − / +.
        focus.current.dispatchEvent(new CustomEvent("dp-step", { bubbles: true, detail: dir === "right" ? 1 : -1 }));
      } else if (range && (dir === "left" || dir === "right")) {
        range.value = Number(range.value) + Number(range.step || 1) * (dir === "right" ? 1 : -1);
        paintSlider(range);
        range.dispatchEvent(new Event("input", { bubbles: true }));
      } else {
        focus.move(dir);
      }
      e.preventDefault();
    } else if (e.key === "Enter" || e.key === " ") {
      press(focus.current);
      e.preventDefault();
    } else if (e.key === "Escape" || e.key === "Backspace") {
      const scrim = focus.scope.closest(".dp-scrim");
      if (scrim?._dpCancel) scrim._dpCancel();
      else focus.scope.dispatchEvent(new CustomEvent("dp-back", { bubbles: true }));
      e.preventDefault();
    }
  });

  addEventListener("resize", () => document.querySelectorAll(".dp-segmented").forEach(placeThumb));
  document.fonts?.ready.then(() => init());
  document.addEventListener("DOMContentLoaded", () => init());

  window.DP = { init, toggle, select, setValue, toast, dialog, focus, press, icon, mark, replaySplash, libs };
})();
