# OpenPlay website

Home, Documentation and About for OpenPlay, with a link to the GitHub repository.
Built with React, [Chakra UI](https://v2.chakra-ui.com/) 2, [Framer Motion](https://motion.dev/) and [GSAP](https://gsap.com/).
The layout loosely follows soliditylang.org; the theme is OpenPlay's own (`design/ui-library/tokens.css`).

```sh
cd website
npm install
npm run dev      # http://localhost:5173
npm run build    # static site in website/dist
npm run preview  # serve the build
```

The build uses a relative base and hash routes (`#/docs/install`), so `dist/` runs from any folder,
including GitHub Pages. The existing `site/` folder and its Pages workflow are unchanged.

## Where things live

| Path | What |
| --- | --- |
| `src/theme.js` | Chakra theme: colours, Outfit and JetBrains Mono, buttons, light and dark mode |
| `src/pages/` | `Home.jsx`, `Docs.jsx`, `About.jsx` |
| `src/content/docs.js` | Documentation pages, written from the repository's `docs/` folder |
| `src/components/Mark.jsx` | The OpenPlay mark; GSAP traces it as on the app's splash |
| `public/screens/` | Screenshots of the app |

## Motion

- GSAP: the mark trace (DrawSVG), the "How it works" rail (ScrollTrigger) and the About lineage.
- Framer Motion: page transitions, scroll reveals, the nav pill, the status line crossfade and the screenshot tilt.
- Both respect `prefers-reduced-motion`.
