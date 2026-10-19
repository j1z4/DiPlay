import { useEffect, useLayoutEffect, useRef, useState } from "react";
import {
  Box, Button, Container, Flex, Grid, Heading, HStack, Image, Link, SimpleGrid, Stack, Text,
} from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";
import { AnimatePresence, motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import {
  ArrowRight, ArrowUpRight, BookOpen, Broadcast, CarProfile, ChatCircleDots, DownloadSimple, GithubLogo, NavigationArrow,
  Plugs, ShieldCheck, SlidersHorizontal, Star, WifiHigh,
} from "@phosphor-icons/react";
import Mark from "../components/Mark.jsx";
import { Reveal, SectionHeading } from "../components/Reveal.jsx";
import { GITHUB, GITHUB_REPO, ISSUES, UPSTREAM, BASE_VERSION } from "../site.js";

gsap.registerPlugin(ScrollTrigger);

const MotionBox = motion(Box);

// What CarPlay brings into the car: the faded cascade down the hero's right edge.
const CASCADE = ["maps", "music", "messages", "calls", "siri", "podcasts", "audiobooks", "calendar", "weather"];

// The loading screen's status line, cycling as it does while CarPlay connects.
const STATUSES = [
  { text: "Starting OpenPlay…", tone: "muted" },
  { text: "Waiting for your iPhone", tone: "muted" },
  { text: "Connecting over Wi-Fi", tone: "accent" },
  { text: "CarPlay connected", tone: "accent" },
];

function useStars() {
  const [stars, setStars] = useState(null);
  useEffect(() => {
    let live = true;
    fetch(`https://api.github.com/repos/${GITHUB_REPO}`)
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => { if (live && d && typeof d.stargazers_count === "number") setStars(d.stargazers_count); })
      .catch(() => {});
    return () => { live = false; };
  }, []);
  return stars;
}

/** The app's status line: a dot and one line of text that crossfades, no slide. */
function StatusLine() {
  const [i, setI] = useState(0);
  const reduce = useReducedMotion();
  useEffect(() => {
    const t = setInterval(() => setI((n) => (n + 1) % STATUSES.length), 2200);
    return () => clearInterval(t);
  }, []);
  const s = STATUSES[i];
  return (
    <HStack spacing={3} minW="220px">
      <Box position="relative" w="10px" h="10px" flex="none">
        <Box position="absolute" inset={0} borderRadius="full" bg={s.tone} />
        {s.tone === "accent" && !reduce && (
          <MotionBox position="absolute" inset={0} borderRadius="full" bg="accent"
            animate={{ scale: [1, 2.4], opacity: [0.6, 0] }} transition={{ duration: 1.4, repeat: Infinity, ease: "easeOut" }} />
        )}
      </Box>
      <AnimatePresence mode="wait">
        <MotionBox key={s.text} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.24 }}>
          <Text fontWeight={550} fontSize="sm" color={s.tone === "accent" ? "accent" : "muted"} whiteSpace="nowrap">{s.text}</Text>
        </MotionBox>
      </AnimatePresence>
    </HStack>
  );
}

/** Large faded words down the right edge; GSAP staggers them in and lets them drift. */
function Cascade() {
  const root = useRef(null);
  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      gsap.fromTo(".cw", { opacity: 0, x: 40 }, { opacity: 1, x: 0, duration: 1.2, ease: "expo.out", stagger: 0.07, delay: 0.4 });
      gsap.utils.toArray(".cw").forEach((el, i) => {
        gsap.to(el, { x: i % 2 ? -14 : 14, duration: 5 + (i % 3), ease: "sine.inOut", yoyo: true, repeat: -1, delay: 1.6 + i * 0.2 });
      });
    }, root);
    return () => ctx.revert();
  }, []);
  return (
    <Stack ref={root} spacing={{ lg: 1, "2xl": 2 }} align="flex-end" aria-hidden="true" userSelect="none">
      {CASCADE.map((w, i) => (
        <Text key={w} className="cw" fontFamily="mono" fontWeight={400} lineHeight={1.05} letterSpacing="0.04em"
          fontSize={{ lg: "44px", xl: "54px", "2xl": "64px" }}
          color="cascade" whiteSpace="nowrap">
          {w}
        </Text>
      ))}
    </Stack>
  );
}

