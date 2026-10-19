import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { Box, Button, Flex, Grid, Heading, HStack, Image, Link, Stack, Text } from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";
import { AnimatePresence, motion, useReducedMotion, useScroll, useTransform } from "framer-motion";
import gsap from "gsap";
import { ArrowRight, ArrowUpRight, GithubLogo, Star } from "@phosphor-icons/react";
import Mark from "../components/Mark.jsx";
import { Reveal } from "../components/Reveal.jsx";
import { FrameSection } from "../components/Frame.jsx";
import Bento from "../components/home/Bento.jsx";
import HowItWorks from "../components/home/HowItWorks.jsx";
import Faq from "../components/home/Faq.jsx";
import OrbitCta from "../components/home/OrbitCta.jsx";
import { GITHUB, GITHUB_REPO, UPSTREAM, BASE_VERSION } from "../site.js";

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
  const rotate = useTransform(scrollYProgress, [0, 0.45], [12, 0]);
  const scale = useTransform(scrollYProgress, [0, 0.45], [0.92, 1]);
  const reduce = useReducedMotion();

  return (
    <FrameSection band mt={{ base: 14, md: 20 }}>
      <Box ref={ref} px={{ base: 3, md: 10 }} py={{ base: 6, md: 12 }} sx={{ perspective: "1600px" }}>
        <MotionBox
          style={reduce ? undefined : { rotateX: rotate, scale }}
          borderRadius={{ base: "16px", md: "28px" }}
          p={{ base: 1.5, md: 2.5 }}
          bg="linear-gradient(180deg, #2a303b 0%, #14161b 100%)"
          border="1px solid"
          borderColor="whiteAlpha.100"
          boxShadow="0 60px 120px -40px rgba(0,0,0,0.9), 0 0 0 1px rgba(255,255,255,0.04)"
        >
          <Image src="./screens/home.jpg" alt="OpenPlay Home on a head unit: the device card with the iPhone, Wi-Fi connection and status, and the Connect phone button" borderRadius={{ base: "11px", md: "21px" }} w="100%" />
        </MotionBox>
        <Text textAlign="center" color="subtle" fontSize="sm" mt={5}>OpenPlay Home, running on an Android head-unit emulator.</Text>
      </Box>
    </FrameSection>
  );
}

export default function Home() {
  return (
    <>
      <Hero />
      <Showcase />
      <Bento />
      <HowItWorks />
      <Faq />
      <OrbitCta />
    </>
  );
}
