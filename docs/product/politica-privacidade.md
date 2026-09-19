# Política de Privacidade — Plataforma Automotiva

**Versão 1.0 — minuta para revisão jurídica.** O inventário de dados abaixo foi levantado a partir
do código e das migrations em 17/09/2026 (`V1`–`V4`). **Nada aqui afirma coleta que o sistema não
faça.** Campos entre colchetes dependem de definição da empresa.

- Controladora da plataforma: `[INSERIR RAZÃO SOCIAL]` — CNPJ `[INSERIR CNPJ]`
- Encarregado (DPO): `[INSERIR NOME]` — `[INSERIR E-MAIL DO ENCARREGADO]`
- Data de vigência: `[INSERIR DATA]`

---

## 1. Papéis no tratamento

| Situação | Controlador | Operador |
|---|---|---|
| Dados da oficina contratante e de seus usuários | Plataforma Automotiva | — |
| Dados de clientes finais e veículos cadastrados pela oficina | A oficina contratante | Plataforma Automotiva |
| Dados de cobrança da assinatura | Plataforma Automotiva | Meio de pagamento contratado |

A oficina é quem decide cadastrar um cliente final e quais dados inserir. Cabe a ela a base legal
desse tratamento. A Plataforma trata esses dados apenas para executar o serviço contratado.

## 2. Dados efetivamente coletados

O que segue é o inventário real. Cada linha corresponde a um campo existente no banco ou a um
registro que o sistema comprovadamente grava.

### 2.1 Dados da oficina

| Dado | Origem | Finalidade | Base legal |
|---|---|---|---|
| Nome e identificador (slug) da oficina | Cadastro | Identificar a conta e isolar os dados | Execução de contrato |
| Plano e situação da assinatura | Sistema | Aplicar limites e cobrança | Execução de contrato |

### 2.2 Dados dos usuários da oficina

| Dado | Origem | Finalidade | Base legal |
|---|---|---|---|
| Nome | Cadastro | Identificação na equipe e autoria de registros | Execução de contrato |
| E-mail | Cadastro | Autenticação e comunicação | Execução de contrato |
| Senha (apenas hash bcrypt, custo 12) | Cadastro | Autenticação | Execução de contrato |
| Papel e situação ativo/inativo | Cadastro | Controle de acesso | Execução de contrato |

**A senha em texto não é armazenada, registrada em log nem recuperável.**

### 2.3 Dados de clientes finais (inseridos pela oficina)

| Dado | Finalidade |
|---|---|
| Nome, telefone e e-mail (e-mail opcional) | Identificar o cliente da oficina e permitir contato sobre o serviço |
| Vínculo com veículos e ordens de serviço | Histórico de atendimento |
| Histórico de contatos do módulo Dinheiro Esquecido: canal, resultado, observação e data | Registro manual de tentativas de retomada comercial pela própria oficina |

### 2.4 Dados de veículos

Placa, marca, modelo, ano, quilometragem, cor e vínculo com o cliente. A placa é dado de veículo e,
associada ao proprietário, pode caracterizar dado pessoal.

### 2.5 Ordens de serviço e conteúdo operacional

Relato do cliente, quilometragem de entrada, status, previsão e conclusão, checklist, diagnóstico,
itens e versões de orçamento, valores, e a linha do tempo imutável com autor e instante de cada
evento.

### 2.6 Arquivos e imagens

Fotos de veículos em formato PNG ou JPEG, enviadas pelos usuários da oficina.

**A imagem é reescrita no servidor no momento do upload, o que descarta metadados EXIF, inclusive
coordenadas de GPS.** O arquivo é armazenado em repositório privado, com nome derivado de
identificador interno, e só é servido pela API após verificação de autenticação e de vínculo com a
oficina. Não existe URL pública de arquivo.

### 2.7 Endereço IP

**Coletado em uma única situação:** quando o cliente final aprova ou recusa um orçamento pelo link
público, o endereço IP de origem é gravado junto da decisão (`aprovacao_orcamento.ip_origem`).

- Finalidade: comprovar a autoria e a integridade de uma decisão comercial com efeito contratual.
- Base legal: legítimo interesse da oficina em documentar o aceite, e cumprimento do art. 15 do
  Marco Civil da Internet.
- Esse registro é imutável por travamento no banco de dados.

Fora dessa hipótese, **o IP não é armazenado**.

### 2.8 Dados técnicos e logs

Os registros de aplicação contêm: identificador da requisição, método HTTP, rota, status, duração,
identificador da oficina, identificador do usuário e código de erro.

**Os logs não contêm** endereço IP, informações de dispositivo, user agent, corpo de requisição,
senha, token, número de cartão ou qualquer credencial.

### 2.9 Dados de cobrança

| Dado | Observação |
|---|---|
| Plano, status, período, datas de cobrança | Armazenado na Plataforma |
| Identificadores de cliente e de assinatura no meio de pagamento | Referências opacas, sem dado de cartão |
| Histórico de eventos financeiros | Trilha imutável, higienizada |

**A Plataforma não armazena número de cartão, CVV, validade nem credencial de pagamento.** O
histórico financeiro passa por uma rotina que descarta qualquer metadado cuja chave contenha termos
sensíveis (senha, token, segredo, cartão, CVV, chave de API, assinatura criptográfica).

### 2.10 Cookies

