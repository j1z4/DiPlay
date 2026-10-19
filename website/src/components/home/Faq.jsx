import { Accordion, AccordionButton, AccordionIcon, AccordionItem, AccordionPanel, Box, Button, Flex, Text } from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";
import { CenteredHeading, FrameSection } from "../Frame.jsx";
import { Inline } from "../DocBlocks.jsx";
import { ISSUES } from "../../site.js";

// Answers come from the README and docs/; inline markup as in the documentation content.
const FAQ = [
  { q: "Do I need a dongle, a Mac or an account?",
    a: "No. OpenPlay is installed on the car itself. No jailbreak, dongle, Mac, account or authentication server is required." },
  { q: "Which cars and head units does it support?",
    a: "OpenPlay focuses on **BYD** cars. It may work on other brands, but they are unsupported. The head unit must allow APK installation and run Android 7.0 or later; Android 7.0 to 8.1 is not yet confirmed on a vehicle." },
  { q: "Is OpenPlay Apple-certified?",
    a: "No. Like DiPlay, it uses an experimental accessory identity recovered from public Carlinkit firmware, not a newly provisioned MFi identity. Acceptance after future iOS updates is not guaranteed." },
  { q: "Does it need ADB?",
    a: "Core CarPlay does not. Optional dashboard, battery, wheel-speed and parked-video features do." },
  { q: "How does wireless CarPlay connect?",
    a: "Over the car's built-in hotspot (the default), Wi-Fi Direct, or an existing Wi-Fi network with the iPhone and head unit on the same LAN. A USB data port works too." },
  { q: "Where do diagnostic reports go?",
    a: "Use **Settings → Diagnostics → Save diagnostic report**. Reports are saved on the car and shared only if you choose to share them." },
  { q: "How is OpenPlay different from DiPlay?",
    a: "It keeps DiPlay's CarPlay engine, connection and settings, and replaces the interface: a traced splash, one device card with a clear status, and a Settings screen with everything in one place." },
];

export default function Faq() {
  return (
    <FrameSection>
      <Box py={{ base: 16, md: 24 }}>
        <CenteredHeading eyebrow="FAQs" title="Frequently asked questions" lead="The short answers. The documentation has the long ones." />
        <Flex gap={3} justify="center" mt={8} wrap="wrap">
          <Button as={RouterLink} to="/docs" variant="primary">Read the docs</Button>
          <Button as="a" href={ISSUES} target="_blank" rel="noreferrer" variant="ghostLine">Report a problem</Button>
        </Flex>
      </Box>
      <Accordion allowToggle borderTop="1px solid" borderColor="outline">
        {FAQ.map((f) => (
          <AccordionItem key={f.q} borderColor="outline" _last={{ borderBottomWidth: 0 }}>
            <AccordionButton px={{ base: 5, md: 8 }} py={5} _hover={{ bg: "surface" }} _expanded={{ bg: "surface" }}>
              <Text flex="1" textAlign="left" fontWeight={550} fontSize={{ base: "md", md: "lg" }}>{f.q}</Text>
              <AccordionIcon color="muted" />
            </AccordionButton>
            <AccordionPanel px={{ base: 5, md: 8 }} pb={6} bg="surface">
              <Text color="muted" fontSize="lg" lineHeight={1.65} maxW="820px"><Inline text={f.a} /></Text>
            </AccordionPanel>
          </AccordionItem>
        ))}
      </Accordion>
    </FrameSection>
  );
}
