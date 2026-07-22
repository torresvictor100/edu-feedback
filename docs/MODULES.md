# Registro de módulos — EduFeedback

Estados permitidos: `proposto`, `aprovado`, `em implementação`, `disponível`, `pausado` ou `descontinuado`.

Seguir o processo definido em `NEW_MODULE_GUIDE.md` antes de adicionar um módulo.

---

## Módulos registrados

### Autenticação admin

- ID: `auth`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: plataforma
- Rotas principais: `POST /auth/login` (Serviço A)
- Propósito: administrador faz login e recebe um JWT para acessar rotas protegidas.
- Público: administradores.
- Responsável pela lógica: módulo `auth` no Serviço A (`backend/`).
- Limitações conhecidas: sem refresh token, sem cadastro de novo admin via API (seed inicial via migration).

### Recebimento de feedback

- ID: `avaliacao`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: produto
- Rotas principais: `POST /avaliação` (Serviço A, público)
- Propósito: estudante envia uma avaliação de aula (nota 0–10 e descrição).
- Público: estudantes.
- Responsável pela lógica: módulo `avaliacao` no Serviço A (`backend/`).
- Limitações conhecidas: não identifica o estudante (o contrato do desafio não pede autenticação nem identificação); notas ≤ 3 apenas enfileiram a notificação, o envio real acontece no Serviço B.

### Notificação de feedback crítico

- ID: `notificacao-critica`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: integração
- Rotas principais: função `FeedbackCriticoFunction` (Queue Trigger, fila `notificacoes-criticas`)
- Propósito: avisar os administradores por e-mail quando chega um feedback com nota ≤ 3.
- Público: administradores.
- Responsável pela lógica: módulo `notificacao` no Serviço B (`functions/`).
- Limitações conhecidas: envia para todos os admins cadastrados, sem preferência individual de notificação.

### Geração de relatório agendado

- ID: `relatorio-agendado`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: produto
- Rotas principais: função `RelatorioAgendadoFunction` (Timer Trigger, periodicidade configurável via `RELATORIO_AGENDADO_CRON`)
- Propósito: gerar automaticamente um relatório com médias e contagens de avaliações.
- Público: administradores.
- Responsável pela lógica: módulo `timer` no Serviço B (`functions/`).
- Limitações conhecidas: periodicidade fixa por configuração, não por regra de negócio dinâmica.

### Solicitação de relatório sob demanda

- ID: `relatorio-sob-demanda`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: produto
- Rotas principais: `POST /relatorios/solicitacoes` (Serviço B, função HTTP, JWT admin)
- Propósito: administrador pede um relatório quando quiser; como a geração demora, o pedido é enfileirado e processado depois.
- Público: administradores.
- Responsável pela lógica: módulo `relatorio` no Serviço B (`functions/`).
- Limitações conhecidas: resposta é assíncrona (202 + id), o cliente precisa consultar depois.

### Processamento assíncrono do relatório

- ID: `relatorio-processamento`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: infraestrutura
- Rotas principais: função `ProcessarRelatorioFunction` (Queue Trigger, fila `solicitacoes-relatorio`)
- Propósito: gerar de fato o relatório solicitado sob demanda e avisar o admin por e-mail quando ficar pronto.
- Público: administradores (indireto, via e-mail e via consulta posterior).
- Responsável pela lógica: módulo `queue` no Serviço B (`functions/`).
- Limitações conhecidas: sem retry customizado além do padrão do Azure Storage Queue.

### Consulta de relatório

- ID: `relatorio-consulta`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: produto
- Rotas principais: `GET /relatorios/{id}` (Serviço A, JWT admin)
- Propósito: administrador consulta o status e o conteúdo de um relatório (agendado ou sob demanda).
- Público: administradores.
- Responsável pela lógica: módulo `relatorio` no Serviço A (`backend/`).
- Limitações conhecidas: nenhuma listagem paginada de todos os relatórios nesta primeira versão, apenas consulta por id.

---

## Como registrar um novo módulo

Adicionar uma entrada com os seguintes campos:

```markdown
### {{Nome do módulo}}

- ID: `{{slug-do-modulo}}`
- Estado: `proposto`
- Data de inclusão: {{data}}
- Categoria: {{plataforma | produto | integração | infraestrutura}}
- Rotas principais: {{/caminho/da/rota ou endpoint da API}}
- Propósito: {{uma frase}}
- Público: {{quem usa este módulo}}
- Responsável pela lógica: módulo `{{slug}}` no backend
- Limitações conhecidas: {{o que este módulo não faz}}
```

Não iniciar implementação com estado `proposto`. Mover para `aprovado` somente após revisão e aprovação explícita.
