import { Box, Container, Flex, Link, SimpleGrid, Stack, Text } from "@chakra-ui/react";
import { Link as RouterLink } from "react-router-dom";
import { Wordmark } from "./Header.jsx";
import { NAV, GITHUB, ISSUES, UPSTREAM } from "../site.js";

function Column({ title, children }) {
  return (
    <Stack spacing={3}>
      <Text fontFamily="mono" fontSize="xs" color="subtle" textTransform="uppercase" letterSpacing="0.08em">{title}</Text>
      {children}
    </Stack>
  );
}

const linkStyle = { color: "muted", _hover: { color: "text" }, w: "fit-content" };

export default function Footer() {
  return (
    <Box as="footer" borderTop="1px solid" borderColor="outline">
      <Container maxW="7xl" px={{ base: 4, md: 6 }} py={14}>
        <SimpleGrid columns={{ base: 1, sm: 2, md: 4 }} spacing={10}>
          <Stack spacing={4}>
            <Wordmark />
            <Text color="muted" maxW="260px">CarPlay, that you own. A fork of DiPlay for compatible BYD Android head units.</Text>
          </Stack>
          <Column title="Discover more">
            {NAV.map((item) => <Link key={item.to} as={RouterLink} to={item.to} {...linkStyle}>{item.label}</Link>)}
          </Column>
          <Column title="Project">
            <Link href={GITHUB} isExternal {...linkStyle}>GitHub</Link>
            <Link href={ISSUES} isExternal {...linkStyle}>Report a problem</Link>
            <Link as={RouterLink} to="/docs/build-from-source" {...linkStyle}>Build from source</Link>
          </Column>
          <Column title="Upstream">
            <Link href={UPSTREAM} isExternal {...linkStyle}>DiPlay</Link>
            <Link href={`${UPSTREAM}/releases`} isExternal {...linkStyle}>DiPlay releases</Link>
            <Link href="https://github.com/shilapi/xcertplay" isExternal {...linkStyle}>xcertplay</Link>
            <Link href="https://github.com/shihabal3amri/DiAuto" isExternal {...linkStyle}>DiAuto</Link>
          </Column>
        </SimpleGrid>
        <Flex mt={14} pt={6} borderTop="1px solid" borderColor="outline" gap={3} direction={{ base: "column", md: "row" }} justify="space-between" color="subtle" fontSize="sm">
          <Text>© 2026 OpenPlay contributors · GPL-3.0</Text>
          <Text>CarPlay belongs to Apple Inc. No Apple or BYD affiliation or endorsement is implied.</Text>
        </Flex>
      </Container>
    </Box>
  );
}
