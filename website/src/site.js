import { useEffect } from "react";

/** Sets the browser tab's title for the current page. */
export function useTitle(title) {
  useEffect(() => { document.title = title; }, [title]);
}

// Links and labels used across the site.
export const GITHUB = "https://github.com/j1z4/DiPlay";
export const GITHUB_REPO = "j1z4/DiPlay";
export const UPSTREAM = "https://github.com/shihabal3amri/DiPlay";
export const ISSUES = `${GITHUB}/issues`;
export const BASE_VERSION = "0.2.21";

export const NAV = [
  { label: "Home", to: "/" },
  { label: "Documentation", to: "/docs" },
  { label: "About", to: "/about" },
];
