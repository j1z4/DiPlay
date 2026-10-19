import { Box, Container, Heading, Stack, Text } from "@chakra-ui/react";
import { Reveal } from "./Reveal.jsx";
import Shimmer from "./Shimmer.jsx";

/** A small square where a section rule meets a guide line. */
function Corner({ side }) {
  return (
    <Box position="absolute" top="-4px" {...{ [side]: "-4px" }} w="7px" h="7px" bg="bg" border="1px solid" borderColor="subtle" zIndex={2} />
  );
}

/**
 * A blueprint frame: a full-width rule on top, guide lines down both content edges and squares
 * where they cross. `band` darkens the section, for a product shot.
 */
export function FrameSection({ children, band = false, inner = {}, ...rest }) {
  return (
    <Box as="section" position="relative" borderTop="1px solid" borderColor="outline" bg={band ? "band" : undefined} {...rest}>
      <Container maxW="7xl" px={0} position="relative" borderX="1px solid" borderColor="outline" {...inner}>
        <Corner side="left" />
        <Corner side="right" />
        {children}
      </Container>
    </Box>
  );
}

/** "How it works": a glint sweeps through the eyebrow on a loop. */
function Eyebrow({ text }) {
  return <Shimmer fontSize="sm" fontWeight={550} letterSpacing="0.01em">{text}</Shimmer>;
}

/** A centred section heading: eyebrow, title, lead. */
export function CenteredHeading({ eyebrow, title, lead, ...rest }) {
  return (
    <Reveal {...rest}>
      <Stack spacing={4} align="center" textAlign="center" maxW="720px" mx="auto" px={4}>
        {eyebrow && <Eyebrow text={eyebrow} />}
        <Heading as="h2" fontSize={{ base: "3xl", md: "5xl" }} fontWeight={600} letterSpacing="-0.03em" lineHeight={1.05}>
          {title}
        </Heading>
        {lead && <Text fontSize={{ base: "lg", md: "xl" }} color="muted" lineHeight={1.5}>{lead}</Text>}
      </Stack>
    </Reveal>
  );
}
