import { Box, Heading, Text, Stack } from "@chakra-ui/react";
import { motion, useReducedMotion } from "framer-motion";
import Shimmer from "./Shimmer.jsx";

const MotionBox = motion(Box);

/** Fades and lifts its children in once they scroll into view. */
export function Reveal({ delay = 0, y = 24, children, ...rest }) {
  const reduce = useReducedMotion();
  return (
    <MotionBox
      initial={reduce ? false : { opacity: 0, y }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ type: "spring", stiffness: 140, damping: 22, delay }}
      {...rest}
    >
      {children}
    </MotionBox>
  );
}

/** A section heading with the rule beneath it, and an optional eyebrow and lead. */
export function SectionHeading({ eyebrow, title, lead, ...rest }) {
  return (
    <Reveal {...rest}>
      <Stack spacing={4} maxW="760px">
        {eyebrow && <Box><Shimmer fontFamily="mono" fontSize="sm" letterSpacing="0.02em">{eyebrow}</Shimmer></Box>}
        <Heading as="h2" fontSize={{ base: "3xl", md: "5xl" }} fontWeight={600} letterSpacing="-0.03em" lineHeight={1.05}>
          {title}
        </Heading>
        <Box h="2px" w="56px" bg="accent" borderRadius="full" />
        {lead && <Text fontSize={{ base: "lg", md: "xl" }} color="muted" lineHeight={1.5}>{lead}</Text>}
      </Stack>
    </Reveal>
  );
}
