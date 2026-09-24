import { useEffect, useRef, useState } from "react";
import { ApiError } from './api';
import { FormErrors } from './form-context';
import type { FormEvent, ReactNode } from "react";
import { Empty, Field, Icon } from "./ui";
import { uid, val } from "./model";
import type { Client, Vehicle, User, Order } from "./model";

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
  useEffect(() => {
    if (!error || busy || pending) return;
    const target = ref.current?.querySelector<HTMLElement>('[aria-invalid="true"]')
      ?? ref.current?.querySelector<HTMLElement>('[role="alert"]');
    target?.focus();
  }, [error, fieldErrors, busy, pending]);
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
  close, allowUnowned = false, clientPicker,
}: {
  allowUnowned?: boolean; clientPicker?: ReactNode;
  current?: Vehicle;
  clients: Client[];
  vehicles: Vehicle[];
  save: (v: Vehicle) => void | Promise<void>;
  close: () => void | Promise<void>;
}) {
  if (!allowUnowned && !clients.length) return <><Empty title="Cadastre um cliente primeiro">Todo veículo precisa estar vinculado ao seu proprietário. Abra Clientes e cadastre o contato antes de continuar.</Empty><button onClick={close}>Voltar</button></>;
  return (
    <Form
      close={close}
      save={(f) => {
        const placa = val(f, "placa").replace(/[- ]/g, "").toUpperCase();
        if (placa && vehicles.some((v) => v.placa === placa && v.id !== current?.id))
          throw Error("Esta placa já está cadastrada na oficina.");
        if (!val(f, "marca") || !val(f, "modelo") || !val(f, "cor"))
          throw Error("Preencha marca, modelo e cor.");
        return save({
          id: current?.id || uid(),
          clienteId: val(f, "clienteId"),
          propriedade: current?.propriedade,
          versao: val(f, "versao") || null, anoModelo: Number(val(f, "anoModelo")) || null,
          chassi: val(f, "chassi") || null, renavam: val(f, "renavam") || null,
          combustivel: val(f, "combustivel") || null, cambio: val(f, "cambio") || null, observacoes: val(f, "observacoes") || null,
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
      {clientPicker || <Field label="Cliente *">
        <select
          name="clienteId"
          required={!allowUnowned}
          disabled={current?.propriedade === 'EMPRESA'}
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
      </Field>}
      <Field label={allowUnowned ? 'Placa (ou informe chassi)' : 'Placa *'}>
        <input
          name="placa"
          className="plate-input"
          required={!allowUnowned}
          pattern="[A-Za-z]{3}[- ]?[0-9][A-Za-z0-9][0-9]{2}"
          defaultValue={current?.placa || ''}
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
      <div className="form-grid">
        <Field label="Versão"><input name="versao" maxLength={100} defaultValue={current?.versao || ''} /></Field>
        <Field label="Ano modelo"><input name="anoModelo" type="number" min={1886} max={2200} defaultValue={current?.anoModelo || current?.ano} /></Field>
        <Field label="Chassi"><input name="chassi" maxLength={30} pattern="[A-Za-z0-9]+" defaultValue={current?.chassi || ''} /></Field>
        <Field label="RENAVAM"><input name="renavam" maxLength={20} pattern="[0-9]+" defaultValue={current?.renavam || ''} /></Field>
        <Field label="Combustível"><input name="combustivel" maxLength={40} defaultValue={current?.combustivel || ''} /></Field>
        <Field label="Câmbio"><input name="cambio" maxLength={40} defaultValue={current?.cambio || ''} /></Field>
      </div><Field label="Observações"><textarea name="observacoes" maxLength={4000} defaultValue={current?.observacoes || ''} /></Field>
    </Form>
  );
}
export function OrderForm({
  vehicles,
  clients,
  users,
  save,
  close,
  createClient,
  createVehicle,
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
  createClient: (c: Pick<Client, "nome" | "telefone">) => Promise<Client>;
  createVehicle: (v: Omit<Vehicle, "id" | "revisao">) => Promise<Vehicle>;
}) {
  const [vehicleId, setVehicleId] = useState("");
  // Cadastrar sem sair daqui: quem está com o cliente na frente não deveria ter que abandonar a
  // abertura do serviço, navegar até Veículos e voltar para recomeçar do zero.
  const [cadastro, setCadastro] = useState<null | "veiculo">(null);
  const vehicle = vehicles.find((v) => v.id === vehicleId);
  if (cadastro === "veiculo" || !vehicles.length)
    return <NovoVeiculoInline clients={clients} createClient={createClient} createVehicle={createVehicle}
      close={vehicles.length ? () => setCadastro(null) : close}
      done={(v) => { setVehicleId(v.id); setCadastro(null); }} />;
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
      <button type="button" className="text-button" onClick={() => setCadastro("veiculo")}>
        O veículo não está na lista
      </button>
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

/**
 * Cadastro de veículo — e, se preciso, do dono — sem sair da abertura do serviço.
 *
 * <p>O cliente é escolhido da lista ou criado aqui mesmo, em sequência: criar o cliente antes do
 * veículo não é detalhe de implementação, é a regra do domínio (um veículo de cliente exige dono).
 * Nada é duplicado: quem já existe é selecionado, não recriado.
 */
function NovoVeiculoInline({ clients, createClient, createVehicle, close, done }: {
  clients: Client[];
  createClient: (c: Pick<Client, "nome" | "telefone">) => Promise<Client>;
  createVehicle: (v: Omit<Vehicle, "id" | "revisao">) => Promise<Vehicle>;
  close: () => void | Promise<void>;
  done: (v: Vehicle) => void;
}) {
  const [novoCliente, setNovoCliente] = useState(!clients.length);
  return (
    <Form
      close={close}
      submit="Cadastrar e continuar"
      note="O veículo entra no serviço assim que for cadastrado."
      save={async (f) => {
        const clienteId = novoCliente
          ? (await createClient({ nome: val(f, "nome"), telefone: val(f, "telefone") })).id
          : val(f, "clienteId");
        if (!clienteId) throw Error("Selecione ou cadastre o cliente.");
        const veiculo = await createVehicle({
          clienteId,
          placa: val(f, "placa").replace(/[- ]/g, "").toUpperCase(),
          marca: val(f, "marca"),
          modelo: val(f, "modelo"),
          ano: Number(f.get("ano")),
          km: Number(f.get("km")),
          cor: val(f, "cor"),
        } as Omit<Vehicle, "id" | "revisao">);
        done(veiculo);
      }}
    >
      {clients.length > 0 && (
        <Field label="Cliente *">
          <select name="clienteId" required={!novoCliente} disabled={novoCliente} defaultValue="">
            <option value="" disabled>Selecione o proprietário</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
        </Field>
      )}
      <label className="company-switch">
        <input type="checkbox" checked={novoCliente} disabled={!clients.length}
          onChange={(e) => setNovoCliente(e.target.checked)} /> O cliente também é novo
      </label>
      {novoCliente && <>
        <Field label="Nome do cliente *"><input name="nome" required maxLength={160} /></Field>
        <Field label="Telefone *"><input name="telefone" required maxLength={30} inputMode="tel" /></Field>
      </>}
      <Field label="Placa *"><input name="placa" required maxLength={8} /></Field>
      <Field label="Marca *"><input name="marca" required maxLength={80} /></Field>
      <Field label="Modelo *"><input name="modelo" required maxLength={100} /></Field>
      <Field label="Ano *"><input name="ano" type="number" required min={1886} max={2200} /></Field>
      <Field label="Quilometragem *"><input name="km" type="number" required min={0} /></Field>
      <Field label="Cor *"><input name="cor" required maxLength={60} /></Field>
    </Form>
  );
}
