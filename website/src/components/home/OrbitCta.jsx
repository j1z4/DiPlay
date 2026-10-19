import { Box, Button, Flex, Heading, Text } from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";
import { motion, useReducedMotion } from "framer-motion";
import { BookOpen, ChatCircleText, GithubLogo, MapTrifold, Microphone, MusicNotes, Phone, Radio } from "@phosphor-icons/react";
import Mark from "../Mark.jsx";
import { FrameSection } from "../Frame.jsx";
import { GITHUB } from "../../site.js";

const MotionBox = motion(Box);

// CarPlay's everyday apps, orbiting the mark on two rings.
const RINGS = [
  { r: 190, dur: 46, icons: [MapTrifold, MusicNotes, Phone] },
  { r: 300, dur: 70, icons: [ChatCircleText, Microphone, Radio, MapTrifold] },
];

function Ring({ r, dur, icons, reverse }) {
  const reduce = useReducedMotion();
  return (
    <MotionBox position="absolute" left="50%" top="50%" w={`${r * 2}px`} h={`${r * 2}px`} ml={`${-r}px`} mt={`${-r}px`}
      borderRadius="full" border="1px solid" borderColor="outline"
      animate={reduce ? undefined : { rotate: reverse ? -360 : 360 }} transition={{ duration: dur, repeat: Infinity, ease: "linear" }}>
      {icons.map((I, k) => {
        const a = (k / icons.length) * Math.PI * 2;
        return (
          <MotionBox key={k} position="absolute" left={`${r + Math.cos(a) * r - 22}px`} top={`${r + Math.sin(a) * r - 22}px`}
            w="44px" h="44px" borderRadius="14px" bg="surface" border="1px solid" borderColor="outline" display="grid" placeItems="center" color="muted"
            // Counter-rotate so each icon stays upright as the ring turns.
            animate={reduce ? undefined : { rotate: reverse ? 360 : -360 }} transition={{ duration: dur, repeat: Infinity, ease: "linear" }}>
            <I size={20} weight="bold" />
          </MotionBox>
        );
      })}
    </MotionBox>
  );
}

export default function OrbitCta() {
  return (
    <FrameSection inner={{ overflow: "hidden" }}>
      <Box position="relative" h={{ base: "560px", md: "640px" }}>
        <Box position="absolute" inset={0} bg="radial-gradient(circle at 50% 50%, rgba(61,255,110,0.12) 0%, transparent 45%)" />
        <Box position="absolute" inset={0} opacity={{ base: 0.5, md: 1 }}>
          {RINGS.map((ring, k) => <Ring key={k} {...ring} reverse={k % 2 === 1} />)}
          <Box position="absolute" left="50%" top="50%" transform="translate(-50%,-50%)" opacity={0.18}><Mark size={120} /></Box>
        </Box>
        <Flex position="relative" direction="column" align="center" justify="center" h="100%" textAlign="center" px={4} gap={6}>
          <Heading as="h2" fontSize={{ base: "4xl", md: "6xl" }} fontWeight={600} letterSpacing="-0.04em" lineHeight={1} maxW="760px">
            Your iPhone. Your car.<br />Your CarPlay.
          </Heading>
          <Text color="muted" fontSize={{ base: "lg", md: "xl" }} maxW="520px">Start with what you need, then connect your iPhone.</Text>
          <Flex gap={3} wrap="wrap" justify="center">
            <Button as={RouterLink} to="/docs/getting-started" variant="primary" size="lg" leftIcon={<BookOpen size={20} weight="bold" />}>Get started</Button>
            <Button as="a" href={GITHUB} target="_blank" rel="noreferrer" variant="ghostLine" size="lg" leftIcon={<GithubLogo size={20} weight="bold" />}>GitHub</Button>
          </Flex>
        </Flex>
      </Box>
    </FrameSection>
  );
}
