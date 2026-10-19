import { extendTheme } from "@chakra-ui/react";

/*
 * The OpenPlay theme, from design/ui-library/tokens.css: signal green fading into black,
 * graphite surfaces, Outfit for type. Green is the only hue; it means "on" and "you are here".
 * Dark is the default, as in the app; light keeps the same structure on paper white.
 */
const theme = extendTheme({
  config: { initialColorMode: "dark", useSystemColorMode: false },
  fonts: {
    heading: `"Outfit Variable", ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif`,
    body: `"Outfit Variable", ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif`,
    mono: `"JetBrains Mono", ui-monospace, "SF Mono", Menlo, Consolas, monospace`,
  },
  colors: {
    brand: {
      50: "#eafff0",
      100: "#c6ffd5",
      200: "#94ffb0",
      300: "#3dff6e",
      400: "#2fe46a",
      500: "#1fc457",
      600: "#0e9e3e",
      700: "#0b7a31",
      800: "#085623",
      900: "#021a0c",
    },
    graphite: {
      50: "#f5f7fa",
      100: "#e6e9ef",
      200: "#cfd4dd",
      300: "#8d95a5",
      400: "#6f7787",
      500: "#5f6676",
      600: "#343944",
      700: "#282c35",
      800: "#1d2027",
      900: "#14161b",
      950: "#0c0e12",
    },
  },
  semanticTokens: {
    colors: {
      bg: { default: "#f6f7f9", _dark: "#000000" },
      bgRaised: { default: "#ffffff", _dark: "#0c0e12" },
      surface: { default: "#ffffff", _dark: "#14161b" },
      surface2: { default: "#eef0f4", _dark: "#1d2027" },
      outline: { default: "rgba(11,13,17,0.09)", _dark: "rgba(255,255,255,0.07)" },
      text: { default: "#0b0d11", _dark: "#f5f7fa" },
      muted: { default: "#4d5564", _dark: "#8d95a5" },
      subtle: { default: "#6f7787", _dark: "#5f6676" },
      label: { default: "#3a4a7a", _dark: "#cfdcff" },
      accent: { default: "#0e9e3e", _dark: "#2fe46a" },
      accentSoft: { default: "rgba(14,158,62,0.10)", _dark: "rgba(47,228,106,0.12)" },
      warning: { default: "#a3620a", _dark: "#ffbd5c" },
    },
  },
  radii: { card: "28px", control: "16px" },
  styles: {
    global: {
      "html, body": { bg: "bg", color: "text" },
      body: { WebkitFontSmoothing: "antialiased", textRendering: "optimizeLegibility" },
      "::selection": { bg: "brand.400", color: "brand.900" },
    },
  },
  components: {
    Button: {
      baseStyle: { fontWeight: 600, borderRadius: "control", letterSpacing: "-0.01em" },
      sizes: { lg: { h: "56px", px: 7, fontSize: "lg" } },
      variants: {
        // The app's primary button: white on black, black on white.
        primary: {
          bg: "text",
          color: "bg",
          _hover: { transform: "translateY(-1px)", boxShadow: "0 12px 32px -12px rgba(47,228,106,0.55)" },
          _active: { transform: "scale(0.98)" },
          transition: "all 160ms cubic-bezier(.2,.8,.2,1)",
        },
        ghostLine: {
          border: "1px solid",
          borderColor: "outline",
          bg: "surface",
          color: "text",
          _hover: { bg: "surface2", transform: "translateY(-1px)" },
          _active: { transform: "scale(0.98)" },
          transition: "all 160ms cubic-bezier(.2,.8,.2,1)",
        },
      },
    },
    Link: { baseStyle: { _hover: { textDecoration: "none" } } },
  },
});

export default theme;
