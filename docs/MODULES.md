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
- Data de inclusão: 2026-07-22 (revisado em 2026-07-24, ADR-006)
- Categoria: integração
- Rotas principais: `FeedbackCriticoTrigger` (Queue Trigger, fila `notificacoes-criticas`) → `POST /internal/feedback-critico` (Quarkus, `X-Internal-Secret`)
- Propósito: avisar os administradores por e-mail quando chega um feedback com nota ≤ 3, com descrição, urgência e data de envio.
- Público: administradores.
- Responsável pela lógica: módulos `notificacao`/`admin`/`shared` no Serviço B (`functions/`) — `FeedbackCriticoResource` + `EmailService` (CDI).
- Limitações conhecidas: envia para todos os admins cadastrados, sem preferência individual de notificação.

### Geração de relatório agendado

- ID: `relatorio-agendado`
- Estado: `disponível`
- Data de inclusão: 2026-07-22 (revisado em 2026-07-24, ADR-006)
- Categoria: produto
- Rotas principais: `RelatorioAgendadoTrigger` (Timer Trigger, periodicidade configurável via `RELATORIO_AGENDADO_CRON`) → `POST /internal/relatorio-agendado` (Quarkus, `X-Internal-Secret`)
- Propósito: gerar automaticamente um relatório com médias, contagens por dia/urgência e a lista de avaliações (descrição, urgência, data de envio).
- Público: administradores.
- Responsável pela lógica: módulos `relatorio`/`avaliacao`/`shared` no Serviço B (`functions/`) — `RelatorioAgendadoResource` + `RelatorioService` (CDI, Panache).
- Limitações conhecidas: periodicidade fixa por configuração, não por regra de negócio dinâmica.

### Consulta de relatório

- ID: `relatorio-consulta`
- Estado: `disponível`
- Data de inclusão: 2026-07-22
- Categoria: produto
- Rotas principais: `GET /relatorios/{id}` (Serviço A, JWT admin)
- Propósito: administrador consulta o status e o conteúdo de um relatório.
- Público: administradores.
- Responsável pela lógica: módulo `relatorio` no Serviço A (`backend/`).
- Limitações conhecidas: nenhuma listagem paginada de todos os relatórios nesta primeira versão, apenas consulta por id. Só existem relatórios do tipo agendado (ver ADR-005 em `DECISIONS.md` — solicitação sob demanda foi removida).

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
