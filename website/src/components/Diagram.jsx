import { useLayoutEffect, useRef } from "react";
import { Box, HStack, Text } from "@chakra-ui/react";
import gsap from "gsap";

const W = 600;
const H = 320;

/**
 * Nodes joined by lines, with light travelling along each line (GSAP). Coordinates are in a
 * 600 x 320 space; nodes are HTML so icons and labels stay crisp at any size.
 *   nodes: [{ id, x, y, label?, icon?, accent?, big? }]
 *   edges: [{ from, to, elbow? }]   elbow: route horizontally, then vertically
 *   pill:  { text, x, y }           e.g. "Connected"
 */
export default function Diagram({ nodes, edges, pill, h = { base: "260px", md: "320px" } }) {
  const root = useRef(null);
  const at = Object.fromEntries(nodes.map((n) => [n.id, n]));

  const path = (e) => {
    const a = at[e.from];
    const b = at[e.to];
    if (e.elbow) {
      const mx = (a.x + b.x) / 2;
      return `M${a.x} ${a.y} H${mx} V${b.y} H${b.x}`;
    }
    return `M${a.x} ${a.y} L${b.x} ${b.y}`;
  };

  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      gsap.utils.toArray(".beam").forEach((el, i) => {
        const len = el.getTotalLength();
        gsap.set(el, { strokeDasharray: `${Math.min(90, len * 0.4)} ${len + 120}`, strokeDashoffset: len + 90 });
        gsap.to(el, { strokeDashoffset: -90, duration: 1.6 + (i % 3) * 0.3, ease: "power1.inOut", repeat: -1, delay: i * 0.35, repeatDelay: 0.6 });
      });
    }, root);
    return () => ctx.revert();
  }, [nodes, edges]);

  return (
    <Box ref={root} position="relative" w="100%" h={h}
      bgImage="radial-gradient(var(--chakra-colors-outline) 1px, transparent 1px)" bgSize="14px 14px">
      <Box as="svg" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" position="absolute" inset={0} w="100%" h="100%" overflow="visible">
        {edges.map((e) => (
          <g key={`${e.from}-${e.to}`}>
            <path d={path(e)} fill="none" stroke="var(--chakra-colors-outline)" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />
            <path className="beam" d={path(e)} fill="none" stroke="var(--chakra-colors-accent)" strokeWidth="2" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
          </g>
        ))}
      </Box>

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
