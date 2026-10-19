import { Fragment } from "react";
import { Box, Code, Flex, Heading, Link, ListItem, OrderedList, Stack, Table, Tbody, Td, Text, Th, Thead, Tr, UnorderedList } from "@chakra-ui/react";
import { Info, Warning } from "@phosphor-icons/react";

// Inline markup in the docs content: **bold**, `code` and [label](https://url).
const INLINE = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\(https?:\/\/[^)\s]+\))/g;

export function Inline({ text }) {
  return text.split(INLINE).map((part, i) => {
    if (part.startsWith("**")) return <Text as="strong" key={i} fontWeight={650} color="text">{part.slice(2, -2)}</Text>;
    if (part.startsWith("`")) return <Code key={i} fontSize="0.9em" px={1.5} py={0.5} borderRadius="md" bg="surface2" color="text">{part.slice(1, -1)}</Code>;
    const link = part.match(/^\[([^\]]+)\]\((.+)\)$/);
    if (link) {
      return (
        <Link key={i} href={link[2]} isExternal color="accent" fontWeight={550} textDecoration="underline" textUnderlineOffset="3px" textDecorationColor="accentSoft"
          _hover={{ textDecorationColor: "accent" }}>
          {link[1]}
        </Link>
      );
    }
    return <Fragment key={i}>{part}</Fragment>;
  });
}

function Note({ tone, title, text }) {
  const warning = tone === "warning";
  const Icon = warning ? Warning : Info;
  return (
    <Flex gap={3} p={5} borderRadius="18px" border="1px solid" borderColor={warning ? "warning" : "outline"} bg={warning ? "rgba(255,189,92,0.08)" : "accentSoft"}>
      <Box color={warning ? "warning" : "accent"} pt="2px" flex="none"><Icon size={20} weight="bold" /></Box>
      <Box>
        {title && <Text fontWeight={650} mb={1}>{title}</Text>}
        <Text color="muted" lineHeight={1.6}><Inline text={text} /></Text>
      </Box>
    </Flex>
  );
}

function Block({ block }) {
  switch (block.type) {
    case "p":
      return <Text color="muted" fontSize="lg" lineHeight={1.7}><Inline text={block.text} /></Text>;
    case "list":
      return (
        <UnorderedList spacing={2} color="muted" fontSize="lg" lineHeight={1.6} pl={1} sx={{ "li::marker": { color: "var(--chakra-colors-accent)" } }}>
          {block.items.map((t, i) => <ListItem key={i}><Inline text={t} /></ListItem>)}
        </UnorderedList>
      );
    case "steps":
      return (
        <OrderedList spacing={0} ml={0} styleType="none" sx={{ counterReset: "step" }}>
          {block.items.map((t, i) => (
            <ListItem key={i} display="flex" gap={4} py={3} borderTop={i ? "1px solid" : "none"} borderColor="outline" color="muted" fontSize="lg" lineHeight={1.6}>
              <Box flex="none" w="30px" h="30px" mt="1px" borderRadius="full" bg="text" color="bg" display="grid" placeItems="center" fontFamily="mono" fontSize="sm" fontWeight={600}>
                {i + 1}
              </Box>
              <Box><Inline text={t} /></Box>
            </ListItem>
          ))}
        </OrderedList>
      );
    case "code":
      return (
        <Box position="relative" borderRadius="18px" bg="#0b0d11" border="1px solid" borderColor="whiteAlpha.100" overflow="hidden">
          <Text position="absolute" top={2} right={3} fontFamily="mono" fontSize="xs" color="whiteAlpha.500">{block.lang}</Text>
          <Box as="pre" p={5} pt={7} overflowX="auto" fontFamily="mono" fontSize="sm" lineHeight={1.7} color="#e6e9ef">
            <code>{block.code}</code>
          </Box>
        </Box>
      );
    case "note":
      return <Note {...block} />;
    case "table":
      return (
        <Box overflowX="auto" border="1px solid" borderColor="outline" borderRadius="18px">
          <Table size="md">
            <Thead bg="surface2">
              <Tr>{block.head.map((h) => <Th key={h} color="text" fontFamily="body" textTransform="none" fontSize="sm" letterSpacing="0" borderColor="outline">{h}</Th>)}</Tr>
            </Thead>
            <Tbody>
              {block.rows.map((row, r) => (
                <Tr key={r}>
                  {row.map((cell, c) => (
                    <Td key={c} color={c ? "muted" : "text"} fontWeight={c ? 400 : 600} borderColor="outline" verticalAlign="top" minW={c ? "220px" : "120px"}>
                      <Inline text={cell} />
                    </Td>
                  ))}
                </Tr>
              ))}
            </Tbody>
          </Table>
        </Box>
      );
    default:
      return null;
  }
}

export function DocSection({ section }) {
  return (
    <Box as="section" id={section.id} scrollMarginTop="110px">
      <Heading as="h2" fontSize={{ base: "2xl", md: "3xl" }} fontWeight={600} letterSpacing="-0.02em" mb={5}>
        {section.heading}
      </Heading>
      <Stack spacing={5}>
        {section.blocks.map((b, i) => <Block key={i} block={b} />)}
      </Stack>
    </Box>
  );
}
