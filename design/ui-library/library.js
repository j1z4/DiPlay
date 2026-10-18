/* The library site: navigation, page routing, code snippets and demos. */
(() => {
  "use strict";

  const pages = [...document.querySelectorAll(".lib-page")];
  const nav = document.getElementById("nav");

  /* ---------- Code snippets (captured before icons are hydrated) ---------- */

  function dedent(html) {
    const lines = html.replace(/^\s*\n/, "").replace(/\s+$/, "").split("\n");
    const indent = Math.min(...lines.filter((l) => l.trim()).map((l) => l.match(/^\s*/)[0].length));
    return lines.map((l) => l.slice(indent)).join("\n").replace(/ (is-focused|is-pressed)/g, "");
  }

  document.querySelectorAll(".lib-stage[data-code]").forEach((stage) => {
    const code = dedent(stage.innerHTML);
    const details = document.createElement("details");
    details.className = "lib-code";
    details.innerHTML = '<summary><span>HTML</span></summary><pre><button class="lib-copy" type="button">Copy</button><code></code></pre>';
    details.querySelector("code").textContent = code;
    details.querySelector(".lib-copy").addEventListener("click", async (e) => {
      try {
        await navigator.clipboard.writeText(code);
        e.target.textContent = "Copied";
      } catch {
        e.target.textContent = "Select and copy";
      }
      setTimeout(() => (e.target.textContent = "Copy"), 1400);
    });
    stage.after(details);
  });

  /* ---------- Navigation ---------- */

  let group = null;
  pages.forEach((p, i) => {
    if (p.dataset.group !== group) {
      group = p.dataset.group;
      const h = document.createElement("div");
      h.className = "lib-nav__group";
      h.textContent = group;
      nav.append(h);
    }
    const a = document.createElement("a");
    a.href = `#${p.dataset.page}`;
    a.innerHTML = `<span class="lib-nav__num">${String(i).padStart(2, "0")}</span>`;
    a.append(p.dataset.title);
    nav.append(a);

    const pager = document.createElement("nav");
    pager.className = "lib-pager";
    const prev = pages[i - 1];
    const next = pages[i + 1];
    if (prev) pager.innerHTML += `<a href="#${prev.dataset.page}"><small>← Previous</small>${prev.dataset.title}</a>`;
    if (next) pager.innerHTML += `<a href="#${next.dataset.page}" data-next><small>Next →</small>${next.dataset.title}</a>`;
    p.append(pager);
  });

  /* ---------- Prototype ---------- */

  const protoHost = document.getElementById("proto");
  const app = DPPrototype.mount(protoHost, { interactive: true });

  const heroHost = document.getElementById("hero-screen");
  heroHost.style.pointerEvents = "none";
  DPPrototype.mount(heroHost, { interactive: false, start: location.hash.length > 1 ? "home" : "splash" });

  document.getElementById("logo-replay").addEventListener("click", () => DP.replaySplash(document.getElementById("logo-splash")));
  let walkthroughSeen = false;

  document.getElementById("proto-bar").addEventListener("click", (e) => {
    const go = e.target.closest("[data-go]");
    if (go) {
      app.go(go.dataset.go);
      app.activate();
    }
    const theme = e.target.closest("[data-theme]");
    if (theme) {
      protoHost.closest(".hu").dataset.dpTheme = theme.dataset.theme;
      document.querySelectorAll("#proto-bar [data-theme]").forEach((b) => b.setAttribute("aria-pressed", String(b === theme)));
    }
  });

  /* ---------- Routing ---------- */

  function route() {
    // #walkthrough/settings opens the walkthrough on that screen.
    const [id = "start", screen] = location.hash.slice(1).split("/").map((s) => s || undefined);
    const page = pages.find((p) => p.dataset.page === id) || pages[0];
    pages.forEach((p) => (p.hidden = p !== page));
    nav.querySelectorAll("a").forEach((a) => a.toggleAttribute("aria-current", a.getAttribute("href") === `#${page.dataset.page}`));
    nav.querySelectorAll("a[aria-current]").forEach((a) => a.setAttribute("aria-current", "page"));
    document.title = `${page.dataset.title.replace("&amp;", "&")} · OpenPlay UI Library`;
    document.getElementById("lib").classList.remove("is-menu-open");
    window.scrollTo({ top: 0, behavior: "instant" });
    DP.init(page); // segmented thumbs need the page visible to measure
    if (page.dataset.page === "walkthrough") {
      // The first visit boots like the car: splash, then Home.
      if (screen) app.go(screen);
      else if (!walkthroughSeen) app.go("splash");
      walkthroughSeen = true;
      app.activate();
    } else {
      app.deactivate();
    }
    if (page.dataset.page === "logo") DP.replaySplash(document.getElementById("logo-splash"));
  }
  addEventListener("hashchange", route);

  document.getElementById("menu").addEventListener("click", () => document.getElementById("lib").classList.toggle("is-menu-open"));

  /* ---------- Color swatches ---------- */

  const css = getComputedStyle(document.documentElement);
  const swatch = (token, name, note) => `
    <div class="lib-swatch">
      <div class="lib-swatch__chip" style="background:var(${token})"></div>
      <div><div class="lib-swatch__name">${name}</div>
      <div class="lib-swatch__meta">${token}<br>${css.getPropertyValue(token).trim()}${note ? ` · ${note}` : ""}</div></div>
    </div>`;
  document.getElementById("sw-brand").innerHTML = [
    swatch("--dp-brand", "Signal", "mark top, glows"),
    swatch("--dp-brand-strong", "Accent", "on, connected, focus ring"),
    swatch("--dp-brand-deep", "Deep", "pressed, focused switch"),
    swatch("--dp-brand-ink", "Ink", "mark bottom"),
  ].join("");
  document.getElementById("sw-surface").innerHTML = [
    swatch("--dp-bg", "Canvas", "true black"),
    swatch("--dp-bg-raised", "Raised", "dialogs"),
    swatch("--dp-surface", "Row"),
    swatch("--dp-surface-2", "Row hover"),
    swatch("--dp-card", "Slate card", "Figma"),
  ].join("");
  document.getElementById("sw-text").innerHTML = [
    swatch("--dp-text", "Primary"),
    swatch("--dp-text-label", "Label", "Figma lavender"),
    swatch("--dp-text-secondary", "Value"),
    swatch("--dp-text-section", "Section header"),
    swatch("--dp-text-tertiary", "Footer"),
  ].join("");
  document.getElementById("sw-state").innerHTML = [
    swatch("--dp-focus-fill", "Focus fill"),
    swatch("--dp-focus-ring", "Focus ring", "app focusRing"),
    swatch("--dp-success", "Connected"),
    swatch("--dp-warning", "Attention"),
    swatch("--dp-danger", "Destructive"),
  ].join("");

  /* ---------- Motion lanes ---------- */

  const lanes = [
    ["Spring · focus", "--dp-dur-base", "--dp-ease-spring"],
    ["Out · push, swap", "--dp-dur-slow", "--dp-ease-out"],
    ["In-out · exits", "--dp-dur-base", "--dp-ease-in-out"],
    ["Fast · color", "--dp-dur-fast", "--dp-ease-out"],
  ];
  document.getElementById("motion-lanes").innerHTML = lanes
    .map(
      ([name, dur, ease]) => `<div>
        <div class="lib-motion__lane" style="--d:var(${dur});--e:var(${ease})"><span class="lib-motion__dot" style="transition:left var(--d) var(--e)"></span></div>
        <div class="lib-motion__meta">${name}<br>${css.getPropertyValue(dur).trim()} · ${css.getPropertyValue(ease).trim()}</div></div>`,
    )
    .join("");
  document.getElementById("motion-lanes").addEventListener("click", (e) => e.target.closest(".lib-motion__lane")?.classList.toggle("is-on"));
  let tick = 0;
  setInterval(() => {
    if (document.getElementById("motion-lanes").offsetParent && tick++ % 2 === 0)
      document.querySelectorAll(".lib-motion__lane").forEach((l) => l.classList.toggle("is-on"));
  }, 900);

  /* ---------- Icons ---------- */

  document.getElementById("icon-grid").innerHTML = Object.keys(window.DP_ICONS)
    .map((n) => `<div class="lib-icon"><i data-dp-icon="${n}"></i><span>${n}</span></div>`)
    .join("");

  /* ---------- Steps demo ---------- */

  let step = 1;
  document.getElementById("steps-next").addEventListener("click", () => {
    step = (step + 1) % 5;
    document.querySelectorAll("#steps-demo .dp-steps__dot").forEach((d, i) => {
      d.classList.toggle("is-done", i < step);
      d.classList.toggle("is-current", i === step);
    });
    document.getElementById("steps-demo").setAttribute("aria-label", `Step ${step + 1} of 5`);
  });

  DP.init();
  route();
})();
