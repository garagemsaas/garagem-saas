import { cloneElement, isValidElement, useEffect, useId, useRef } from "react";
import type { ReactNode } from "react";
import { statuses } from "./model";
import type { Status } from "./model";

export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    orders: (
      <>
        <rect x="5" y="4" width="14" height="17" rx="2" />
        <path d="M9 4V2h6v2M9 9h6M9 13h6M9 17h4" />
      </>
    ),
    clients: (
      <>
        <circle cx="10" cy="7" r="3" />
        <path d="M3 21v-3a7 7 0 0 1 14 0v3M17 4a3 3 0 0 1 0 6M21 21v-3a7 7 0 0 0-3-6" />
      </>
    ),
    vehicles: (
      <>
        <path d="m3 10 2-6h14l2 6v9h-3v-3H6v3H3zM3 10h18M6 13h2M16 13h2" />
      </>
    ),
    team: (
      <>
        <circle cx="12" cy="7" r="3" />
        <path d="M5 21v-3a7 7 0 0 1 14 0v3M3 5v6M0 8h6" />
      </>
    ),
    search: (
      <>
        <circle cx="10" cy="10" r="6" />
        <path d="m15 15 5 5" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    arrow: <path d="m9 5 7 7-7 7" />,
    back: <path d="m14 5-7 7 7 7M7 12h14" />,
    logout: (
      <>
        <path d="M9 4H4v16h5M9 12h12m-4-4 4 4-4 4" />
      </>
    ),
    close: <path d="m6 6 12 12M6 18 18 6" />,
    photo: (
      <>
        <rect x="3" y="5" width="18" height="15" rx="2" />
        <path d="m8 5 2-3h4l2 3" />
        <circle cx="12" cy="12" r="4" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.orders}
    </svg>
  );
}
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
      <span className="dot" />
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
}: {
  title: string;
  children: ReactNode;
  close: () => void;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    ref.current?.showModal();
    return () => {
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className={`drawer ${wide ? "wide" : ""}`}
      onCancel={(e) => {
        e.preventDefault();
        close();
      }}
    >
      <header>
        <h2>{title}</h2>
        <button
          className="icon-button"
          onClick={close}
          aria-label="Fechar painel"
        >
          <Icon name="close" />
        </button>
      </header>
      <div className="drawer-body">{children}</div>
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
