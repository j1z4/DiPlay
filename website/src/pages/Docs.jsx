import { useEffect, useMemo, useState } from "react";
import {
  Box, Button, Container, Divider, Drawer, DrawerBody, DrawerContent, DrawerOverlay, Flex, Grid, Heading, Link, Stack, Text, useDisclosure,
} from "@chakra-ui/react";
import { Link as RouterLink, NavLink, useParams } from "react-router-dom";
import { LayoutGroup, motion } from "framer-motion";
import { useTitle } from "../site.js";
import { ArrowLeft, ArrowRight, List, PencilSimple } from "@phosphor-icons/react";
import { DOCS } from "../content/docs.js";
import { DocSection, Inline } from "../components/DocBlocks.jsx";
import { GITHUB } from "../site.js";

const MotionBox = motion(Box);

const GROUPS = DOCS.reduce((acc, d) => {
  (acc[d.group] ||= []).push(d);
  return acc;
}, {});

function Sidebar({ current, onPick }) {
  return (
    <LayoutGroup id="docs-nav">
      <Stack as="nav" spacing={7} aria-label="Documentation">
        {Object.entries(GROUPS).map(([group, pages]) => (
          <Box key={group}>
            <Text fontFamily="mono" fontSize="xs" color="subtle" textTransform="uppercase" letterSpacing="0.08em" mb={2} px={3}>{group}</Text>
            <Stack spacing={0.5}>
              {pages.map((p) => {
                const on = p.slug === current;
                return (
                  <Link key={p.slug} as={NavLink} to={`/docs/${p.slug}`} onClick={onPick} position="relative" isolation="isolate"
                    px={3} py={2} borderRadius="12px" fontWeight={on ? 600 : 500} color={on ? "bg" : "muted"} _hover={{ color: on ? "bg" : "text" }}
                    aria-current={on ? "page" : undefined}>
                    {/* The current page is lit white, as in the app's Settings rail. */}
                    {on && <MotionBox layoutId="docs-pill" position="absolute" inset={0} bg="text" borderRadius="12px" zIndex={-1}
                      transition={{ type: "spring", stiffness: 420, damping: 34 }} />}
                    {p.title}
                  </Link>
                );
              })}
            </Stack>
          </Box>
        ))}
      </Stack>
    </LayoutGroup>
  );
}

/** "On this page": the section nearest the top of the viewport is lit. */
function useActiveSection(ids) {
  const [active, setActive] = useState(ids[0]);
  useEffect(() => {
    setActive(ids[0]);
    const seen = new Map();
    const io = new IntersectionObserver((entries) => {
      entries.forEach((e) => seen.set(e.target.id, e.isIntersecting ? e.boundingClientRect.top : null));
      const visible = ids.filter((id) => seen.get(id) != null);
      if (visible.length) setActive(visible[0]);
    }, { rootMargin: "-100px 0px -55% 0px" });
    ids.forEach((id) => { const el = document.getElementById(id); if (el) io.observe(el); });
    return () => io.disconnect();
  }, [ids]);
  return active;
}

function OnThisPage({ doc }) {
  const ids = useMemo(() => doc.sections.map((s) => s.id), [doc]);
  const active = useActiveSection(ids);
  return (
    <Box>
      <Text fontFamily="mono" fontSize="xs" color="subtle" textTransform="uppercase" letterSpacing="0.08em" mb={3}>On this page</Text>
      <Stack spacing={0} borderLeft="2px solid" borderColor="outline">
        {doc.sections.map((s) => {
          const on = s.id === active;
          return (
            <Link key={s.id} href={`#${s.id}`} onClick={(e) => { e.preventDefault(); document.getElementById(s.id)?.scrollIntoView({ behavior: "smooth" }); }}
              position="relative" pl={4} py={1.5} fontSize="sm" color={on ? "text" : "muted"} fontWeight={on ? 600 : 400} _hover={{ color: "text" }}>
              {on && <MotionBox layoutId="toc-bar" position="absolute" left="-2px" top={0} bottom={0} w="2px" bg="accent" />}
              {s.heading}
            </Link>
          );
        })}
      </Stack>
    </Box>
  );
}

