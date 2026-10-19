import { useLayoutEffect, useRef, useState } from "react";
import { Box, HStack, Text } from "@chakra-ui/react";
import gsap from "gsap";

const W = 600;
const H = 320;
const SPEED = 260; // beam speed in screen pixels per second, the same on every line
const BEAM = 72; // beam length in screen pixels

/**
 * Nodes joined by lines, with light travelling along each line (GSAP). Layout coordinates are
 * in a 600 x 320 space; nodes are HTML so icons and labels stay crisp at any size.
 *   nodes: [{ id, x, y, label?, icon?, accent?, big? }]
 *   edges: [{ from, to, elbow? }]   elbow: route horizontally, then vertically
 *   pill:  { text, x, y }           e.g. "Connected"
 *
 * Lines are drawn in the box's real pixel size, not a stretched viewBox: a stretched SVG measures
 * path length in its own units but draws dashes in screen pixels, so beams jumped and stuttered.
 */
export default function Diagram({ nodes, edges, pill, h = { base: "260px", md: "320px" } }) {
  const root = useRef(null);
  const [size, setSize] = useState(null);
  const at = Object.fromEntries(nodes.map((n) => [n.id, n]));

  // Follow the box's size so the lines always match the node positions.
  useLayoutEffect(() => {
    const el = root.current;
    const measure = () => setSize({ w: el.clientWidth, h: el.clientHeight });
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const path = (e) => {
    const sx = size.w / W;
    const sy = size.h / H;
    const a = { x: at[e.from].x * sx, y: at[e.from].y * sy };
    const b = { x: at[e.to].x * sx, y: at[e.to].y * sy };
    if (e.elbow) {
      const mx = (a.x + b.x) / 2;
      return `M${a.x} ${a.y} H${mx} V${b.y} H${b.x}`;
    }
    return `M${a.x} ${a.y} L${b.x} ${b.y}`;
  };

  useLayoutEffect(() => {
    if (!size) return undefined;
    const ctx = gsap.context(() => {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      gsap.utils.toArray(".beam").forEach((el, i) => {
        const len = el.getTotalLength();
        // The beam starts just before the line and ends just past it, so each loop restarts
        // out of sight; a linear ease keeps the same speed from end to end.
        gsap.set(el, { strokeDasharray: `${BEAM} ${len + BEAM * 2}`, strokeDashoffset: BEAM });
        gsap.to(el, {
          strokeDashoffset: -len,
          duration: (len + BEAM) / SPEED,
          ease: "none",
          repeat: -1,
          repeatDelay: 0.5,
          delay: (i * 0.37) % 1.5,
        });
      });
    }, root);
    return () => ctx.revert();
  }, [size, nodes, edges]);

  return (
    <Box ref={root} position="relative" w="100%" h={h}
      bgImage="radial-gradient(var(--chakra-colors-outline) 1px, transparent 1px)" bgSize="14px 14px">
      {size && (
        <Box as="svg" viewBox={`0 0 ${size.w} ${size.h}`} position="absolute" inset={0} w="100%" h="100%" overflow="visible">
          {edges.map((e) => (
            <g key={`${e.from}-${e.to}`}>
              <path d={path(e)} fill="none" stroke="var(--chakra-colors-outline)" strokeWidth="1.5" />
              <path className="beam" d={path(e)} fill="none" stroke="var(--chakra-colors-accent)" strokeWidth="2" strokeLinecap="round"
                strokeDasharray="0 1" />
            </g>
          ))}
        </Box>
      )}

      {nodes.map((n) => (
        <Box key={n.id} position="absolute" left={`${(n.x / W) * 100}%`} top={`${(n.y / H) * 100}%`} transform="translate(-50%,-50%)" zIndex={1}>
          {n.label && !n.icon ? (
            <HStack spacing={2} px={3} py={1.5} borderRadius="10px" bg="surface" border="1px solid" borderColor="outline" whiteSpace="nowrap">
              <Text fontSize="sm" fontWeight={550}>{n.label}</Text>
            </HStack>
          ) : (
            <Box textAlign="center">
              <Box display="grid" placeItems="center" mx="auto" w={n.big ? "64px" : "48px"} h={n.big ? "64px" : "48px"} borderRadius="16px"
                bg={n.accent ? "text" : "surface"} color={n.accent ? "bg" : "text"} border="1px solid" borderColor={n.accent ? "text" : "outline"}
                boxShadow={n.accent ? "0 0 0 6px var(--chakra-colors-accentSoft), 0 12px 32px -12px rgba(47,228,106,0.6)" : "0 10px 24px -14px rgba(0,0,0,0.7)"}>
                {n.icon}
              </Box>
              {n.label && <Text mt={2} fontSize="xs" color="muted" fontWeight={550} whiteSpace="nowrap">{n.label}</Text>}
            </Box>
          )}
        </Box>
      ))}

      {pill && (
        <Box position="absolute" left={`${(pill.x / W) * 100}%`} top={`${(pill.y / H) * 100}%`} transform="translate(-50%,-50%)" zIndex={1}
          px={2.5} py={1} borderRadius="8px" bg="accent" color="bg" fontSize="xs" fontWeight={650} whiteSpace="nowrap">
          {pill.text}
        </Box>
      )}
    </Box>
  );
}
