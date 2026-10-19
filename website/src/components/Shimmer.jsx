import { Text } from "@chakra-ui/react";
import { keyframes } from "@emotion/react";

// A glint that sweeps through the letters and loops, like a CLI's "thinking" line.
const sweep = keyframes`
  from { background-position: 150% 0; }
  to { background-position: -50% 0; }
`;

/**
 * Small label text with a moving highlight: the base colour is muted, and a band of the
 * accent colour (brightening to white at its centre) travels left to right. Reduced motion
 * shows the text still, in the muted colour.
 */
export default function Shimmer({ children, duration = 2.4, ...rest }) {
  return (
    <Text
      as="span"
      display="inline-block"
      color="muted"
      bgImage="linear-gradient(90deg, var(--chakra-colors-muted) 0%, var(--chakra-colors-muted) 38%, var(--chakra-colors-accent) 46%, var(--chakra-colors-text) 50%, var(--chakra-colors-accent) 54%, var(--chakra-colors-muted) 62%, var(--chakra-colors-muted) 100%)"
      bgSize="250% 100%"
      bgClip="text"
      sx={{
        WebkitTextFillColor: "transparent",
        animation: `${sweep} ${duration}s linear infinite`,
        // Still: a flat muted fill, with no glint showing.
        "@media (prefers-reduced-motion: reduce)": {
          animation: "none",
          backgroundImage: "linear-gradient(var(--chakra-colors-muted), var(--chakra-colors-muted))",
        },
      }}
      {...rest}
    >
      {children}
    </Text>
  );
}
