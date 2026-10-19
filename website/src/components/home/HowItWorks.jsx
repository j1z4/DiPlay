import { useEffect, useState } from "react";
import { Box, Grid, HStack, Stack, Text } from "@chakra-ui/react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Bluetooth, Car, CheckCircle, DeviceMobile, DownloadSimple, Package, WifiHigh } from "@phosphor-icons/react";
import Diagram from "../Diagram.jsx";
import Mark from "../Mark.jsx";
import { CenteredHeading, FrameSection } from "../Frame.jsx";

const MotionBox = motion(Box);
const STEP_MS = 5200;

const icon = (I) => <I size={22} weight="bold" />;

const STEPS = [
  {
    icon: DownloadSimple,
    title: "Install on the car",
    text: "Install the APK on the head unit, not the iPhone. The head unit must allow APK installation; Android 7.0 or later.",
    diagram: {
      nodes: [
        { id: "apk", x: 120, y: 160, icon: icon(Package), label: "OpenPlay APK" },
        { id: "unit", x: 470, y: 160, icon: <Mark size={30} />, label: "Head unit", accent: true, big: true },
      ],
      edges: [{ from: "apk", to: "unit" }],
      pill: { text: "Android 7.0+", x: 295, y: 130 },
    },
  },
  {
    icon: Bluetooth,
    title: "Choose your iPhone",
    text: "Pair the iPhone with the car over Bluetooth, then pick it in OpenPlay and choose Wi-Fi or USB.",
    diagram: {
      nodes: [
        { id: "phone", x: 110, y: 160, icon: icon(DeviceMobile), label: "Your iPhone" },
        { id: "bt", x: 300, y: 160, icon: icon(Bluetooth), label: "Bluetooth" },
        { id: "unit", x: 490, y: 160, icon: <Mark size={30} />, label: "OpenPlay", accent: true, big: true },
      ],
      edges: [{ from: "phone", to: "bt" }, { from: "bt", to: "unit" }],
      pill: { text: "Paired", x: 205, y: 128 },
    },
  },
  {
    icon: WifiHigh,
    title: "Connect phone",
    text: "Press Connect phone. The loading screen shows each stage until CarPlay appears; next time it can connect on its own.",
    diagram: {
      nodes: [
        { id: "phone", x: 90, y: 90, icon: icon(DeviceMobile), label: "iPhone" },
        { id: "wifi", x: 90, y: 240, icon: icon(WifiHigh), label: "Car hotspot" },
        { id: "unit", x: 320, y: 165, icon: <Mark size={30} />, label: "OpenPlay", accent: true, big: true },
        { id: "car", x: 520, y: 165, icon: icon(Car), label: "Car display" },
      ],
      edges: [{ from: "phone", to: "unit", elbow: true }, { from: "wifi", to: "unit", elbow: true }, { from: "unit", to: "car" }],
      pill: { text: "CarPlay connected", x: 425, y: 132 },
    },
  },
];

export default function HowItWorks() {
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const reduce = useReducedMotion();

  // Steps advance on their own, as a short walkthrough; hovering holds the current one.
  useEffect(() => {
    if (paused || reduce) return undefined;
    const t = setTimeout(() => setActive((a) => (a + 1) % STEPS.length), STEP_MS);
    return () => clearTimeout(t);
  }, [active, paused, reduce]);

  const step = STEPS[active];

  return (
    <FrameSection>
      <Box py={{ base: 16, md: 24 }}>
        <CenteredHeading eyebrow="How it works" title="Three steps to CarPlay" lead="The first connection takes a minute. After that, OpenPlay can connect when it opens or when your iPhone comes back in range." />
      </Box>
      <Grid templateColumns={{ base: "1fr", lg: "0.85fr 1.15fr" }} borderTop="1px solid" borderColor="outline"
        onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}>
        <Stack spacing={0} borderRight={{ lg: "1px solid var(--chakra-colors-outline)" }}>
          {STEPS.map((s, i) => {
            const on = i === active;
            return (
              <Box key={s.title} as="button" type="button" onClick={() => setActive(i)} textAlign="left" position="relative"
                px={{ base: 5, md: 8 }} py={7} borderBottom="1px solid" borderColor="outline" aria-pressed={on}
                bg={on ? "surface" : "transparent"} transition="background 200ms"
                // The active step gets a fine hatch, as a selected row.
                bgImage={on ? "repeating-linear-gradient(135deg, var(--chakra-colors-outline) 0 1px, transparent 1px 7px)" : undefined}
                _hover={{ bg: on ? "surface" : "surface2" }}>
                <HStack spacing={3} mb={2} color={on ? "text" : "muted"}>
                  <s.icon size={18} weight="bold" />
                  <Text fontWeight={600} fontSize="lg">{s.title}</Text>
                </HStack>
                <Text color="muted" lineHeight={1.55} pl={{ base: 0, md: 7 }}>{s.text}</Text>
                {on && (
                  <Box position="absolute" left={0} right={0} bottom="-1px" h="2px" bg="outline">
                    <MotionBox key={`${active}-${paused}`} h="100%" bg="accent" initial={{ width: paused || reduce ? "100%" : "0%" }}
                      animate={{ width: "100%" }} transition={{ duration: paused || reduce ? 0 : STEP_MS / 1000, ease: "linear" }} />
                  </Box>
                )}
              </Box>
            );
          })}
        </Stack>
        <Box position="relative" minH={{ base: "300px", md: "380px" }} display="grid" alignItems="center" px={{ base: 3, md: 8 }} py={6} overflow="hidden">
          <AnimatePresence mode="wait">
            <MotionBox key={active} initial={{ opacity: 0, scale: 0.98 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.98 }}
              transition={{ duration: 0.3, ease: [0.2, 0.8, 0.2, 1] }}>
              <Diagram {...step.diagram} />
            </MotionBox>
          </AnimatePresence>
          <HStack position="absolute" bottom={4} right={5} spacing={1.5} color="subtle" fontSize="xs">
            <CheckCircle size={14} weight="bold" />
            <Text>Step {active + 1} of {STEPS.length}</Text>
          </HStack>
        </Box>
      </Grid>
    </FrameSection>
  );
}
