# Arquitetura — EduFeedback

## Status

Esta arquitetura foi definida em 2026-07-22, revisada no mesmo dia (ADR-005: redução do Serviço B de 4 para 2 funções) e revisada novamente em 2026-07-24 (ADR-006: Quarkus de volta no Serviço B via gatilho nativo fino + endpoint interno). Mudanças estruturais devem ser registradas em `DECISIONS.md` antes de serem implementadas.

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

Serviço B — Funções serverless (gatilho nativo fino + endpoint interno Quarkus)
  → [Timer trigger nativo] RelatorioAgendadoTrigger
      → POST /internal/relatorio-agendado (mesmo Function App, X-Internal-Secret)
      → RelatorioAgendadoResource (Quarkus) → RelatorioService (CDI)
      → Panache: AvaliacaoEntity (lê) + RelatorioEntity (grava) → PostgreSQL

  → [Queue trigger nativo] FeedbackCriticoTrigger: processa "notificacoes-criticas"
      → POST /internal/feedback-critico (mesmo Function App, X-Internal-Secret)
      → FeedbackCriticoResource (Quarkus) → AdminEntity (Panache, lê e-mails)
      → EmailService (CDI) → Azure Communication Services (e-mail ao admin)

  → Sem integrações externas além de Azure Storage Queue e Azure Communication Services no escopo inicial
```

## Stack

| Camada         | Tecnologia                     |
|----------------|-------------------------------|
| Backend (API)  | Java 21 + Spring Boot 3.3.x (Maven) |
| Backend (funções) | Java 21 + Quarkus 3.x (lógica de negócio) + Azure Functions Java Worker puro (gatilhos nativos) — Maven |
| Frontend       | Não aplicável (sem frontend) |
| Banco de dados | PostgreSQL 16 (compartilhado pelos dois serviços) |
| Autenticação   | JWT stateless (HS256, `io.jsonwebtoken:jjwt`) — só no Serviço A. Serviço B usa um segredo compartilhado próprio (`INTERNAL_TRIGGER_SECRET`) só entre gatilho e endpoint interno |
| Migrations     | Flyway (propriedade exclusiva do Serviço A) |
| Deploy         | Serviço A → Azure Container Apps · Serviço B → Azure Functions |
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

Exatamente 2 funções, cada uma com um único trigger e uma única responsabilidade (ADR-005). Cada função tem duas partes (ADR-006): um gatilho nativo fino (sem CDI) e um endpoint Quarkus interno (CDI, Panache) que faz o trabalho de verdade.

```
functions/
├── src/main/java/br/com/edufeedback/functions/
│   ├── timer/
│   │   └── RelatorioAgendadoTrigger.java      ← Timer Trigger nativo: só repassa via HTTP
│   ├── notificacao/
│   │   ├── FeedbackCriticoTrigger.java        ← Queue Trigger nativo: só repassa via HTTP
│   │   ├── FeedbackCriticoResource.java       ← endpoint interno Quarkus (CDI)
│   │   └── FeedbackCriticoPayload.java        ← DTO do corpo da fila/requisição
│   ├── relatorio/
│   │   ├── RelatorioAgendadoResource.java     ← endpoint interno Quarkus (CDI)
│   │   ├── RelatorioService.java              ← regra de negócio (CDI, Panache)
│   │   ├── RelatorioEntity.java               ← entidade Panache (tabela relatorios)
│   │   ├── TipoRelatorio.java / StatusRelatorio.java
│   ├── avaliacao/
│   │   └── AvaliacaoEntity.java               ← entidade Panache, só leitura (tabela avaliacoes)
│   ├── admin/
│   │   └── AdminEntity.java                   ← entidade Panache, só leitura (tabela admins)
│   └── shared/
│       ├── InternalHttpCaller.java            ← chamada HTTP gatilho → endpoint interno
│       ├── InternalSecretValidator.java       ← valida X-Internal-Secret nos endpoints internos
│       ├── EmailService.java                  ← CDI, Azure Communication Services
│       ├── Agregados.java / AvaliacaoResumo.java
│       └── AgregadosJsonSerializer.java
├── src/main/resources/application.properties  ← config Quarkus (datasource, segredo interno)
├── host.json
├── local.settings.json.example
└── pom.xml
```

### Frontend

Não aplicável — cliente é externo a este repositório.

## Fronteiras entre módulos

- Um módulo não acessa diretamente o código interno de outro módulo.
- Comunicação entre o Serviço A e o Serviço B ocorre via banco de dados compartilhado e a fila `notificacoes-criticas` — nunca chamada HTTP síncrona direta entre os dois serviços.
- Dentro do Serviço B, a comunicação entre gatilho nativo e endpoint interno é uma chamada HTTP autenticada por segredo compartilhado (`INTERNAL_TRIGGER_SECRET`) para o próprio Function App — não é CDI, porque o gatilho nativo não roda dentro do container do Quarkus.
- `RelatorioAgendadoResource`/`RelatorioService` e `FeedbackCriticoResource`/`EmailService` não têm dependência entre si; compartilham apenas classes utilitárias em `shared/`.

## Decisões de arquitetura

Consultar `DECISIONS.md`. Toda mudança de stack ou padrão estrutural exige uma ADR aprovada antes da implementação.
