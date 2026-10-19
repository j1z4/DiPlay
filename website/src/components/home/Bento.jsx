import { useEffect, useState } from "react";
import { Box, Flex, Grid, HStack, SimpleGrid, Stack, Text } from "@chakra-ui/react";
import { AnimatePresence, motion } from "framer-motion";
import {
  Pulse, Car, ChatCircleText, Gauge, Lock, MapTrifold, Microphone, MusicNotes, NavigationArrow, Phone, SlidersHorizontal, WifiHigh,
} from "@phosphor-icons/react";
import Diagram from "../Diagram.jsx";
import Mark from "../Mark.jsx";
import { CenteredHeading, FrameSection } from "../Frame.jsx";

const MotionBox = motion(Box);

function CellTitle({ icon: I, title, text }) {
  return (
    <Box>
      <HStack spacing={2.5} mb={2}>
        <Box color="accent"><I size={18} weight="bold" /></Box>
        <Text fontWeight={600} fontSize="lg">{title}</Text>
      </HStack>
      <Text color="muted" lineHeight={1.55} maxW="460px">{text}</Text>
    </Box>
  );
}

const MODES = [
  { name: "Built-in car hotspot", note: "Default", tone: "accent" },
  { name: "Wi-Fi Direct", note: "Available", tone: "muted" },
  { name: "Existing Wi-Fi / Same LAN", note: "Available", tone: "muted" },
  { name: "USB", note: "Plug in", tone: "warning" },
];

/** A window of the connection modes, like a picker with status badges. */
function ModesCard() {
  return (
    <Box mt={8} mx="auto" maxW="400px" borderRadius="16px" border="1px solid" borderColor="outline" bg="surface" overflow="hidden"
      boxShadow="0 30px 60px -30px rgba(0,0,0,0.8)">
      <HStack px={4} py={3} spacing={1.5} borderBottom="1px solid" borderColor="outline">
        {["#ff5f57", "#febc2e", "#28c840"].map((c) => <Box key={c} w="9px" h="9px" borderRadius="full" bg={c} />)}
        <Text ml={3} fontSize="sm" color="muted" fontWeight={550}>Connection</Text>
      </HStack>
      <Stack spacing={0}>
        {MODES.map((m, i) => (
          <Flex key={m.name} px={4} py={3} align="center" justify="space-between" borderTop={i ? "1px solid" : "none"} borderColor="outline"
            bg={i === 0 ? "surface2" : "transparent"}>
            <Text fontSize="sm" fontWeight={i === 0 ? 600 : 500}>{m.name}</Text>
            <Box px={2} py={0.5} borderRadius="6px" fontSize="xs" fontWeight={600} color={m.tone}
              border="1px solid" borderColor="currentColor" opacity={0.9}>{m.note}</Box>
          </Flex>
        ))}
      </Stack>
    </Box>
  );
}

const STATUS = [
  { t: "Choose your iPhone", c: "muted" },
  { t: "Connecting over Wi-Fi", c: "accent" },
  { t: "CarPlay connected", c: "accent" },
  { t: "Reconnect to apply changes", c: "warning" },
];

/** The device card's status row, crossfading through its states. */
function StatusCard() {
  const [i, setI] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setI((n) => (n + 1) % STATUS.length), 2000);
    return () => clearInterval(t);
  }, []);
  const s = STATUS[i];
  return (
    <Box mt={8} mx="auto" maxW="400px" borderRadius="22px" p={2} bg="linear-gradient(180deg, #2a303b 0%, #1e222a 100%)"
      border="1px solid" borderColor="whiteAlpha.100" boxShadow="0 30px 60px -30px rgba(0,0,0,0.8)" color="#f5f7fa">
      {[["Choose this Device", "Your iPhone"], ["Connection", "Wi-Fi"]].map(([l, v]) => (
        <Box key={l} px={5} py={3} borderBottom="1px solid" borderColor="whiteAlpha.100">
          <Text fontSize="sm" color="#cfdcff" fontWeight={500}>{l}</Text>
          <Text fontWeight={500}>{v}</Text>
        </Box>
      ))}
      <Box mt={2} px={5} py={3} borderRadius="16px" bg={`linear-gradient(90deg, var(--chakra-colors-${s.c === "accent" ? "accentSoft" : "whiteAlpha-100"}), transparent 70%)`}>
        <Text fontSize="sm" color="#cfdcff" fontWeight={500}>Status</Text>
        <HStack spacing={2.5}>
          <Box w="9px" h="9px" borderRadius="full" bg={s.c} transition="background 240ms" />
          <AnimatePresence mode="wait">
            <MotionBox key={s.t} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.22 }}>
              <Text fontWeight={500} color={s.c === "muted" ? "#f5f7fa" : s.c}>{s.t}</Text>
            </MotionBox>
          </AnimatePresence>
        </HStack>
      </Box>
    </Box>
  );
}

