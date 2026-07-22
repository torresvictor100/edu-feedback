# Registro de decisões — EduFeedback

Estados permitidos: `proposta`, `aceita`, `substituída` ou `rejeitada`.

---

## ADR-001 — Stack principal

- Data: 2026-07-22
- Estado: aceita

### Contexto

O projeto precisará atender ao requisito obrigatório do Tech Challenge Fase 4: rodar em ambiente de nuvem e implementar no mínimo duas funções serverless com responsabilidade única, além de uma API para login e recebimento de feedback.

### Decisão

Arquitetura de dois serviços: Java 21 + Spring Boot 3 + Maven + Spring Data JPA + Flyway + PostgreSQL para a API principal (login, feedback, consulta de relatório); Java 21 + Azure Functions Java Worker puro (sem framework de aplicação) + Maven para as 2 funções serverless (timer de relatório agendado, queue de notificação crítica — ver ADR-005), compartilhando o mesmo banco PostgreSQL.

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