function Hero() {
  const stars = useStars();
  const reduce = useReducedMotion();
  const item = (d) => ({
    initial: reduce ? false : { opacity: 0, y: 18 },
    animate: { opacity: 1, y: 0 },
    transition: { type: "spring", stiffness: 120, damping: 20, delay: d },
  });

  return (
    <Box position="relative" overflow="hidden">
      {/* A faint signal grid and a green bloom behind the wordmark. */}
      <Box position="absolute" inset={0} opacity={0.45} pointerEvents="none"
        bgImage="linear-gradient(var(--chakra-colors-outline) 1px, transparent 1px), linear-gradient(90deg, var(--chakra-colors-outline) 1px, transparent 1px)"
        bgSize="56px 56px"
        sx={{ maskImage: "radial-gradient(ellipse at 50% 45%, black 10%, transparent 72%)" }} />
      <Box position="absolute" left="50%" top="46%" w="900px" h="560px" transform="translate(-50%,-50%)" pointerEvents="none"
        bg="radial-gradient(ellipse, rgba(61,255,110,0.13) 0%, transparent 62%)" />

      <Box maxW="1680px" mx="auto" px={{ base: 4, md: 8, xl: 12 }} pt={{ base: 10, md: 14 }} pb={{ base: 14, md: 16 }} position="relative">
        <Grid templateColumns={{ base: "1fr", lg: "minmax(0,1fr) minmax(0,760px) minmax(0,1fr)" }} gap={{ base: 10, lg: 8 }} alignItems="start">
          {/* Top-left: the traced mark over the live status line, as on the app's loading screen. */}
          <Stack spacing={5} align={{ base: "center", lg: "flex-start" }} pt={{ lg: 2 }}>
            <Mark size={128} trace glow />
            <Box display={{ base: "none", lg: "block" }}><StatusLine /></Box>
          </Stack>

          {/* Center: wordmark, tagline, one line of what it is, two buttons. */}
          <Stack spacing={8} align="center" textAlign="center" pt={{ lg: "200px", xl: "220px" }}>
            <MotionBox {...item(0.05)}>
              <Box display="inline-block">
                <Heading as="h1" fontSize={{ base: "64px", sm: "92px", md: "120px", xl: "136px" }} fontWeight={650} letterSpacing="-0.045em" lineHeight={0.9}>
                  OpenPlay
                </Heading>
                <Text fontWeight={600} fontSize={{ base: "md", md: "xl" }} color="muted" textAlign="right" mt={{ base: 3, md: 4 }}>
                  (a fork of DiPlay)
                </Text>
              </Box>
            </MotionBox>

            <MotionBox {...item(0.12)}>
              <Text fontSize={{ base: "2xl", md: "4xl" }} fontWeight={600} letterSpacing="-0.02em" lineHeight={1.1}>
                CarPlay, that you own.
              </Text>
              <Text mt={4} fontSize={{ base: "lg", md: "xl" }} color="muted" maxW="640px" lineHeight={1.55} mx="auto">
                Wired and wireless CarPlay for compatible BYD Android head units. It runs on the car itself: no dongle, jailbreak, Mac or account.
              </Text>
            </MotionBox>

            <MotionBox {...item(0.2)}>
              <Flex gap={3} wrap="wrap" justify="center">
                <Button as={RouterLink} to="/docs" variant="primary" size="lg" rightIcon={<ArrowRight weight="bold" />}>
                  Read the docs
                </Button>
                <Button as="a" href={GITHUB} target="_blank" rel="noreferrer" variant="ghostLine" size="lg" leftIcon={<GithubLogo size={20} weight="bold" />}>
                  GitHub
                  {stars > 0 && (
                    <HStack as="span" spacing={1} ml={3} pl={3} borderLeft="1px solid" borderColor="outline" color="muted" fontSize="md">
                      <Star size={16} weight="fill" />
                      <span>{stars.toLocaleString()}</span>
                    </HStack>
                  )}
                </Button>
              </Flex>
            </MotionBox>

            <Box display={{ base: "block", lg: "none" }}><StatusLine /></Box>
          </Stack>

          {/* Right: the faded cascade, filling the edge on wide screens. */}
          <Box display={{ base: "none", lg: "block" }}>
            <Cascade />
          </Box>
        </Grid>

        <ReleaseBox />
      </Box>
    </Box>
  );
}