const i22 = (I) => <I size={20} weight="bold" />;

const FLOW = {
  nodes: [
    { id: "maps", x: 70, y: 60, label: "Maps" },
    { id: "music", x: 70, y: 130, label: "Music" },
    { id: "calls", x: 70, y: 200, label: "Calls" },
    { id: "siri", x: 70, y: 270, label: "Siri" },
    { id: "op", x: 300, y: 165, icon: <Mark size={30} />, accent: true, big: true },
    { id: "screen", x: 500, y: 70, icon: i22(Car), label: "Car display" },
    { id: "hud", x: 540, y: 165, icon: i22(NavigationArrow), label: "Windshield HUD" },
    { id: "dash", x: 500, y: 260, icon: i22(Gauge), label: "Dashboard" },
  ],
  edges: [
    { from: "maps", to: "op", elbow: true }, { from: "music", to: "op", elbow: true },
    { from: "calls", to: "op", elbow: true }, { from: "siri", to: "op", elbow: true },
    { from: "op", to: "screen", elbow: true }, { from: "op", to: "hud" }, { from: "op", to: "dash", elbow: true },
  ],
  pill: { text: "Connected", x: 400, y: 140 },
};

const SMALL = [
  { icon: Lock, title: "Local first", text: "No account and no authentication server. Diagnostic reports stay on the car unless you share them." },
  { icon: NavigationArrow, title: "BYD HUD guidance", text: "Turn arrows, distance and street names on the windshield HUD, on verified firmware." },
  { icon: SlidersHorizontal, title: "Every setting, one place", text: "Picture, resolution, frame rate, audio, wheel keys and more, grouped by what you want to do." },
];

export default function Bento() {
  return (
    <FrameSection>
      <Box py={{ base: 16, md: 24 }}>
        <CenteredHeading eyebrow="Features" title="Built for the car, not around it" lead="Everything runs on the head unit itself, so CarPlay starts the way the car does." />
      </Box>
      <Grid templateColumns={{ base: "1fr", md: "1fr 1fr" }} borderTop="1px solid" borderColor="outline">
        <Box p={{ base: 6, md: 9 }} borderRight={{ md: "1px solid var(--chakra-colors-outline)" }} borderBottom="1px solid var(--chakra-colors-outline)">
          <CellTitle icon={WifiHigh} title="Four ways to connect" text="The car's own hotspot by default, Wi-Fi Direct, an existing Wi-Fi network on the same LAN, or a USB cable." />
          <ModesCard />
        </Box>
        <Box p={{ base: 6, md: 9 }} borderBottom="1px solid" borderColor="outline">
          <CellTitle icon={Pulse} title="One clear status" text="The device card says what is happening in a word and a colour, from across the cabin." />
          <StatusCard />
        </Box>
      </Grid>
      <Box p={{ base: 6, md: 9 }} borderBottom="1px solid" borderColor="outline">
        <CellTitle icon={MusicNotes} title="Your iPhone, on the car's screens" text="Maps, music, calls and Siri come through OpenPlay to the centre display, and turn-by-turn guidance to the HUD and dashboard on supported BYD firmware." />
        <Box mt={6}><Diagram {...FLOW} /></Box>
        <HStack spacing={4} mt={2} color="subtle" justify="center" display={{ base: "none", md: "flex" }}>
          {[MapTrifold, MusicNotes, Phone, ChatCircleText, Microphone].map((I, k) => <I key={k} size={16} weight="bold" />)}
        </HStack>
      </Box>
      <SimpleGrid columns={{ base: 1, md: 3 }}>
        {SMALL.map((s, k) => (
          <Box key={s.title} p={{ base: 6, md: 8 }} borderRight={{ md: k < 2 ? "1px solid var(--chakra-colors-outline)" : "none" }} borderBottom={{ base: "1px solid var(--chakra-colors-outline)", md: "none" }}>
            <CellTitle {...s} />
          </Box>
        ))}
      </SimpleGrid>
    </FrameSection>
  );
}
