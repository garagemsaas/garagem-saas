export type Role = "OWNER" | "ATENDENTE" | "MECANICO";
export const roles: Record<Role, string> = {
  OWNER: "Proprietário",
  ATENDENTE: "Atendente",
  MECANICO: "Mecânico",
};
export const statuses = {
  RECEBIDO: "Recebido",
  DIAGNOSTICO: "Diagnóstico",
  ORCAMENTO: "Orçamento",
  AGUARDANDO_APROVACAO: "Aguardando aprovação",
  EM_MANUTENCAO: "Em manutenção",
  AGUARDANDO_PECA: "Aguardando peça",
  TESTE: "Em teste",
  PRONTO: "Pronto",
};
export type Status = keyof typeof statuses;
export type Classification = "OK" | "ACOMPANHAR" | "TROCAR";
export interface Client {
  id: string;
  nome: string;
  telefone: string;
  email: string;
  revisao: number;
}
export interface Vehicle {
  id: string;
  clienteId: string;
  placa: string;
  marca: string;
  modelo: string;
  ano: number;
  km: number;
  cor: string;
  revisao: number;
}
export interface User {
  id: string;
  nome: string;
  email: string;
  papel: Role;
  ativo: boolean;
}
export interface CheckItem {
  id: string;
  descricao: string;
  condicao: string;
  observacao: string;
}
export interface Diagnosis {
  id: string;
  descricao: string;
  classificacao: Classification;
  criadoEm: string;
}
export interface BudgetItem {
  id: string;
  tipo: "PECA" | "SERVICO";
  descricao: string;
  quantidade: number;
  valorUnitario: number;
  subtotal: number;
}
export interface Version {
  id: string;
  numero: number;
  observacoes: string;
  total: number;
  criadoEm: string;
  itens: BudgetItem[];
  decisao?: { aprovado: boolean; criadoEm: string; canal: string };
}
export interface Photo {
  id: string;
  finalidade: string;
  descricao: string;
  url: string;
  vinculo?: string;
}
export interface Order {
  id: string;
  numero: number;
  veiculoId: string;
  clienteId: string;
  mecanicoId: string;
  status: Status;
  kmEntrada: number;
  relato: string;
  criadoEm: string;
  previsaoEntrega: string;
  revisao: number;
  checklist?: { observacoes: string; itens: CheckItem[] };
  diagnosticos: Diagnosis[];
  versoes: Version[];
  fotos: Photo[];
  timeline: {
    id: string;
    descricao: string;
    origem: string;
    criadoEm: string;
  }[];
  link?: { ativo: boolean; expiraEm: string; id?: string; url?: string; token?: string };
}
export const uid = () => crypto.randomUUID();
export const val = (f: FormData, key: string) =>
  String(f.get(key) || "").trim();
export const now = () => new Date().toISOString();
export const money = (n: number) =>
  n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const date = (s?: string, time = false) =>
  s
    ? new Date(s).toLocaleString("pt-BR", {
        day: "2-digit",
        month: "2-digit",
        ...(time
          ? { hour: "2-digit", minute: "2-digit" }
          : { year: "numeric" }),
      })
    : "Não informada";