/** The bordered release note under the hero: version on the left, what changed on the right. */
function ReleaseBox() {
  return (
    <Reveal maxW="1100px" mx="auto" mt={{ base: 14, lg: 10 }}>
      <Grid templateColumns={{ base: "1fr", md: "200px 1fr" }} gap={{ base: 4, md: 8 }} p={{ base: 6, md: 8 }}
        border="1px solid" borderColor="outline" borderRadius="card" bg="surface"
        boxShadow="inset 0 1px 0 rgba(255,255,255,0.04)">
        <Box fontFamily="mono" color="accent" fontSize={{ base: "lg", md: "xl" }} lineHeight={1.4}>
          <Text>DiPlay</Text>
          <Text>v{BASE_VERSION}</Text>
        </Box>
        <Box>
          <Text color="muted" fontSize="lg" lineHeight={1.65}>
            <Link href={`${UPSTREAM}/releases/tag/v${BASE_VERSION}`} isExternal color="text" fontWeight={600}
              textDecoration="underline" textUnderlineOffset="4px" textDecorationColor="accent">
              OpenPlay is built on DiPlay {BASE_VERSION}.
            </Link>{" "}
            Music plays in step with the iPhone again, wired CarPlay recovers by itself when the iPhone never opens it, and the
            microphone no longer closes the app on head units without Android's own Opus encoder. It installs on Android 7.0 and later.
          </Text>
          <Link href={`${UPSTREAM}/blob/main/docs/RELEASE-NOTES-${BASE_VERSION}.md`} isExternal display="inline-flex" alignItems="center" gap={1.5}
            mt={4} color="accent" fontWeight={600} fontSize="sm">
            Release notes <ArrowUpRight size={14} weight="bold" />
          </Link>
        </Box>
      </Grid>
    </Reveal>
  );
}

function Showcase() {
  const ref = useRef(null);
  const { scrollYProgress } = useScroll({ target: ref, offset: ["start end", "end start"] });
  const rotate = useTransform(scrollYProgress, [0, 0.45], [14, 0]);
  const scale = useTransform(scrollYProgress, [0, 0.45], [0.9, 1]);
  const reduce = useReducedMotion();

  return (
    <Container maxW="7xl" px={{ base: 4, md: 6 }} ref={ref}>
      <Box sx={{ perspective: "1600px" }}>
        <MotionBox
          style={reduce ? undefined : { rotateX: rotate, scale }}
          borderRadius={{ base: "18px", md: "32px" }}
          p={{ base: 2, md: 3 }}
          bg="linear-gradient(180deg, #2a303b 0%, #14161b 100%)"
          border="1px solid"
          borderColor="whiteAlpha.100"
          boxShadow="0 60px 120px -40px rgba(0,0,0,0.9), 0 0 0 1px rgba(255,255,255,0.04)"
        >
          <Image src="./screens/home.jpg" alt="OpenPlay Home on a head unit: the device card with the iPhone, Wi-Fi connection and status, and the Connect phone button" borderRadius={{ base: "12px", md: "24px" }} w="100%" />
        </MotionBox>
      </Box>
      <Text textAlign="center" color="subtle" fontSize="sm" mt={5}>OpenPlay Home, running on an Android head-unit emulator.</Text>
    </Container>
  );
}

