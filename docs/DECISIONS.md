# Registro de decisões — EduFeedback

Estados permitidos: `proposta`, `aceita`, `substituída` ou `rejeitada`.

---

## ADR-001 — Stack principal

- Data: 2026-07-22
- Estado: aceita

### Contexto

O projeto precisará atender ao requisito obrigatório do Tech Challenge Fase 4: rodar em ambiente de nuvem e implementar no mínimo duas funções serverless com responsabilidade única, além de uma API para login e recebimento de feedback.

### Decisão

Arquitetura de dois serviços: Java 21 + Spring Boot 3 + Maven + Spring Data JPA + Flyway + PostgreSQL para a API principal (login, feedback, consulta de relatório); Java 21 + Maven para as 2 funções serverless (timer de relatório agendado, queue de notificação crítica), compartilhando o mesmo banco PostgreSQL. O Serviço B combina gatilhos nativos finos com Quarkus para a lógica de negócio — ver ADR-006 (substitui a ADR-005).

### Consequências

Mudanças de stack exigem nova ADR aprovada. Introduzir tecnologias fora desta lista sem aprovação viola a regra de trabalho definida em `AGENTS.md`.

---

## ADR-002 — Organização por módulos de domínio

- Data: 2026-07-22
- Estado: aceita

### Contexto

O projeto terá múltiplas funcionalidades que precisam crescer de forma independente.

### Decisão

Organizar cada serviço por módulos de domínio (`auth`, `avaliacao`, `relatorio`). Cada módulo tem sua própria pasta, rota e contratos. Código compartilhado surge apenas de necessidades reais, não por antecipação.

### Consequências

Adicionar um novo módulo não exige reestruturar os existentes. O acoplamento entre módulos é explicitamente proibido a não ser via interfaces formais.

---

## ADR-003 — Segredos fora do versionamento

- Data: 2026-07-22
- Estado: aceita

### Contexto

Credenciais e chaves de API precisam ser configuráveis por ambiente sem risco de exposição em repositórios públicos ou privados.

### Decisão

Todos os segredos (chaves de API, tokens, senhas) ficam exclusivamente em variáveis de ambiente ou em gerenciadores de segredos (Azure Key Vault em produção). O `.env.example` documenta os nomes sem os valores. O `.env` real está no `.gitignore`.

### Consequências

Nenhum segredo em código, logs ou arquivos versionados. Deployments precisam configurar variáveis de ambiente antes da execução.

---

## ADR-004 — Empacotamento híbrido do Serviço B (Quarkus + Azure Functions Java Worker)

- Data: 2026-07-22
- Estado: substituída (ver ADR-005)

### Contexto

Versão inicial do desenho tinha 4 funções: timer (relatório agendado), HTTP (solicitação sob demanda), queue (processamento do relatório) e queue (notificação de feedback crítico). A extensão oficial `quarkus-azure-functions-http` só suporta função com HTTP trigger, o que exigia misturar Quarkus CDI (na função HTTP) com Azure Functions Java Worker puro (nas demais) no mesmo módulo.

### Decisão (substituída)

Esta ADR foi substituída pela ADR-005 após a decisão de reduzir o Serviço B a exatamente 2 funções, eliminando a função HTTP e, com ela, toda a necessidade de Quarkus no módulo `functions/`.

---

## ADR-005 — Redução a 2 funções serverless, sem framework de aplicação no Serviço B

- Data: 2026-07-22
- Estado: aceita

### Contexto

O enunciado exige no mínimo 2 funções serverless com responsabilidade única, e pede como avaliação "a correta separação dos serviços e responsabilidades". O desenho anterior (ADR-004, 4 funções) tinha um ponto fraco de responsabilidade única: a função de processamento de relatório sob demanda misturava "gerar relatório" com "notificar que ficou pronto por e-mail". A decisão do usuário foi simplificar para as 2 funções que mapeiam direto nas duas automações que o próprio enunciado pede ("o envio de notificações e a geração de relatórios"), eliminando o fluxo de solicitação sob demanda por completo.

### Decisão

Serviço B passa a ter exatamente 2 funções, cada uma com um único trigger e uma única responsabilidade:

1. **`FeedbackCriticoFunction`** — Queue Trigger na fila `notificacoes-criticas`. Única responsabilidade: enviar e-mail de alerta ao(s) administrador(es) quando chega uma avaliação crítica (nota ≤ limite). Não calcula nada, não persiste nada.
2. **`RelatorioAgendadoFunction`** — Timer Trigger (periodicidade configurável via `RELATORIO_AGENDADO_CRON`). Única responsabilidade: calcular médias/contagens das avaliações e persistir um novo relatório. Não envia e-mail, não recebe requisição.

Como nenhuma das duas funções precisa de HTTP, CDI ou ORM reativo, o módulo `functions/` deixa de depender do Quarkus inteiramente — vira um projeto Azure Functions Java puro (`azure-functions-java-library` + `azure-functions-maven-plugin`), usando `JdbcRelatorioDao` (JDBC simples) para acessar o Postgres compartilhado com o Serviço A.

### Consequências

