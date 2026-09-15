import { Component } from "react";
import type { ErrorInfo, ReactNode } from "react";
import { Icon } from "./icons";

export function PageState({ state, title, children, retry }: {
  state: "loading" | "empty" | "error"; title: string; children?: ReactNode; retry?: () => void;
}) {
  return <section className={`page-state ${state}`} role={state === "error" ? "alert" : "status"} aria-busy={state === "loading"}>
    <Icon name={state === "error" ? "critical" : state === "loading" ? "clock" : "orders"} size={28} />
    <h2>{title}</h2>{children && <p>{children}</p>}
    {retry && <button onClick={retry}>Tentar novamente</button>}
  </section>;
}

export class AppBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) console.error("Falha ao renderizar a interface", error, info.componentStack);
  }
  render() {
    return this.state.failed ? <main className="premium-main"><PageState state="error" title="Não foi possível abrir esta tela" retry={() => this.setState({ failed: false })}>
      Tente abrir a interface novamente. Recarregar exige um novo login; os registros salvos permanecem na oficina.
    </PageState></main> : this.props.children;
  }
}