**A Plataforma não utiliza cookies.** A autenticação é feita por token enviado no cabeçalho
`Authorization`, e a sessão do frontend é mantida apenas em memória durante a navegação: nada é
gravado em cookie, `localStorage` ou `sessionStorage`. Fechar a aba encerra a sessão local.

`[SE A EMPRESA ADOTAR ANALYTICS, CHAT DE SUPORTE OU PIXEL DE MARKETING NO SITE INSTITUCIONAL, ESTA
SEÇÃO PRECISA SER REESCRITA E UM AVISO DE COOKIES SERÁ NECESSÁRIO.]`

## 3. Compartilhamento

Os dados **não são vendidos nem cedidos para fins de marketing de terceiros**. Há compartilhamento
restrito com operadores necessários à prestação do serviço:

| Operador | O que recebe | Finalidade |
|---|---|---|
| `[INSERIR PROVEDOR DE INFRAESTRUTURA/NUVEM]` | Hospedagem do banco e da aplicação | Execução do serviço |
| `[INSERIR PROVEDOR DE ARMAZENAMENTO DE ARQUIVOS]` | Arquivos de fotos | Armazenamento |
| `[INSERIR GATEWAY DE PAGAMENTO]` | Dados de cobrança da oficina | Processamento da assinatura |

Também pode haver compartilhamento por obrigação legal ou ordem de autoridade competente.

## 4. Transferência internacional

`[DEPENDE DA REGIÃO DE HOSPEDAGEM CONTRATADA. Se a infraestrutura ficar fora do Brasil, descrever
aqui o país e a salvaguarda adotada (cláusulas contratuais padrão). Se ficar no Brasil, declarar que
não há transferência internacional.]`

## 5. Retenção

| Categoria | Prazo |
|---|---|
| Dados operacionais da oficina | Enquanto a assinatura estiver vigente |
| Após o encerramento | Conforme a Política de Cancelamento (`politica-cancelamento.md`) |
| Registros de auditoria e trilha financeira | `[INSERIR PRAZO — sugestão: 5 anos, alinhado a prazos prescricionais]` |
| Registros de acesso a aplicação (logs) | `[INSERIR PRAZO — mínimo legal de 6 meses, art. 15 do Marco Civil]` |
| Decisões de orçamento com IP | Preservadas como prova enquanto durar a relação e o prazo prescricional |

## 6. Segurança

- Isolamento entre oficinas garantido em três camadas: token autenticado, filtro por oficina em toda
  consulta e chaves estrangeiras compostas no banco.
- Senhas apenas como hash bcrypt de custo 12.
- Tokens de acesso de curta duração, com renovação por token separado e revogável.
- Repositório de arquivos privado, sem acesso anônimo.
- Links públicos de acompanhamento com token aleatório de 43 caracteres, guardado apenas como hash,
  com expiração e revogação.
- Registros de auditoria imutáveis por travamento no banco.
- Segredos exclusivamente no servidor, por variável de ambiente; nada sensível chega ao frontend.

Detalhamento técnico e achados da revisão em `revisao-lgpd.md`.

## 7. Direitos do titular

Nos termos do art. 18 da LGPD, o titular pode solicitar confirmação de tratamento, acesso,
correção, anonimização, bloqueio, eliminação, portabilidade, informação sobre compartilhamento e
revogação de consentimento quando este for a base legal.

**Encaminhamento correto do pedido:**

- Titular que é **usuário da oficina** (funcionário) ou **cliente final da oficina**: o pedido deve
  ser dirigido à oficina, que é a controladora desses dados. A Plataforma apoia a oficina no
  atendimento.
- Titular cujos dados são controlados pela Plataforma (contato comercial e financeiro da conta): o
  pedido pode ser dirigido diretamente ao encarregado.

Prazo de resposta: `[INSERIR PRAZO — sugestão: 15 dias]`.

> **Pendência técnica conhecida:** não existe hoje um endpoint de exportação completa nem de
> eliminação por titular. Enquanto não houver, o atendimento é operacional, conduzido pela equipe.
> Ver `revisao-lgpd.md`, itens P1 e P2.

## 8. Incidentes de segurança

Em caso de incidente com risco relevante aos titulares, a Plataforma comunicará as oficinas afetadas
e a ANPD nos termos do art. 48 da LGPD, no prazo `[INSERIR PRAZO INTERNO DE COMUNICAÇÃO]`.

## 9. Alterações desta Política

Alterações relevantes serão comunicadas com antecedência de `[INSERIR PRAZO]` pelos canais
cadastrados. A versão vigente é sempre a publicada na Plataforma.

## 10. Contato

- Encarregado: `[INSERIR NOME E E-MAIL DO ENCARREGADO]`
- Contato geral: `[INSERIR E-MAIL OFICIAL]`

---

## Pendências para a empresa preencher antes de publicar

| Campo | Seção |
|---|---|
| Razão social, CNPJ | Cabeçalho, 1 |
| Nome e e-mail do encarregado (DPO) | Cabeçalho, 7, 10 |
| Provedores de nuvem, armazenamento e pagamento | 3 |
| Região de hospedagem e transferência internacional | 4 |
| Prazos de retenção de auditoria e de logs | 5 |
| Prazo de resposta ao titular | 7 |
| Prazo interno de comunicação de incidente | 8 |
| Prazo de aviso de alteração | 9 |
| Decisão sobre analytics/cookies no site institucional | 2.10 |
