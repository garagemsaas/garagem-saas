import { cloneElement, isValidElement, useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { statuses } from "./model";
import type { Status } from "./model";

import { Icon } from "./icons";
export { Icon } from "./icons";
export function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark">
        <Icon name="vehicles" size={25} />
      </span>
      <span>
        garagem<span className="brand-sub">GESTÃO DE OFICINAS</span>
      </span>
    </div>
  );
}
export function Badge({ status }: { status: Status }) {
  return (
    <span className={`badge ${status.toLowerCase()}`}>
      <Icon name={status === "PRONTO" ? "good" : status === "AGUARDANDO_APROVACAO" || status === "AGUARDANDO_PECA" ? "clock" : "work"} size={14} />
      {statuses[status]}
    </span>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <Icon name="orders" size={30} />
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  const id = useId();
  return (
    <label className="field">
      <span id={id}>{label}</span>
      {isValidElement<Record<string, unknown>>(children) &&
      typeof children.type === "string" &&
      ["input", "select", "textarea"].includes(children.type)
        ? cloneElement(children, {
            "aria-labelledby": id,
            "aria-describedby": hint ? `${id}-hint` : undefined,
          })
        : children}
      {hint && <small id={`${id}-hint`}>{hint}</small>}
    </label>
  );
}
export function Drawer({
  title,
  children,
  close,
  wide = false,
  error,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
  wide?: boolean;
  error?: string;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    ref.current?.showModal();
    return () => {
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      className={`drawer ${wide ? "wide" : ""}`}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
    >
      <header>
        <h2 id={titleId}>{title}</h2>
        <button
          className="icon-button"
          onClick={close}
          aria-label="Fechar painel"
        >
          <Icon name="close" />
        </button>
      </header>
      <div className="drawer-body">{error && <p role="alert" className="error">{error}</p>}{children}</div>
    </dialog>
  );
}
export function Search({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (s: string) => void;
  placeholder: string;
}) {
  return (
    <div className="search">
      <Icon name="search" />
      <input
        aria-label={placeholder}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {value && (
        <button
          className="icon-button"
          aria-label="Limpar busca"
          onClick={() => onChange("")}
        >
          <Icon name="close" size={16} />
        </button>
      )}
    </div>
  );
}
export function Pager({
  total,
  page,
  setPage,
}: {
  total: number;
  page: number;
  setPage: (n: number) => void;
}) {
  return (
    <footer className="pager">
      <span>
        {total
          ? `${page * 10 + 1}–${Math.min((page + 1) * 10, total)} de ${total} registros`
          : "0 registros"}
      </span>
      <div>
        <button
          disabled={!page}
          onClick={() => setPage(page - 1)}
          aria-label="Página anterior"
        >
          Anterior
        </button>
        <span>Página {page + 1}</span>
        <button
          disabled={(page + 1) * 10 >= total}
          onClick={() => setPage(page + 1)}
          aria-label="Próxima página"
        >
          Próxima
        </button>
      </div>
    </footer>
  );
}
