import { useState } from 'react';
import { api } from '../api';
import { Form } from '../forms';
import { money, val } from '../model';
import { EntitySelect, MoneyField, Notes, ResourceState } from './shared';
import { useResource } from './dados';
import type { Estoque, Proposta } from './types';
export default function VendaForm({ estoque, userId, close, done }: { estoque: Estoque; userId: string; close: () => void; done: () => void }) {
  const [proposalId, setProposalId] = useState('');
  return <><p>{estoque.marca} {estoque.modelo} · Custo acumulado: <strong>{money(estoque.custoTotal)}</strong></p><EntitySelect path="/revenda/propostas?status=ACEITA" name="proposta" label="Proposta aceita (opcional)" required={false} onPick={setProposalId} />
    {proposalId ? <VendaComProposta key={proposalId} id={proposalId} estoque={estoque} userId={userId} close={close} done={done} /> : <ConfirmarVenda estoque={estoque} userId={userId} close={close} done={done} />}</>;
}
function VendaComProposta(props: { id: string; estoque: Estoque; userId: string; close: () => void; done: () => void }) {
  const r = useResource<Proposta>(`/revenda/propostas/${props.id}`); return r.data ? <ConfirmarVenda {...props} proposta={r.data} /> : <ResourceState error={r.error} retry={r.reload} />;
}
function ConfirmarVenda({ estoque, userId, close, done, proposta }: { estoque: Estoque; userId: string; close: () => void; done: () => void; proposta?: Proposta }) {
  return <Form close={close} submit="Concluir venda" confirmation="Confirmar a venda, os valores informados e a transferência de propriedade ao comprador? Se houver troca, o veículo avaliado será adquirido pela empresa nesta mesma operação." save={async f => {
    await api('/revenda/vendas', 'POST', { estoqueId: estoque.id, revisaoEstoque: estoque.revisao, clienteId: proposta?.clienteId ?? val(f, 'clienteId'), vendedorId: val(f, 'vendedorId'), propostaId: proposta?.id ?? null, propostaVersaoId: proposta?.versaoId ?? null, valorVendido: proposta?.valorNegociado ?? val(f, 'valorVendido'), entrada: proposta?.entrada ?? val(f, 'entrada'), avaliacaoTrocaId: proposta?.avaliacaoTrocaId ?? (val(f, 'avaliacaoTrocaId') || null), valorTroca: proposta?.valorTroca ?? val(f, 'valorTroca'), observacoes: val(f, 'observacoes') }); done();
  }}>
    {proposta ? <section><h3>Proposta de {proposta.clienteNome}</h3><p>{proposta.veiculoDescricao} · Versão {proposta.numeroVersao}</p><dl><dt>Valor vendido</dt><dd>{money(proposta.valorNegociado)}</dd><dt>Entrada</dt><dd>{money(proposta.entrada)}</dd><dt>Troca</dt><dd>{money(proposta.valorTroca)}</dd></dl></section> : <><EntitySelect path="/clientes" name="clienteId" label="Comprador" /><MoneyField name="valorVendido" label="Valor vendido *" value={estoque.precoAnunciado} /><MoneyField name="entrada" label="Entrada *" /><EntitySelect path="/revenda/avaliacoes?status=ABERTA" name="avaliacaoTrocaId" label="Avaliação da troca (opcional)" required={false} /><MoneyField name="valorTroca" label="Valor da troca (igual ao oferecido na avaliação) *" /></>}
    <EntitySelect path="/usuarios" name="vendedorId" label="Vendedor" commercial initial={userId} /><Notes />
  </Form>;
}
