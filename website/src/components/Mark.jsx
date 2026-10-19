import { useLayoutEffect, useRef, useId } from "react";
import { Box } from "@chakra-ui/react";
import gsap from "gsap";
import { DrawSVGPlugin } from "gsap/DrawSVGPlugin";

gsap.registerPlugin(DrawSVGPlugin);

// The OpenPlay mark (design/ui-library/brand/diplay-mark.svg), one evenodd path.
export const MARK_PATH =
  "M58.0615 0C54.0815 0 50.8035 3.28714 50.8035 7.2785V38.1089C50.8035 38.9159 50.406 39.6654 49.7407 40.1181C49.0746 40.5708 48.2344 40.6627 47.4867 40.3641L22.9912 30.186C22.0721 29.8037 21.113 29.6314 20.1702 29.6412C17.3425 29.6704 14.65 31.3633 13.5073 34.1287L0.54667 65.5079C-0.976196 69.1951 0.794 73.4982 4.47372 75.0189L34.1829 87.3013C34.8644 87.5843 35.3763 88.1591 35.5815 88.87C35.7867 89.5804 35.6571 90.3365 35.2316 90.9406L15.7831 115.624C13.3605 118.79 13.9632 123.402 17.1202 125.831L43.9929 146.511C47.1496 148.94 51.7489 148.331 54.1711 145.165L73.0819 121.159C73.5407 120.559 74.2461 120.211 75.0002 120.211C75.7538 120.211 76.4592 120.559 76.9179 121.159L95.8288 145.165C98.2514 148.331 102.849 148.94 106.007 146.511L132.88 125.831C136.037 123.402 136.64 118.791 134.217 115.624L114.768 90.9406C114.343 90.3374 114.214 89.5804 114.418 88.87C114.624 88.1591 115.136 87.5843 115.817 87.3013L145.526 75.0189C149.206 73.4982 150.976 69.1963 149.453 65.5079L136.491 34.1287C135.349 31.3635 132.656 29.6706 129.829 29.6412C128.886 29.6315 127.927 29.8048 127.008 30.186L102.512 40.3641C101.765 40.6631 100.924 40.5708 100.258 40.1181C99.5892 39.6654 99.1917 38.9159 99.1917 38.1089V7.2785C99.1917 3.28714 95.9141 0 91.9341 0H58.0615Z";

/**
 * The mark. With `trace`, GSAP draws its outline, the green fill blooms in and the trace
 * fades, as on the app's splash. Reduced motion shows the finished mark.
 */
export default function Mark({ size = 28, trace = false, glow = false, ...rest }) {
  const id = useId().replace(/:/g, "");
  const root = useRef(null);

  useLayoutEffect(() => {
    if (!trace) return undefined;
    const ctx = gsap.context(() => {
      const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      if (reduce) {
        gsap.set(".mk-trace", { opacity: 0 });
        return;
      }
      gsap.timeline({ delay: 0.15 })
        .fromTo(".mk-trace", { drawSVG: "0% 0%" }, { drawSVG: "0% 100%", duration: 1.6, ease: "power2.inOut" })
        .fromTo(".mk-glow", { drawSVG: "0% 0%" }, { drawSVG: "0% 100%", duration: 1.6, ease: "power2.inOut" }, 0.08)
        .fromTo(".mk-fill", { opacity: 0, scale: 0.94, transformOrigin: "50% 50%" }, { opacity: 1, scale: 1, duration: 0.9, ease: "expo.out" }, 1.25)
        .to([".mk-trace", ".mk-glow"], { opacity: 0, duration: 0.8, ease: "power1.in" }, 1.8);
    }, root);
    return () => ctx.revert();
  }, [trace]);

  return (
    <Box
      as="svg"
      ref={root}
      viewBox="-6 -6 162 160"
      width={`${size}px`}
      height={`${size}px`}
      flex="none"
      overflow="visible"
      aria-hidden="true"
      filter={glow ? "drop-shadow(0 0 28px rgba(61,255,110,0.35))" : undefined}
      {...rest}
    >
      <defs>
        <linearGradient id={`mk-g-${id}`} x1="75" y1="0" x2="75" y2="148" gradientUnits="userSpaceOnUse">
          <stop stopColor="#3DFF6E" />
          <stop offset="0.55" stopColor="#0E9E3E" />
          <stop offset="1" stopColor="#021A0C" />
        </linearGradient>
        <filter id={`mk-b-${id}`} x="-30%" y="-30%" width="160%" height="160%">
          <feGaussianBlur stdDeviation="5" />
        </filter>
      </defs>
      <path className="mk-fill" d={MARK_PATH} fill={`url(#mk-g-${id})`} fillRule="evenodd" />
      {trace && (
        <>
          <path className="mk-glow" d={MARK_PATH} fill="none" stroke="#3DFF6E" strokeWidth="6" filter={`url(#mk-b-${id})`} />
          <path className="mk-trace" d={MARK_PATH} fill="none" stroke="#C6FFD5" strokeWidth="2.2" strokeLinejoin="round" />
        </>
      )}
    </Box>
  );
}
