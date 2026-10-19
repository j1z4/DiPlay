import {
  Box, Container, Flex, HStack, IconButton, Link, Text, useColorMode, useDisclosure,
  Drawer, DrawerOverlay, DrawerContent, DrawerBody, VStack, Button,
} from "@chakra-ui/react";
import { NavLink, Link as RouterLink, useLocation } from "react-router-dom";
import { motion, LayoutGroup } from "framer-motion";
import { GithubLogo, Moon, Sun, List, X, ArrowUpRight } from "@phosphor-icons/react";
import Mark from "./Mark.jsx";
import { NAV, GITHUB, BASE_VERSION } from "../site.js";

const MotionBox = motion(Box);

function isCurrent(pathname, to) {
  return to === "/" ? pathname === "/" : pathname.startsWith(to);
}

export function Wordmark() {
  return (
    <Link as={RouterLink} to="/" display="flex" alignItems="center" gap={2.5} aria-label="OpenPlay home">
      <Mark size={26} />
      <Text fontSize="xl" fontWeight={650} letterSpacing="-0.03em">OpenPlay</Text>
    </Link>
  );
}

export default function Header() {
  const { colorMode, toggleColorMode } = useColorMode();
  const { pathname } = useLocation();
  const menu = useDisclosure();

  return (
    <>
      {/* Announcement bar */}
      <Box bg="surface2" borderBottom="1px solid" borderColor="outline">
        <Container maxW="7xl" py={2} px={{ base: 4, md: 6 }}>
          <Link href={GITHUB} isExternal fontSize="sm" color="muted" display="inline-flex" alignItems="center" gap={1.5} _hover={{ color: "text" }}>
            <Box as="span" w="6px" h="6px" borderRadius="full" bg="accent" />
            OpenPlay is a fork of DiPlay {BASE_VERSION}, public preview. Follow it on GitHub
            <ArrowUpRight size={14} weight="bold" />
          </Link>
        </Container>
      </Box>

      <Box
        as="header"
        position="sticky"
        top={0}
        zIndex={20}
        bg={colorMode === "dark" ? "rgba(0,0,0,0.72)" : "rgba(246,247,249,0.78)"}
        backdropFilter="saturate(160%) blur(14px)"
        borderBottom="1px solid"
        borderColor="outline"
      >
        <Link href="#content" position="absolute" left="-999px" _focus={{ left: 4, top: 3, bg: "surface", px: 3, py: 2, borderRadius: "md" }}>
          Skip to content
        </Link>
        <Container maxW="7xl" px={{ base: 4, md: 6 }}>
          <Flex h="68px" align="center" justify="space-between">
            <Wordmark />

            <LayoutGroup>
              <HStack as="nav" spacing={1} display={{ base: "none", md: "flex" }} aria-label="Main">
                {NAV.map((item) => {
                  const current = isCurrent(pathname, item.to);
                  return (
                    <Link
                      key={item.to}
                      as={NavLink}
                      to={item.to}
                      position="relative"
                      isolation="isolate"
                      px={4}
                      py={2}
                      fontWeight={550}
                      color={current ? "bg" : "muted"}
                      _hover={{ color: current ? "bg" : "text" }}
                      aria-current={current ? "page" : undefined}
                    >
                      {/* The current page is lit white, like the app's selected row. */}
                      {current && (
                        <MotionBox
                          layoutId="nav-pill"
                          position="absolute"
                          inset={0}
                          bg="text"
                          borderRadius="full"
                          zIndex={-1}
                          transition={{ type: "spring", stiffness: 420, damping: 34 }}
                        />
                      )}
                      {item.label}
                    </Link>
                  );
                })}
              </HStack>
            </LayoutGroup>

            <HStack spacing={2}>
              <Button
                as="a"
                href={GITHUB}
                target="_blank"
                rel="noreferrer"
                variant="ghostLine"
                size="sm"
                h="40px"
                leftIcon={<GithubLogo size={18} weight="bold" />}
                display={{ base: "none", sm: "inline-flex" }}
              >
                GitHub
              </Button>
              <IconButton
                aria-label={colorMode === "dark" ? "Use light mode" : "Use dark mode"}
                icon={colorMode === "dark" ? <Sun size={18} weight="bold" /> : <Moon size={18} weight="bold" />}
                onClick={toggleColorMode}
                variant="ghostLine"
                size="sm"
                h="40px"
                w="40px"
              />
              <IconButton
                aria-label="Open menu"
                icon={<List size={20} weight="bold" />}
                onClick={menu.onOpen}
                variant="ghostLine"
                size="sm"
                h="40px"
                w="40px"
                display={{ base: "inline-flex", md: "none" }}
              />
            </HStack>
          </Flex>
        </Container>
      </Box>

      <Drawer isOpen={menu.isOpen} onClose={menu.onClose} placement="top">
        <DrawerOverlay backdropFilter="blur(6px)" />
        <DrawerContent bg="bg" borderBottom="1px solid" borderColor="outline">
          <DrawerBody px={4} py={4}>
            <Flex justify="space-between" align="center" mb={4}>
              <Wordmark />
              <IconButton aria-label="Close menu" icon={<X size={20} weight="bold" />} onClick={menu.onClose} variant="ghostLine" size="sm" h="40px" w="40px" />
            </Flex>
            <VStack align="stretch" spacing={1}>
              {NAV.map((item) => {
                const current = isCurrent(pathname, item.to);
                return (
                  <Link
                    key={item.to}
                    as={NavLink}
                    to={item.to}
                    onClick={menu.onClose}
                    px={4}
                    py={3}
                    borderRadius="control"
                    fontSize="lg"
                    fontWeight={600}
                    bg={current ? "text" : "transparent"}
                    color={current ? "bg" : "text"}
                  >
                    {item.label}
                  </Link>
                );
              })}
              <Link href={GITHUB} isExternal px={4} py={3} fontSize="lg" fontWeight={600} display="flex" alignItems="center" gap={2}>
                <GithubLogo size={20} weight="bold" /> GitHub
              </Link>
            </VStack>
          </DrawerBody>
        </DrawerContent>
      </Drawer>
    </>
  );
}
