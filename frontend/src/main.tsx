import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource/inter/latin-400.css";
import "@fontsource/inter/latin-500.css";
import "@fontsource/inter/latin-600.css";
import "./index.css";
import App from "./App.tsx";
import { PublicRoute } from './PublicOrder';
import { AppBoundary } from "./PageState";
import SitePage from './site/SitePage';

/**
 * Três destinos, decididos pelo caminho: o acompanhamento público de uma OS, o site de apresentação
 * de uma empresa e o sistema. Não há institucional dentro do produto — o que é apresentação mora no
 * site de cada empresa, e o sistema serve para trabalhar.
 */
function rota() {
  const caminho = window.location.pathname;
  if (caminho === '/acompanhar') return <PublicRoute />;
  const site = /^\/site\/([a-z0-9-]{3,80})\/?$/.exec(caminho);
  if (site) return <SitePage slug={site[1]} />;
  return <App />;
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppBoundary>{rota()}</AppBoundary>
  </StrictMode>,
);
