# Arquitetura — EduFeedback

## Status

Esta arquitetura foi definida em 2026-07-22, revisada no mesmo dia (ADR-005: redução do Serviço B de 4 para 2 funções), revisada em 2026-07-24 (ADR-006: Quarkus de volta no Serviço B via gatilho nativo fino + endpoint interno) e revisada novamente em 2026-07-26 (ADR-007: o gatilho fino deixou de ser Azure Functions e passou a ser Azure Container Apps Jobs). Mudanças estruturais devem ser registradas em `DECISIONS.md` antes de serem implementadas.

## Visão geral

```
Estudante (cliente externo)
  → POST /avaliação (público, sem autenticação)
      → Serviço A — API (Spring Boot 3, módulo avaliacao)
          → PostgreSQL (tabela avaliacoes)
          → nota <= 3? → enfileira em "notificacoes-criticas" (Azure Storage Queue),
             com descrição, urgência e data de envio

Administrador (cliente externo)
  → POST /auth/login → Serviço A → JWT
  → GET /relatorios/{id} (JWT) → Serviço A → PostgreSQL (tabela relatorios)

Serviço B — Container App interno (Quarkus, sem ingress externo) + 2 Container
Apps Jobs (gatilho fino, ver ADR-007)
  → [Job "job-relatorio-agendado", Schedule trigger, cron semanal]
      → POST /internal/relatorio-agendado (Container App interno, X-Internal-Secret)
      → RelatorioAgendadoResource (Quarkus) → RelatorioService (CDI)
      → Panache: AvaliacaoEntity (lê) + RelatorioEntity (grava) → PostgreSQL

  → [Job "job-feedback-critico", Event trigger, KEDA azure-queue sobre "notificacoes-criticas"]
      → consome 1 mensagem da fila (az storage message get/delete)
      → POST /internal/feedback-critico (Container App interno, X-Internal-Secret)
      → FeedbackCriticoResource (Quarkus) → AdminEntity (Panache, lê e-mails)
      → EmailService (CDI) → Azure Communication Services (e-mail ao admin)

  → Sem integrações externas além de Azure Storage Queue e Azure Communication Services no escopo inicial
```

## Stack

| Camada         | Tecnologia                     |
|----------------|-------------------------------|
| Backend (API)  | Java 21 + Spring Boot 3.3.x (Maven) |
| Backend (Serviço B) | Java 21 + Quarkus 3.x (Maven) — app HTTP comum, sem dependência de runtime serverless próprio |
| Gatilho fino (Serviço B) | Azure Container Apps Jobs (Schedule + Event/KEDA) — scripts `sh`/`az cli` inline no Bicep, sem código Java |
| Frontend       | Não aplicável (sem frontend) |
| Banco de dados | PostgreSQL 16 (compartilhado pelos dois serviços) |
| Autenticação   | JWT stateless (HS256, `io.jsonwebtoken:jjwt`) — só no Serviço A. Serviço B usa um segredo compartilhado próprio (`INTERNAL_TRIGGER_SECRET`) só entre gatilho e endpoint interno |
| Migrations     | Flyway (propriedade exclusiva do Serviço A) |
| Deploy         | Serviço A → Azure Container Apps (Container App público) · Serviço B → Azure Container Apps (Container App interno + 2 Jobs) |
| IA integrada   | Não aplicável |

## Estrutura de módulos

### Backend — Serviço A (`backend/`)

Organizado por módulo de domínio. Cada módulo é um pacote isolado com suas próprias camadas internas. Código compartilhado só existe quando há uso real em mais de um módulo.