export default function Docs() {
  const { slug } = useParams();
  const index = Math.max(0, DOCS.findIndex((d) => d.slug === slug));
  const doc = DOCS[index];
  const prev = DOCS[index - 1];
  const next = DOCS[index + 1];
  const menu = useDisclosure();
  useTitle(`${doc.title} · OpenPlay docs`);

  return (
    <Container maxW="7xl" px={{ base: 4, md: 6 }} pt={{ base: 6, md: 12 }}>
      <Grid templateColumns={{ base: "1fr", lg: "230px minmax(0,1fr)", xl: "230px minmax(0,1fr) 200px" }} gap={{ base: 6, lg: 12 }}>
        <Box display={{ base: "none", lg: "block" }} position="sticky" top="100px" alignSelf="start" maxH="calc(100vh - 120px)" overflowY="auto" pb={8}>
          <Sidebar current={doc.slug} />
        </Box>

        <Box minW={0}>
          <Button display={{ base: "inline-flex", lg: "none" }} onClick={menu.onOpen} variant="ghostLine" leftIcon={<List size={18} weight="bold" />} mb={6}>
            Browse documentation
          </Button>

          {/* Enter only, no exit: the new page's sections must exist as soon as it is chosen, for "On this page". */}
          <MotionBox key={doc.slug} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.24, ease: [0.2, 0.8, 0.2, 1] }}>
              <Text fontFamily="mono" fontSize="sm" color="accent" mb={3}>{doc.group}</Text>
              <Heading as="h1" fontSize={{ base: "4xl", md: "6xl" }} fontWeight={650} letterSpacing="-0.04em" lineHeight={1}>{doc.title}</Heading>
              <Text fontSize={{ base: "lg", md: "xl" }} color="muted" mt={5} lineHeight={1.5}><Inline text={doc.summary} /></Text>
              <Divider my={10} borderColor="outline" />
              <Stack spacing={14}>
                {doc.sections.map((s) => <DocSection key={s.id} section={s} />)}
              </Stack>

              <Link href={`${GITHUB}/issues/new?title=${encodeURIComponent(`Docs: ${doc.title}`)}`} isExternal display="inline-flex" alignItems="center" gap={2} mt={14} color="muted" fontSize="sm" _hover={{ color: "text" }}>
                <PencilSimple size={16} weight="bold" /> Suggest a change to this page
              </Link>

              <Grid templateColumns={{ base: "1fr", sm: "1fr 1fr" }} gap={4} mt={8}>
                {prev ? <PageLink doc={prev} dir="prev" /> : <Box />}
                {next && <PageLink doc={next} dir="next" />}
              </Grid>
          </MotionBox>
        </Box>

        <Box display={{ base: "none", xl: "block" }} position="sticky" top="100px" alignSelf="start">
          <OnThisPage doc={doc} />
        </Box>
      </Grid>

      <Drawer isOpen={menu.isOpen} onClose={menu.onClose} placement="left">
        <DrawerOverlay backdropFilter="blur(6px)" />
        <DrawerContent bg="bg" maxW="300px">
          <DrawerBody py={6} px={3}>
            <Sidebar current={doc.slug} onPick={menu.onClose} />
          </DrawerBody>
        </DrawerContent>
      </Drawer>
    </Container>
  );
}

function PageLink({ doc, dir }) {
  const next = dir === "next";
  return (
    <Link as={RouterLink} to={`/docs/${doc.slug}`} p={5} border="1px solid" borderColor="outline" borderRadius="18px" bg="surface" role="group"
      textAlign={next ? "right" : "left"} transition="all 160ms" _hover={{ borderColor: "accent", transform: "translateY(-2px)" }}>
      <Flex align="center" gap={2} justify={next ? "flex-end" : "flex-start"} color="subtle" fontSize="sm" mb={1}>
        {!next && <ArrowLeft size={14} weight="bold" />}
        {next ? "Next" : "Previous"}
        {next && <ArrowRight size={14} weight="bold" />}
      </Flex>
      <Text fontWeight={600} fontSize="lg">{doc.title}</Text>
    </Link>
  );
}
