export default function Help({ dealer = false }: { dealer?: boolean }) {
  return <section className="product-help"><h1>Como podemos ajudar?</h1>
    <details><summary>Como cadastrar um cliente?</summary><p>Abra Clientes e busque o nome antes de adicionar. Confira o telefone para facilitar os próximos contatos.</p></details>
    <details><summary>{dealer ? 'Como registrar a compra de um carro?' : 'Como iniciar um serviço?'}</summary><p>{dealer ? 'Em Carros, escolha Comprar carro. Informe o veículo, o fornecedor, o valor e a data da compra. Registre os custos de preparação antes de anunciar.' : 'Cadastre o cliente e seu veículo. Em Serviços, abra um atendimento e descreva o que precisa ser verificado.'}</p></details>
    <details><summary>Como organizar os retornos?</summary><p>Em Retornos, agende o contato com motivo, data, horário e responsável. Após conversar com o cliente, conclua e registre o resultado. Se precisar de outro dia, use Editar ou reagendar.</p></details>
    <details><summary>Como administrar o acesso da equipe?</summary><p>O proprietário pode adicionar e editar pessoas em Usuários. Desativar o acesso preserva os registros anteriores. Se perdeu sua senha, peça ao proprietário para redefini-la.</p></details>
    <details><summary>O que fazer quando uma alteração não é salva?</summary><p>Leia o aviso e confira os campos. Se outra pessoa alterou o registro, atualize a tela antes de tentar novamente. Em caso de falha de conexão, confira a lista para saber se a operação foi concluída.</p></details>
  </section>;
}
