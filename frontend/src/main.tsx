import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@fontsource/inter/latin-400.css";
import "@fontsource/inter/latin-500.css";
import "@fontsource/inter/latin-600.css";
import "./index.css";
import "./polimento.css";
import App from "./App.tsx";
import { PublicRoute } from './PublicOrder';
import { AppBoundary } from "./PageState";
import PlatformAdmin from './PlatformAdmin';
import Help from './Help';
function rota() {
 const caminho=window.location.pathname;
 if(caminho==='/acompanhar') return <PublicRoute />;
 if(caminho==='/administracao') return <PlatformAdmin />;
 if(caminho==='/ajuda') return <main className="platform-login"><Help /></main>;
 if(caminho==='/') return <App />;
 return <main className="platform-login"><h1>Página não encontrada</h1><a href="/">Voltar ao início</a></main>;
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppBoundary>{rota()}</AppBoundary>
  </StrictMode>,
);
