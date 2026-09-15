import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource/inter/latin-400.css";
import "@fontsource/inter/latin-500.css";
import "@fontsource/inter/latin-600.css";
import "./index.css";
import App from "./App.tsx";
import { PublicRoute } from './PublicOrder';
import { AppBoundary } from "./PageState";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppBoundary>{window.location.pathname === '/acompanhar' ? <PublicRoute /> : <App />}</AppBoundary>
  </StrictMode>,
);
