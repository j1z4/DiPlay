import { useLayoutEffect, useRef } from "react";
import { Box, Button, Container, Flex, Grid, Heading, Link, SimpleGrid, Stack, Text } from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import { ArrowRight, GithubLogo, HandHeart, Key, Lock, SteeringWheel, Warning } from "@phosphor-icons/react";
import Mark from "../components/Mark.jsx";
import { Reveal, SectionHeading } from "../components/Reveal.jsx";
import { GITHUB, UPSTREAM, useTitle } from "../site.js";

gsap.registerPlugin(ScrollTrigger);

const LINEAGE = [
  { name: "xcertplay", role: "The CarPlay receiver", text: "CarPlay wired and wireless on Android head units: iAP2, AirPlay, media and authentication.", href: "https://github.com/shilapi/xcertplay", licence: "GPL-3.0" },
  { name: "DiAuto", role: "The home and settings UI", text: "The interface DiPlay's home, settings and website adapt.", href: "https://github.com/shihabal3amri/DiAuto", licence: "AGPL-3.0" },
  { name: "DiPlay", role: "CarPlay for BYD head units", text: "An independent app for compatible BYD head units, with HUD guidance and car hotspot support.", href: UPSTREAM, licence: "GPL-3.0" },
  { name: "OpenPlay", role: "This fork", text: "DiPlay's engine with a new interface: a traced splash, one device card and every setting in one place.", href: GITHUB, licence: "GPL-3.0", current: true },
];

const PRINCIPLES = [
  { icon: Key, title: "Yours to own", text: "The source is open. Read it, build it, change it and run it in your own car." },
  { icon: Lock, title: "Local first", text: "No account and no authentication server. Diagnostic reports leave the car only when you share them." },
  { icon: SteeringWheel, title: "Driver first", text: "Large targets, high contrast and one clear status, so a glance is enough." },
  { icon: HandHeart, title: "Honest about limits", text: "What is verified, and on which car, is written down. What is not is called experimental." },
];

function Lineage() {
  const root = useRef(null);
  useLayoutEffect(() => {
    const ctx = gsap.context(() => {
      if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
      const tl = gsap.timeline({ scrollTrigger: { trigger: root.current, start: "top 70%" } });
      tl.fromTo(".ln-line", { scaleX: 0 }, { scaleX: 1, transformOrigin: "left", duration: 1.1, ease: "power2.inOut" })
        .fromTo(".ln-node", { opacity: 0, y: 24 }, { opacity: 1, y: 0, stagger: 0.14, duration: 0.6, ease: "expo.out" }, 0.1);
    }, root);
    return () => ctx.revert();
  }, []);

  return (
    <Box ref={root} position="relative" mt={12}>
      <Box className="ln-line" display={{ base: "none", lg: "block" }} position="absolute" left="4%" right="4%" top="22px" h="2px"
        bg="linear-gradient(90deg, var(--chakra-colors-outline), var(--chakra-colors-accent))" />
      <SimpleGrid columns={{ base: 1, md: 2, lg: 4 }} spacing={4}>
        {LINEAGE.map((n) => (
          <Box key={n.name} className="ln-node">
            <Box w="46px" h="46px" borderRadius="full" display="grid" placeItems="center" mb={5} position="relative"
              bg={n.current ? "text" : "surface"} border="1px solid" borderColor={n.current ? "text" : "outline"}>
              {n.current ? <Mark size={24} /> : <Box w="10px" h="10px" borderRadius="full" bg="subtle" />}
            </Box>
            <Link href={n.href} isExternal display="block" p={6} h="calc(100% - 66px)" borderRadius="card" border="1px solid"
              borderColor={n.current ? "accent" : "outline"} bg={n.current ? "accentSoft" : "surface"} transition="transform 180ms" _hover={{ transform: "translateY(-3px)" }}>
              <Flex justify="space-between" align="baseline" mb={1}>
                <Text fontWeight={650} fontSize="xl" letterSpacing="-0.02em">{n.name}</Text>
                <Text fontFamily="mono" fontSize="xs" color="subtle">{n.licence}</Text>
              </Flex>
              <Text color="accent" fontWeight={550} fontSize="sm" mb={3}>{n.role}</Text>
              <Text color="muted" lineHeight={1.55}>{n.text}</Text>
            </Link>
          </Box>
        ))}
      </SimpleGrid>
    </Box>
  );
}