- A fila `solicitacoes-relatorio` e o endpoint de solicitação sob demanda deixam de existir — não há mais forma de o admin pedir um relatório "avulso"; ele só recebe relatórios pela geração agendada.
- `infra/azure/main.bicep` não provisiona mais essa fila.
- O módulo `functions/` fica mais simples de explicar na avaliação: 2 componentes, 2 responsabilidades, sem mistura de estilos de código.
- Se o admin precisar de relatórios sob demanda no futuro, a forma mais simples de reintroduzir isso é um endpoint comum (síncrono) no Serviço A — não como função serverless — para não reabrir a mistura de responsabilidades identificada aqui.

**Nota:** esta ADR foi parcialmente substituída pela ADR-006 — o Serviço B voltou a usar Quarkus, mas a decisão de ter exatamente 2 funções (timer + queue), cada uma com responsabilidade única, continua valendo.

---

## ADR-006 — Quarkus de volta no Serviço B, via gatilho nativo fino + endpoint interno

- Data: 2026-07-24
- Estado: aceita

### Contexto

Objetivo de aprendizado do usuário: praticar Quarkus (conteúdo visto no curso) nas funções serverless, com deploy real na Azure. A restrição técnica que motivou a ADR-005 continua verdadeira: a extensão oficial `quarkus-azure-functions-http` só sabe fazer **HTTP trigger** — não existe suporte oficial do Quarkus para Timer trigger ou Queue trigger com CDI. Não dá para simplesmente "voltar a usar Quarkus" nas 2 funções como elas eram antes (Timer/Queue nativos) sem violar essa limitação.

A alternativa de tirar essas 2 responsabilidades do Azure Functions e rodar como serviço Quarkus sempre ativo (Container App) foi descartada: isso deixaria de ser serverless, violando a regra obrigatória do enunciado ("Deve, obrigatoriamente, implementar serverless").

### Decisão

Cada uma das 2 funções passa a ter duas partes:

1. **Gatilho nativo fino** — classe `@FunctionName` do modelo padrão do Azure Functions Java Worker (sem CDI), com uma única responsabilidade: disparar no trigger certo (Timer ou Queue) e repassar a chamada, via HTTP, para um endpoint interno.
   - `RelatorioAgendadoTrigger` (Timer) → `POST /internal/relatorio-agendado`
   - `FeedbackCriticoTrigger` (Queue) → `POST /internal/feedback-critico`
2. **Endpoint interno Quarkus** — recurso RESTEasy Reactive (`@Path("/internal/...")`), exposto via `quarkus-azure-functions-http` (função HTTP única que já existia na versão original do projeto), com toda a lógica de negócio de verdade: CDI (`@ApplicationScoped`), Panache (`AvaliacaoEntity`, `AdminEntity`, `RelatorioEntity`), injeção de dependência.
   - `RelatorioAgendadoResource` → delega para `RelatorioService` (calcula agregados, persiste o relatório).
   - `FeedbackCriticoResource` → busca e-mails dos admins via Panache e envia via `EmailService`.

As rotas `/internal/*` ficam protegidas por `InternalSecretValidator`, injetado em cada resource e checado no início do método via `@HeaderParam("X-Internal-Secret")` (comparado com `INTERNAL_TRIGGER_SECRET`) — sem isso, qualquer cliente externo poderia chamá-las diretamente, já que o `quarkus-azure-functions-http` expõe todas as rotas Quarkus atrás do mesmo endpoint HTTP público do Function App. (Uma primeira tentativa usando um `ContainerRequestFilter` JAX-RS global não bloqueava as requisições de forma confiável nos testes — a checagem explícita por endpoint é mais simples e verificadamente funciona.)

A chamada HTTP do gatilho nativo para o endpoint interno usa o hostname do próprio Function App (`WEBSITE_HOSTNAME`, injetado automaticamente pela Azure; `localhost:7071` como padrão local via Core Tools) — é uma chamada de function-to-function dentro do mesmo app, padrão comum quando um Azure Function precisa acionar lógica exposta via HTTP trigger.

### Consequências

- O módulo `functions/` volta a depender do Quarkus (CDI, Panache, RESTEasy Reactive, `quarkus-azure-functions-http`) — igual à primeira versão do projeto, mas agora sem a função de solicitação sob demanda (que segue removida, ADR-005).
- Continuam sendo exatamente 2 funções serverless no sentido do enunciado (2 gatilhos Azure Functions distintos — Timer e Queue), cada uma com responsabilidade única. A diferença é que agora a maior parte do código de cada uma é Quarkus real, não JDBC bruto.
- Adiciona uma chamada HTTP interna por execução (latência extra pequena, aceitável para o volume deste projeto) e uma dependência nova: `INTERNAL_TRIGGER_SECRET`, gerenciado como qualquer outro segredo (Key Vault em produção).
- `JdbcRelatorioDao` e o `EmailSender` sem CDI foram removidos; a lógica equivalente vive em `RelatorioService`/`EmailService` (CDI) e nas entidades Panache.
- Explicar essa arquitetura (gatilho fino + endpoint interno protegido) é parte do que deve constar no vídeo de demonstração e na documentação da avaliação — é a peça mais não óbvia do projeto.
