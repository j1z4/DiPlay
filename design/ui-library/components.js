/*
 * DiPlay component behavior. No dependencies; exposes `window.DP`.
 *
 * Markup carries state in ARIA attributes (aria-checked, aria-current), so the
 * same HTML works for touch, mouse, keyboard and the D-pad focus engine below.
 */
(() => {
  "use strict";

  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)");

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
      this.current?.classList.remove("is-focused");
      this.current = el;
      if (!el) return;
      el.classList.add("is-focused");
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
    setTimeout(() => el.classList.remove("is-pressed"), 120);
    el.click();
  }

  /* ---------- Wiring ---------- */

  // <i data-dp-icon="name"> becomes the app's vector icon from icons.js.
  function hydrateIcons(root) {
    root.querySelectorAll("i[data-dp-icon]").forEach((i) => {
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

  function init(root = document) {
    hydrateIcons(root);
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
      if (range && (dir === "left" || dir === "right")) {
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

  window.DP = { init, toggle, select, setValue, toast, dialog, focus, press, icon };
})();
