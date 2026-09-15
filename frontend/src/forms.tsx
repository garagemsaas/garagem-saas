import { useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { Field, Icon } from "./ui";
import { roles, uid, val } from "./model";
import type { Client, Vehicle, User, Order, Role } from "./model";

export function Form({
  children,
  save,
  close,
  submit = "Salvar",
  note,
}: {
  children: ReactNode;
  save: (f: FormData) => void | Promise<void>;
  close: () => void | Promise<void>;
  submit?: string;
  note?: string;
}) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await save(new FormData(e.currentTarget));
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Confira os dados informados.",
      );
    } finally { setBusy(false); }
  }
  return (
    <form onSubmit={onSubmit}>
      {note && <p className="form-note">{note}</p>}
      {children}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <div className="form-actions">
        <button type="button" onClick={close}>
          Cancelar
        </button>
        <button className="primary" type="submit" disabled={busy}>
          {submit}
        </button>
      </div>
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
