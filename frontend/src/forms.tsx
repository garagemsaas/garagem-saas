import { useRef, useState } from "react";
import { ApiError } from './api';
import { FormErrors } from './form-context';
import type { FormEvent, ReactNode } from "react";
import { Empty, Field, Icon } from "./ui";
import { roles, uid, val } from "./model";
import type { Client, Vehicle, User, Order, Role } from "./model";

export function Form({
  children,
  save,
  close,
  submit = "Salvar",
  note,
  confirmation,
}: {
  children: ReactNode;
  save: (f: FormData) => void | Promise<void>;
  close: () => void | Promise<void>;
  submit?: string;
  note?: string;
  confirmation?: string;
}) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState<FormData | null>(null);
  const ref = useRef<HTMLFormElement>(null);
  const saving = useRef(false);
  async function persist(data: FormData) {
    if (saving.current) return;
    saving.current = true;
    setBusy(true); setError(''); setFieldErrors({});
    try {
      await save(data);
      if (ref.current) delete ref.current.dataset.dirty;
      setPending(null);
    } catch (err) {
      setPending(null);
      setError(err instanceof Error ? err.message : 'Confira os dados informados.');
      if (err instanceof ApiError) setFieldErrors(Object.fromEntries(err.errors.map(item => [item.field, item.message])));
      requestAnimationFrame(() => {
        const target = ref.current?.querySelector<HTMLElement>('[aria-invalid="true"]') ?? ref.current?.querySelector<HTMLElement>('[role="alert"]');
        target?.focus();
      });
    } finally { saving.current = false; setBusy(false); }
  }
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy || pending) return;
    const data = new FormData(e.currentTarget);
    if (confirmation) { setPending(data); return; }
    await persist(data);
  }
  return (
    <form ref={ref} onSubmit={onSubmit} aria-busy={busy} onInput={() => { if (ref.current) ref.current.dataset.dirty = 'true'; }}>
      {note && <p className="form-note">{note}</p>}
      <FormErrors.Provider value={fieldErrors}>
        <fieldset className="form-fields" disabled={busy || Boolean(pending)}>{children}</fieldset>
      </FormErrors.Provider>
      {error && (
        <p role="alert" tabIndex={-1} className="error">
          {error}
        </p>
      )}
      {pending && <section className="confirmation" role="alert">
        <h3>Confirmar registro?</h3><p>{confirmation}</p>
        <div className="form-actions">
          <button type="button" disabled={busy} onClick={() => setPending(null)}>Revisar dados</button>
          <button type="button" className="primary" disabled={busy} onClick={() => void persist(pending)}>{busy ? 'Salvando…' : 'Confirmar e salvar'}</button>
        </div>
      </section>}
      {!pending && <>
      <div className="form-actions">
        <button type="button" disabled={busy} onClick={() => { if (ref.current?.dataset.dirty !== 'true' || window.confirm('Descartar as alterações não salvas?')) void close(); }}>
          Cancelar
        </button>
        <button className="primary" type="submit" disabled={busy}>
          {busy ? 'Salvando…' : submit}
        </button>
      </div>
      </>}
    </form>
  );
}
export function ClientForm({
  current,
  save,
  close,
}: {
  current?: Client;
  save: (v: Client) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  return (
    <Form
      close={close}
      save={(f) => {
        const nome = val(f, "nome"),
          telefone = val(f, "telefone");
        if (!nome || !telefone) throw Error("Preencha nome e telefone.");
        return save({
          id: current?.id || uid(),
          nome,
          telefone,
          email: val(f, "email"),
          revisao: current?.revisao ?? 0,
        });
      }}
    >
      <Field label="Nome completo *">
        <input
          name="nome"
          required
          maxLength={160}
          defaultValue={current?.nome}
          autoComplete="name"
        />
      </Field>
      <Field label="Telefone *">
        <input
          name="telefone"
          type="tel"
          required
          maxLength={30}
          defaultValue={current?.telefone}
          placeholder="(11) 99999-9999"
          autoComplete="tel"
        />
      </Field>
      <Field label="E-mail">
        <input
          name="email"
          type="email"
          maxLength={254}
          defaultValue={current?.email}
          autoComplete="email"
        />
      </Field>
    </Form>
  );
}
export function VehicleForm({
  current,
  clients,
  vehicles,
  save,
  close,
}: {
  current?: Vehicle;
  clients: Client[];
  vehicles: Vehicle[];
  save: (v: Vehicle) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  if (!clients.length) return <><Empty title="Cadastre um cliente primeiro">Todo veículo precisa estar vinculado ao seu proprietário. Abra Clientes e cadastre o contato antes de continuar.</Empty><button onClick={close}>Voltar</button></>;
  return (
    <Form
      close={close}
      save={(f) => {
        const placa = val(f, "placa").replace(/[- ]/g, "").toUpperCase();
        if (vehicles.some((v) => v.placa === placa && v.id !== current?.id))
          throw Error("Esta placa já está cadastrada na oficina.");
        if (!val(f, "marca") || !val(f, "modelo") || !val(f, "cor"))
          throw Error("Preencha marca, modelo e cor.");
        return save({
          id: current?.id || uid(),
          clienteId: val(f, "clienteId"),
          placa,
          marca: val(f, "marca"),
          modelo: val(f, "modelo"),
          ano: Number(f.get("ano")),
          km: Number(f.get("km")),
          cor: val(f, "cor"),
          revisao: current?.revisao ?? 0,
        });
      }}
    >
      <Field label="Cliente *">
        <select
          name="clienteId"
          required
          defaultValue={current?.clienteId || ""}
        >
          <option value="" disabled>
            Selecione o proprietário
          </option>
          {clients.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nome}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Placa *">
        <input
          name="placa"
          className="plate-input"
          required
          pattern="[A-Za-z]{3}[- ]?[0-9][A-Za-z0-9][0-9]{2}"
          defaultValue={current?.placa}
          maxLength={8}
          placeholder="ABC1D23"
        />
      </Field>
      <div className="form-grid">
        <Field label="Marca *">
          <input
            name="marca"
            required
            maxLength={80}
            defaultValue={current?.marca}
          />
        </Field>
        <Field label="Modelo *">
          <input
            name="modelo"
            required
            maxLength={100}
            defaultValue={current?.modelo}
          />
        </Field>
        <Field label="Ano *">
          <input
            name="ano"
            type="number"
            min={1886}
            max={2200}
            required
            defaultValue={current?.ano}
          />
        </Field>
        <Field label="Cor *">
          <input
            name="cor"
            required
            maxLength={60}
            defaultValue={current?.cor}
          />
        </Field>
      </div>
      <Field label="Quilometragem *">
        <input
          name="km"
          type="number"
          min={0}
          step={1}
          required
          defaultValue={current?.km || 0}
        />
      </Field>
    </Form>
  );
}
export function UserForm({
  users,
  save,
  close,
}: {
  users: User[];
  save: (u: User & { senha: string }) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  return (
    <Form
      close={close}
      note="O novo usuário terá acesso à oficina atual conforme o papel escolhido."
      save={(f) => {
        if (!val(f, "nome")) throw Error("Preencha o nome.");
        if (new TextEncoder().encode(String(f.get("senha"))).length > 72)
          throw Error("A senha excede o limite de 72 bytes.");
        const email = val(f, "email").toLowerCase();
        if (users.some((u) => u.email.toLowerCase() === email))
          throw Error("Este e-mail já está cadastrado.");
        return save({
          id: uid(),
          nome: val(f, "nome"),
          email,
          papel: val(f, "papel") as Role,
          ativo: true,
          senha: String(f.get("senha")),
        });
      }}
    >
      <Field label="Nome *">
        <input name="nome" required maxLength={160} />
      </Field>
      <Field label="E-mail *">
        <input name="email" type="email" required maxLength={254} />
      </Field>
      <Field label="Papel *">
        <select name="papel" defaultValue="MECANICO">
          {Object.entries(roles).map(([k, label]) => (
            <option key={k} value={k}>
              {label}
            </option>
          ))}
        </select>
      </Field>
      <Field
        label="Senha inicial *"
        hint="De 12 a 72 caracteres; limite de 72 bytes."
      >
        <input
          name="senha"
          type="password"
          autoComplete="new-password"
          required
          minLength={12}
          maxLength={72}
        />
      </Field>
    </Form>
  );
}
export function OrderForm({
  vehicles,
  clients,
  users,
  save,
  close,
}: {
  vehicles: Vehicle[];
  clients: Client[];
  users: User[];
  save: (
    o: Pick<
      Order,
      "veiculoId" | "mecanicoId" | "kmEntrada" | "relato" | "previsaoEntrega"
    >,
  ) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  const [vehicleId, setVehicleId] = useState("");
  const vehicle = vehicles.find((v) => v.id === vehicleId);
  if (!vehicles.length) return <><Empty title="Cadastre um veículo primeiro">Abra Veículos e vincule um veículo ao cliente antes de abrir a ordem de serviço.</Empty><button onClick={close}>Voltar</button></>;
  return (
    <Form
      close={close}
      submit="Abrir OS"
      note="Comece pelo veículo. O cliente vinculado será incluído automaticamente."
      save={(f) => {
        if (!val(f, "relato")) throw Error("Descreva o relato do cliente.");
        return save({
          veiculoId: vehicleId,
          mecanicoId: val(f, "mecanicoId"),
          kmEntrada: Number(f.get("kmEntrada")),
          relato: val(f, "relato"),
          previsaoEntrega: val(f, "previsaoEntrega")
            ? new Date(val(f, "previsaoEntrega")).toISOString()
            : "",
        });
      }}
    >
      <Field label="Veículo *">
        <select
          required
          value={vehicleId}
          onChange={(e) => setVehicleId(e.target.value)}
        >
          <option value="" disabled>
            Selecione a placa e o veículo
          </option>
          {vehicles.map((v) => (
            <option key={v.id} value={v.id}>
              {v.placa} · {v.marca} {v.modelo}
            </option>
          ))}
        </select>
      </Field>
      {vehicle && (
        <div className="context-box">
          <small>Cliente vinculado</small>
          <strong>
            {clients.find((c) => c.id === vehicle.clienteId)?.nome}
          </strong>
        </div>
      )}
      <Field
        label="Quilometragem de entrada *"
        hint={
          vehicle
            ? `Último registro: ${vehicle.km.toLocaleString("pt-BR")} km`
            : undefined
        }
      >
        <input
          key={vehicleId}
          name="kmEntrada"
          type="number"
          min={vehicle?.km || 0}
          step={1}
          required
          defaultValue={vehicle?.km}
        />
      </Field>
      <Field label="Mecânico responsável">
        <select name="mecanicoId">
          <option value="">Atribuir depois</option>
          {users
            .filter((u) => u.papel === "MECANICO" && u.ativo)
            .map((u) => (
              <option key={u.id} value={u.id}>
                {u.nome}
              </option>
            ))}
        </select>
      </Field>
      <Field label="Previsão de entrega">
        <input name="previsaoEntrega" type="datetime-local" />
      </Field>
      <Field label="Relato do cliente *">
        <textarea
          name="relato"
          required
          maxLength={4000}
          rows={5}
          placeholder="O que o cliente percebeu no veículo?"
        />
      </Field>
    </Form>
  );
}
export function AddButton({
  children,
  onClick,
}: {
  children: ReactNode;
  onClick: () => void | Promise<void>;
}) {
  return (
    <button type="button" className="text-button" onClick={onClick}>
      <Icon name="plus" size={17} />
      {children}
    </button>
  );
}