const FEATURES = [
  { icon: Plugs, title: "Wired and wireless", text: "Plug into a USB data port, or go wireless. Each connection uses local authentication." },
  { icon: WifiHigh, title: "Three ways to go wireless", text: "Wi-Fi Direct, the car's own hotspot, or an existing Wi-Fi network on the same LAN." },
  { icon: NavigationArrow, title: "BYD HUD guidance", text: "Turn arrows, distance and street names on the windshield HUD, on verified firmware." },
  { icon: SlidersHorizontal, title: "Every setting, in one place", text: "Picture, resolution, frame rate, audio streams, wheel keys and more, grouped by what you want to do." },
  { icon: ShieldCheck, title: "Local first", text: "Diagnostic reports stay on the car. They are shared only if you choose to share them." },
  { icon: CarProfile, title: "Made for the cabin", text: "A black, high-contrast interface with large targets, built for a car display at arm's length." },
];

function Features() {
  return (
    <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 24, md: 36 }}>
      <SectionHeading eyebrow="// features" title="Built for the car, not around it." lead="Everything OpenPlay does runs on the head unit itself, so CarPlay starts the way the car does." />
      <SimpleGrid columns={{ base: 1, md: 2, lg: 3 }} spacing={4} mt={12}>
        {FEATURES.map((f, i) => (
          <Reveal key={f.title} delay={i * 0.05}>
            <MotionBox
              h="100%"
              p={7}
              bg="surface"
              border="1px solid"
              borderColor="outline"
              borderRadius="card"
              whileHover={{ y: -4 }}
              transition={{ type: "spring", stiffness: 300, damping: 24 }}
              role="group"
            >
              <Box display="inline-grid" placeItems="center" w="48px" h="48px" borderRadius="14px" bg="accentSoft" color="accent" mb={5}
                transition="transform 200ms" _groupHover={{ transform: "scale(1.08)" }}>
                <f.icon size={24} weight="bold" />
              </Box>
              <Heading as="h3" fontSize="xl" fontWeight={600} letterSpacing="-0.01em" mb={2}>{f.title}</Heading>
              <Text color="muted" lineHeight={1.55}>{f.text}</Text>
            </MotionBox>
          </Reveal>
        ))}
      </SimpleGrid>
    </Container>
  );
}

const STEPS = [
  { n: "01", title: "Install on the car", text: "Install the APK on the head unit, not the iPhone. The head unit must allow APK installation; Android 7.0 or later." },
  { n: "02", title: "Choose your iPhone", text: "Pair the iPhone with the car over Bluetooth, then pick it in OpenPlay. Choose Wi-Fi or USB." },
  { n: "03", title: "Connect phone", text: "Press Connect phone. The loading screen shows each stage until CarPlay appears; next time it can connect on its own." },
];

function HowItWorks() {
  const root = useRef(null);
  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      // The rail fills as you scroll through the steps; each step lights as the fill reaches it.
      gsap.fromTo(".hiw-fill", { scaleY: 0 }, {
        scaleY: 1, ease: "none", transformOrigin: "top",
        scrollTrigger: { trigger: ".hiw-steps", start: "top 70%", end: "bottom 60%", scrub: 0.6 },
      });
      gsap.utils.toArray(".hiw-step").forEach((el) => {
        gsap.fromTo(el, { opacity: 0.35 }, {
          opacity: 1, duration: 0.4,
          scrollTrigger: { trigger: el, start: "top 65%", toggleActions: "play none none reverse" },
        });
      });
    }, root);
    return () => ctx.revert();
  }, []);

  return (
    <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 24, md: 36 }} ref={root}>
      <Grid templateColumns={{ base: "1fr", lg: "0.9fr 1.1fr" }} gap={{ base: 10, lg: 16 }}>
        <Box position={{ lg: "sticky" }} top="120px" alignSelf="start">
          <SectionHeading eyebrow="// how it works" title="Three steps to CarPlay." lead="The first connection takes a minute. After that, OpenPlay can connect when it opens or when your iPhone comes back in range." />
          <Button as={RouterLink} to="/docs/install" variant="ghostLine" size="lg" mt={8} rightIcon={<ArrowRight weight="bold" />}>Install guide</Button>
        </Box>
        <Box className="hiw-steps" position="relative" pl={{ base: 8, md: 12 }}>
          <Box position="absolute" left={{ base: "11px", md: "15px" }} top={2} bottom={2} w="2px" bg="outline" borderRadius="full" />
          <Box className="hiw-fill" position="absolute" left={{ base: "11px", md: "15px" }} top={2} bottom={2} w="2px" bg="accent" borderRadius="full" />
          <Stack spacing={0}>
            {STEPS.map((s, i) => (
              <Box key={s.n} className="hiw-step" py={8} borderTop={i ? "1px solid" : "none"} borderColor="outline" position="relative">
                <Box position="absolute" left={{ base: "-29px", md: "-41px" }} top="42px" w="12px" h="12px" borderRadius="full" bg="accent" boxShadow="0 0 0 6px var(--chakra-colors-bg)" />
                <Text fontFamily="mono" color="accent" fontSize="sm" mb={2}>{s.n}</Text>
                <Heading as="h3" fontSize={{ base: "2xl", md: "3xl" }} fontWeight={600} letterSpacing="-0.02em" mb={3}>{s.title}</Heading>
                <Text color="muted" fontSize="lg" lineHeight={1.55} maxW="560px">{s.text}</Text>
              </Box>
            ))}
          </Stack>
        </Box>
      </Grid>
    </Container>
  );
}