export default function About() {
  useTitle("About · OpenPlay");
  return (
    <>
      <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 12, md: 20 }}>
        <Grid templateColumns={{ base: "1fr", lg: "1.2fr 0.8fr" }} gap={12} alignItems="center">
          <Reveal>
            <Text fontFamily="mono" fontSize="sm" color="accent" mb={4}>// about</Text>
            <Heading as="h1" fontSize={{ base: "5xl", md: "7xl" }} fontWeight={650} letterSpacing="-0.045em" lineHeight={0.98}>
              CarPlay, that you own.
            </Heading>
            <Text fontSize={{ base: "lg", md: "xl" }} color="muted" mt={6} maxW="620px" lineHeight={1.55}>
              OpenPlay is a fork of DiPlay. It keeps DiPlay's CarPlay engine, connection and settings and gives them a calmer interface:
              a traced splash, a single device card that says what is happening, and a Settings screen with everything in one place.
            </Text>
          </Reveal>
          <Box display={{ base: "none", lg: "grid" }} placeItems="center" position="relative">
            <Box position="absolute" w="420px" h="420px" bg="radial-gradient(circle, rgba(61,255,110,0.16) 0%, transparent 62%)" />
            <Mark size={220} trace glow />
          </Box>
        </Grid>
      </Container>

      <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 24, md: 32 }}>
        <SectionHeading eyebrow="// lineage" title="Built on open source." lead="DiPlay combines xcertplay's CarPlay receiver with an interface adapted from DiAuto. OpenPlay forks DiPlay and gives it a new interface. Each project keeps its licence and notices." />
        <Lineage />
      </Container>

      <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 24, md: 32 }}>
        <SectionHeading eyebrow="// principles" title="What OpenPlay stands for." />
        <SimpleGrid columns={{ base: 1, md: 2 }} spacing={0} mt={10} borderTop="1px solid" borderColor="outline">
          {PRINCIPLES.map((p, i) => (
            <Reveal key={p.title} delay={i * 0.05}>
              <Flex gap={5} py={8} pr={{ md: 8 }} borderBottom="1px solid" borderColor="outline">
                <Box color="accent" pt={1}><p.icon size={28} weight="bold" /></Box>
                <Box>
                  <Heading as="h3" fontSize="2xl" fontWeight={600} letterSpacing="-0.02em" mb={2}>{p.title}</Heading>
                  <Text color="muted" fontSize="lg" lineHeight={1.55}>{p.text}</Text>
                </Box>
              </Flex>
            </Reveal>
          ))}
        </SimpleGrid>
      </Container>

      <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 24, md: 32 }}>
        <Reveal>
          <Flex gap={5} p={{ base: 6, md: 10 }} borderRadius="card" border="1px solid" borderColor="warning" bg="rgba(255,189,92,0.06)" direction={{ base: "column", md: "row" }}>
            <Box color="warning"><Warning size={32} weight="bold" /></Box>
            <Stack spacing={3}>
              <Heading as="h2" fontSize={{ base: "2xl", md: "3xl" }} fontWeight={600} letterSpacing="-0.02em">Read this first</Heading>
              <Text color="muted" fontSize="lg" lineHeight={1.6}>
                OpenPlay is not an Apple-certified product. Like DiPlay, it relies on an experimental accessory identity recovered from public
                Carlinkit firmware, not a newly provisioned MFi identity. Its private key is extractable, and acceptance after future iOS updates is
                not guaranteed. It is for community testing on compatible BYD head units; other brands are unsupported.
              </Text>
              <Text color="muted" fontSize="lg" lineHeight={1.6}>
                CarPlay and its icon belong to Apple Inc. No Apple or BYD affiliation or endorsement is implied.
              </Text>
            </Stack>
          </Flex>
        </Reveal>
      </Container>

      <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 24, md: 32 }}>
        <Reveal>
          <Flex direction={{ base: "column", md: "row" }} justify="space-between" align={{ md: "center" }} gap={6}>
            <Box>
              <Heading as="h2" fontSize={{ base: "3xl", md: "5xl" }} fontWeight={600} letterSpacing="-0.03em">Help shape it.</Heading>
              <Text color="muted" fontSize="lg" mt={2}>Test it in your car, report what you find, or send a pull request.</Text>
            </Box>
            <Flex gap={3} wrap="wrap">
              <Button as="a" href={GITHUB} target="_blank" rel="noreferrer" variant="primary" size="lg" leftIcon={<GithubLogo size={20} weight="bold" />}>GitHub</Button>
              <Button as={RouterLink} to="/docs/build-from-source" variant="ghostLine" size="lg" rightIcon={<ArrowRight weight="bold" />}>Build from source</Button>
            </Flex>
          </Flex>
        </Reveal>
      </Container>
    </>
  );
}