```
backend/
├── src/
│   ├── main/
│   │   ├── java/br/com/edufeedback/
│   │   │   ├── auth/                     ← login, JWT, entidade Admin
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── JwtService.java
│   │   │   │   ├── Admin.java
│   │   │   │   ├── AdminRepository.java
│   │   │   │   └── dto/
│   │   │   ├── avaliacao/                ← recebimento de feedback
│   │   │   │   ├── AvaliacaoController.java
│   │   │   │   ├── AvaliacaoService.java
│   │   │   │   ├── Avaliacao.java
│   │   │   │   ├── AvaliacaoRepository.java
│   │   │   │   ├── NotificacaoCriticaPublisher.java
│   │   │   │   └── dto/
│   │   │   ├── relatorio/                ← consulta de relatório
│   │   │   │   ├── RelatorioController.java
│   │   │   │   ├── Relatorio.java
│   │   │   │   ├── RelatorioRepository.java
│   │   │   │   └── dto/
│   │   │   ├── config/                   ← segurança, CORS, beans globais
│   │   │   └── shared/                   ← exceções e handler global
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       └── db/migration/             ← V001, V002, V003
│   └── test/
│       └── java/br/com/edufeedback/      ← testes por módulo
├── pom.xml
└── Dockerfile
```

### Backend — Serviço B (`functions/`)

Exatamente 2 endpoints internos, cada um com uma única responsabilidade (ADR-005), cada um acionado por um Container Apps Job dedicado — gatilho fino, sem lógica de negócio, definido só em `infra/azure/main.bicep` (ADR-007). O módulo `functions/` só contém a parte Quarkus (CDI, Panache) — não há mais classes de gatilho em Java.

```
functions/
├── src/main/java/br/com/edufeedback/functions/
│   ├── notificacao/
│   │   ├── FeedbackCriticoResource.java       ← endpoint interno Quarkus (CDI), acionado pelo job-feedback-critico
│   │   └── FeedbackCriticoPayload.java        ← DTO do corpo da fila/requisição
│   ├── relatorio/
│   │   ├── RelatorioAgendadoResource.java     ← endpoint interno Quarkus (CDI), acionado pelo job-relatorio-agendado
│   │   ├── RelatorioService.java              ← regra de negócio (CDI, Panache)
│   │   ├── RelatorioEntity.java               ← entidade Panache (tabela relatorios)
│   │   ├── TipoRelatorio.java / StatusRelatorio.java
│   ├── avaliacao/
│   │   └── AvaliacaoEntity.java               ← entidade Panache, só leitura (tabela avaliacoes)
│   ├── admin/
│   │   └── AdminEntity.java                   ← entidade Panache, só leitura (tabela admins)
│   └── shared/
│       ├── InternalSecretValidator.java       ← valida X-Internal-Secret nos endpoints internos
│       ├── EmailService.java                  ← CDI, Azure Communication Services
│       ├── Agregados.java / AvaliacaoResumo.java
│       └── AgregadosJsonSerializer.java
├── src/main/resources/application.properties  ← config Quarkus (datasource, segredo interno)
├── Dockerfile                                  ← build multistage, app HTTP comum (porta 8080)
└── pom.xml
```

Os 2 gatilhos finos (`job-relatorio-agendado`, `job-feedback-critico`) vivem só como recursos `Microsoft.App/jobs` em `infra/azure/main.bicep` — scripts `sh`/`az cli` inline, sem módulo Java próprio (ver ADR-007).

### Frontend

Não aplicável — cliente é externo a este repositório.

## Fronteiras entre módulos

- Um módulo não acessa diretamente o código interno de outro módulo.
- Comunicação entre o Serviço A e o Serviço B ocorre via banco de dados compartilhado e a fila `notificacoes-criticas` — nunca chamada HTTP síncrona direta entre os dois serviços.
- Dentro do Serviço B, a comunicação entre o Job (gatilho fino) e o endpoint interno é uma chamada HTTP autenticada por segredo compartilhado (`INTERNAL_TRIGGER_SECRET`) para o Container App interno (sem ingress externo) — não é CDI, porque o Job roda num container separado, fora do processo Quarkus.
- `RelatorioAgendadoResource`/`RelatorioService` e `FeedbackCriticoResource`/`EmailService` não têm dependência entre si; compartilham apenas classes utilitárias em `shared/`.

## Decisões de arquitetura

Consultar `DECISIONS.md`. Toda mudança de stack ou padrão estrutural exige uma ADR aprovada antes da implementação.
