import { Routes, Route, useLocation } from "react-router-dom";
import { motion } from "framer-motion";
import { useEffect } from "react";
import { Box } from "@chakra-ui/react";
import Header from "./components/Header.jsx";
import Footer from "./components/Footer.jsx";
import Home from "./pages/Home.jsx";
import Docs from "./pages/Docs.jsx";
import About from "./pages/About.jsx";

const MotionBox = motion(Box);

export default function App() {
  const location = useLocation();
  // A new page starts at the top; a docs section keeps its own scroll.
  const page = location.pathname.split("/")[1] || "home";
  useEffect(() => { window.scrollTo({ top: 0 }); }, [location.pathname]);

  return (
    <Box minH="100vh" display="flex" flexDirection="column">
      <Header />
      {/* Each page fades in on entry. There is no exit step: waiting on an exit (with shared
          layout animations inside the old page) could leave the next page unmounted. */}
      <MotionBox
        as="main"
        id="content"
        tabIndex={-1}
        outline="none"
        key={page}
        flex="1"
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.32, ease: [0.2, 0.8, 0.2, 1] }}
      >
        <Routes location={location}>
          <Route path="/" element={<Home />} />
          <Route path="/docs" element={<Docs />} />
          <Route path="/docs/:slug" element={<Docs />} />
          <Route path="/about" element={<About />} />
          <Route path="*" element={<Home />} />
        </Routes>
      </MotionBox>
      <Footer />
    </Box>
  );
}
