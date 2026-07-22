# Registro de decisões — EduFeedback

Estados permitidos: `proposta`, `aceita`, `substituída` ou `rejeitada`.

---

## ADR-001 — Stack principal

- Data: 2026-07-22
- Estado: aceita

### Contexto

O projeto precisará atender ao requisito obrigatório do Tech Challenge Fase 4: rodar em ambiente de nuvem e implementar no mínimo duas funções serverless com responsabilidade única, além de uma API para login e recebimento de feedback.

### Decisão

Arquitetura de dois serviços: Java 21 + Spring Boot 3 + Maven + Spring Data JPA + Flyway + PostgreSQL para a API principal (login, feedback, consulta de relatório); Java 21 + Quarkus 3 + Maven para as funções serverless (timer, HTTP, queue, notificação), compartilhando o mesmo banco PostgreSQL.

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
- Estado: aceita

### Contexto

O desafio exige no mínimo duas funções serverless com responsabilidade única. O desenho aprovado tem 4 funções: timer (relatório agendado), HTTP (solicitação sob demanda), queue (processamento do relatório) e queue (notificação de feedback crítico). A extensão oficial `quarkus-azure-functions-http` só suporta função com **HTTP trigger** — ela expõe a API REST inteira do Quarkus atrás de uma única function HTTP, e não tem suporte nativo a Timer/Queue trigger com CDI/Panache.

### Decisão

No módulo `functions/`, a função HTTP (solicitação sob demanda) usa Quarkus de verdade (RESTEasy Reactive + Panache + CDI) via `quarkus-azure-functions-http`. As funções de Timer e Queue trigger usam o modelo padrão do Azure Functions Java Worker (`azure-functions-java-library`, anotações `@FunctionName`), no mesmo módulo Maven, acessando o Postgres via JDBC simples em vez de CDI/Panache — essas funções não rodam dentro do container ArC do Quarkus.

### Consequências

O módulo `functions/` mistura dois estilos de código (Quarkus CDI para a função HTTP, JDBC simples para as demais). Isso é documentado em `ARCHITECTURE.md` e deve ser explicado na avaliação do desafio como parte do "modelo de cloud escolhido e dos componentes envolvidos". Qualquer nova função Timer/Queue segue o mesmo padrão JDBC simples das já existentes, reaproveitando `shared/JdbcRelatorioDao.java`.

Nenhuma ADR adicional na criação inicial.