export const number = (n: number) => n.toLocaleString("pt-BR");
export const transitions: Record<Status, Status[]> = {
  RECEBIDO: ["DIAGNOSTICO"],
  DIAGNOSTICO: ["ORCAMENTO"],
  ORCAMENTO: ["AGUARDANDO_APROVACAO"],
  AGUARDANDO_APROVACAO: ["ORCAMENTO"],
  EM_MANUTENCAO: ["AGUARDANDO_PECA", "TESTE"],
  AGUARDANDO_PECA: ["EM_MANUTENCAO"],
  TESTE: ["PRONTO"],
  PRONTO: [],
};
export function seed() {
  const clientes: Client[] = [
    {
      id: "c1",
      nome: "Mariana Costa",
      telefone: "(11) 99912-3400",
      email: "mariana@example.com",
      revisao: 0,
    },
    {
      id: "c2",
      nome: "Ricardo Almeida",
      telefone: "(11) 98845-1200",
      email: "ricardo@example.com",
      revisao: 0,
    },
    {
      id: "c3",
      nome: "Paulo Henrique Santos",
      telefone: "(11) 99730-6500",
      email: "",
      revisao: 0,
    },
    {
      id: "c4",
      nome: "Fernanda Oliveira",
      telefone: "(11) 98421-7800",
      email: "fernanda@example.com",
      revisao: 0,
    },
    {
      id: "c5",
      nome: "Roberto Ferreira",
      telefone: "(11) 99814-9200",
      email: "",
      revisao: 0,
    },
    {
      id: "c6",
      nome: "Camila Rodrigues",
      telefone: "(11) 98553-6100",
      email: "camila@example.com",
      revisao: 0,
    },
  ];
  const veiculos: Vehicle[] = [
    {
      id: "v1",
      clienteId: "c1",
      placa: "FKS2J48",
      marca: "Volkswagen",
      modelo: "Polo 1.0 TSI",
      ano: 2021,
      km: 48250,
      cor: "Prata",
      revisao: 0,
    },
    {
      id: "v2",
      clienteId: "c2",
      placa: "GHT4B21",
      marca: "Chevrolet",
      modelo: "Onix LT",
      ano: 2020,
      km: 67420,
      cor: "Branco",
      revisao: 0,
    },
    {
      id: "v3",
      clienteId: "c3",
      placa: "EPR8A63",
      marca: "Toyota",
      modelo: "Corolla XEi",
      ano: 2019,
      km: 92310,
      cor: "Preto",
      revisao: 0,
    },
    {
      id: "v4",
      clienteId: "c4",
      placa: "FWL1D90",
      marca: "Hyundai",
      modelo: "HB20 Comfort",
      ano: 2022,
      km: 31800,
      cor: "Cinza",
      revisao: 0,
    },
    {
      id: "v5",
      clienteId: "c5",
      placa: "DML9F12",
      marca: "Fiat",
      modelo: "Strada Freedom",
      ano: 2021,
      km: 78600,
      cor: "Branco",
      revisao: 0,
    },
    {
      id: "v6",
      clienteId: "c6",
      placa: "GHJ6C75",
      marca: "Honda",
      modelo: "Fit EX",
      ano: 2018,
      km: 86400,
      cor: "Azul",
      revisao: 0,
    },
  ];
  const usuarios: User[] = [
    {
      id: "u1",
      nome: "André Martins",
      email: "andre@example.com",
      papel: "OWNER",
      ativo: true,
    },
    {
      id: "u2",
      nome: "Lucas Pereira",
      email: "lucas@example.com",
      papel: "MECANICO",
      ativo: true,
    },
    {
      id: "u3",
      nome: "Diego Souza",
      email: "diego@example.com",
      papel: "MECANICO",
      ativo: true,
    },
    {
      id: "u4",
      nome: "Juliana Lima",
      email: "juliana@example.com",
      papel: "ATENDENTE",
      ativo: true,
    },
  ];
  const day = (offset: number, hour = 9) => {
    const d = new Date();
    d.setDate(d.getDate() + offset);
    d.setHours(hour, 0, 0, 0);
    return d.toISOString();
  };
  const list: Status[] = [
    "AGUARDANDO_APROVACAO",
    "EM_MANUTENCAO",
    "DIAGNOSTICO",
    "RECEBIDO",
    "AGUARDANDO_PECA",
    "PRONTO",
  ];
  const ordens: Order[] = veiculos.map((v, i) => ({
    id: `o${i + 1}`,
    numero: 1048 - i,
    veiculoId: v.id,
    clienteId: v.clienteId,
    mecanicoId: i === 3 ? "" : i % 2 ? "u3" : "u2",
    status: list[i],
    kmEntrada: v.km,
    relato: [
      "Cliente relata ruído ao frear e vibração no pedal. Verificar freios dianteiros e realizar inspeção geral.",
      "Revisão do sistema de arrefecimento. Cliente percebeu redução do nível do reservatório.",
      "Ruído na suspensão dianteira ao passar por irregularidades.",
      "Revisão e troca de óleo do motor.",
      "Dificuldade na partida pela manhã.",
      "Troca de óleo e filtros.",
    ][i],
    criadoEm: day(i < 4 ? 0 : -1, 8 + i),
    previsaoEntrega: day(i === 4 ? 2 : 1, 17),
    revisao: 0,
    diagnosticos: [],
    versoes: [],
    fotos: [],
    timeline: [
      {
        id: `e${i}`,
        descricao: "OS recebida na oficina.",
        origem: "Juliana Lima · Usuário",
        criadoEm: day(i < 4 ? 0 : -1, 8 + i),
      },
    ],
  }));
  const itens: BudgetItem[] = [
    {
      id: "bi1",
      tipo: "PECA",
      descricao: "Jogo de pastilhas de freio dianteiras",
      quantidade: 1,
      valorUnitario: 245,
      subtotal: 245,
    },
    {
      id: "bi2",
      tipo: "PECA",
      descricao: "Disco de freio dianteiro",
      quantidade: 2,
      valorUnitario: 189.9,
      subtotal: 379.8,
    },
    {
      id: "bi3",
      tipo: "SERVICO",
      descricao: "Substituição de discos e pastilhas",
      quantidade: 1,
      valorUnitario: 180,
      subtotal: 180,
    },
  ];
  ordens[0].checklist = {
    observacoes:
      "Veículo recebido com chave principal. Sem objetos de valor informados.",
    itens: [
      {
        id: "ch1",
        descricao: "Carroceria",
        condicao: "Com avaria",
        observacao: "Risco superficial no para-choque traseiro.",
      },
      {
        id: "ch2",
        descricao: "Pneus",
        condicao: "Bom estado",
        observacao: "Desgaste uniforme nos quatro pneus.",
      },
      {
        id: "ch3",
        descricao: "Combustível",
        condicao: "Meio tanque",
        observacao: "",
      },
      {
        id: "ch4",
        descricao: "Painel",
        condicao: "Sem alerta",
        observacao: "",
      },
    ],
  };
  ordens[0].diagnosticos = [
    {
      id: "d1",
      descricao:
        "Pastilhas dianteiras no limite de desgaste. Discos com sulcos e espessura abaixo da recomendada.",
      classificacao: "TROCAR",
      criadoEm: day(0, 10),
    },
    {
      id: "d2",
      descricao:
        "Buchas da suspensão com desgaste inicial, sem folga excessiva.",
      classificacao: "ACOMPANHAR",
      criadoEm: day(0, 10),
    },
    {
      id: "d3",
      descricao: "Nível e condição do fluido de freio adequados.",
      classificacao: "OK",
      criadoEm: day(0, 10),
    },
  ];
  ordens[0].versoes = [
    {
      id: "b1",
      numero: 1,
      observacoes: "Proposta inicial, substituída após inspeção dos discos.",
      total: 425,
      criadoEm: day(0, 10),
      itens: [itens[0], itens[2]],
    },
    {
      id: "b2",
      numero: 2,
      observacoes: "Inclui peças e mão de obra descritas acima.",
      total: 804.8,
      criadoEm: day(0, 11),
      itens,
    },
  ];
  ordens[0].timeline.push(
    ...[
      "Checklist de entrada registrado.",
      "Diagnóstico registrado: trocar, acompanhar e OK.",
      "Orçamento v1 criado. Total: R$ 425,00.",
      "Orçamento v2 criado. Total: R$ 804,80.",
      "Status alterado para Aguardando aprovação.",
    ].map((descricao, i) => ({
      id: `ev${i}`,
      descricao,
      origem: "André Martins · Usuário",
      criadoEm: day(0, 9 + i),
    })),
  );
  for (const i of [1, 4, 5])
    ordens[i].versoes = [
      {
        id: `budget${i}`,
        numero: 1,
        observacoes: "",
        total: 320,
        criadoEm: day(-1),
        itens: [
          {
            id: `item${i}`,
            tipo: "SERVICO",
            descricao: ordens[i].relato,
            quantidade: 1,
            valorUnitario: 320,
            subtotal: 320,
          },
        ],
        decisao: {
          aprovado: true,
          criadoEm: day(-1, 15),
          canal: "LINK_PUBLICO",
        },
      },
    ];
  return { clientes, veiculos, usuarios, ordens };
}