const INVOLVED = [
  { icon: GithubLogo, label: "GitHub", text: "Source, issues and pull requests", href: GITHUB },
  { icon: DownloadSimple, label: "Releases", text: "Builds and release notes", href: `${GITHUB}/releases` },
  { icon: ChatCircleDots, label: "Report a problem", text: "Attach a diagnostic report", href: ISSUES },
  { icon: Broadcast, label: "Upstream DiPlay", text: "Where OpenPlay comes from", href: UPSTREAM },
];

function GetInvolved() {
  return (
    <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 24, md: 36 }}>
      <SectionHeading eyebrow="// get involved" title="Open by default." lead="OpenPlay is GPL-3.0. Read the code, test it in your car, and send what you find." />
      <SimpleGrid columns={{ base: 1, sm: 2, lg: 4 }} spacing={4} mt={12}>
        {INVOLVED.map((x, i) => (
          <Reveal key={x.label} delay={i * 0.05}>
            <Link href={x.href} isExternal display="block" h="100%" p={6} bg="surface" border="1px solid" borderColor="outline" borderRadius="card" role="group"
              transition="all 180ms cubic-bezier(.2,.8,.2,1)" _hover={{ bg: "text", color: "bg", transform: "translateY(-3px)" }}>
              <Flex justify="space-between" align="start" mb={8}>
                <x.icon size={28} weight="bold" />
                <Box opacity={0} transform="translateX(-6px)" transition="all 180ms" _groupHover={{ opacity: 1, transform: "none" }}>
                  <ArrowRight size={20} weight="bold" />
                </Box>
              </Flex>
              <Text fontWeight={600} fontSize="lg">{x.label}</Text>
              <Text color="muted" _groupHover={{ color: "inherit", opacity: 0.7 }}>{x.text}</Text>
            </Link>
          </Reveal>
        ))}
      </SimpleGrid>

      <Reveal mt={16}>
        <Flex direction={{ base: "column", md: "row" }} align={{ md: "center" }} justify="space-between" gap={6} p={{ base: 8, md: 12 }}
          borderRadius="card" border="1px solid" borderColor="outline" bg="linear-gradient(135deg, rgba(47,228,106,0.14), transparent 60%)">
          <Box>
            <Heading as="h2" fontSize={{ base: "2xl", md: "4xl" }} fontWeight={600} letterSpacing="-0.03em">Ready for the drive?</Heading>
            <Text color="muted" fontSize="lg" mt={2}>Start with what you need, then connect your iPhone.</Text>
          </Box>
          <Button as={RouterLink} to="/docs/getting-started" variant="primary" size="lg" leftIcon={<BookOpen size={20} weight="bold" />}>Get started</Button>
        </Flex>
      </Reveal>
    </Container>
  );
}

export default function Home() {
  return (
    <>
      <Hero />
      <Showcase />
      <Features />
      <HowItWorks />
      <GetInvolved />
    </>
  );
}
