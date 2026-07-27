# Arquitetura — EduFeedback

## Status

Esta arquitetura foi definida em 2026-07-22, revisada no mesmo dia (ADR-005: redução do Serviço B de 4 para 2 funções), revisada em 2026-07-24 (ADR-006: Quarkus de volta no Serviço B via gatilho nativo fino + endpoint interno), revisada em 2026-07-26 (ADR-007: o gatilho fino deixou de ser Azure Functions e passou a ser Azure Container Apps Jobs) e revisada novamente em 2026-07-27 (ADR-008: camadas de Clean Architecture — `domain/application/infrastructure` — dentro de cada módulo de domínio, nos dois serviços). Mudanças estruturais devem ser registradas em `DECISIONS.md` antes de serem implementadas.

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
      → RelatorioAgendadoResource (infrastructure/web) → GerarRelatorioAgendadoUseCase (application)
      → Panache via repositório (AvaliacaoPanacheEntity lê, RelatorioPanacheEntity grava) → PostgreSQL

  → [Job "job-feedback-critico", Event trigger, KEDA azure-queue sobre "notificacoes-criticas"]
      → consome 1 mensagem da fila (az storage message get/delete)
      → POST /internal/feedback-critico (Container App interno, X-Internal-Secret)
      → FeedbackCriticoResource (infrastructure/web) → NotificarFeedbackCriticoUseCase (application)
      → AdminPanacheEntity (Panache, lê e-mails) + AzureEmailSender (infrastructure/email) → Azure Communication Services

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

Organizado por módulo de domínio (ADR-002). Dentro de cada módulo, camadas de Clean Architecture (ADR-008): `domain/` (entidade pura + portas), `application/` (caso de uso), `infrastructure/` (web, persistence, e conforme o módulo, security/messaging). Código compartilhado só existe quando há uso real em mais de um módulo.

```
backend/
├── src/
│   ├── main/
│   │   ├── java/br/com/edufeedback/
│   │   │   ├── auth/                                       ← login, JWT
│   │   │   │   ├── domain/
│   │   │   │   │   ├── Admin.java                          ← POJO puro
│   │   │   │   │   ├── AdminRepository.java                ← porta
│   │   │   │   │   └── TokenGerador.java                   ← porta
│   │   │   │   ├── application/
│   │   │   │   │   └── AutenticarAdminUseCase.java
│   │   │   │   └── infrastructure/
│   │   │   │       ├── web/
│   │   │   │       │   ├── AuthController.java
│   │   │   │       │   └── dto/
│   │   │   │       ├── persistence/
│   │   │   │       │   ├── AdminJpaEntity.java
│   │   │   │       │   ├── SpringDataAdminRepository.java
│   │   │   │       │   └── AdminRepositoryAdapter.java
│   │   │   │       └── security/
│   │   │   │           └── JwtTokenGerador.java            ← implementa TokenGerador
│   │   │   ├── avaliacao/                                  ← recebimento de feedback
│   │   │   │   ├── domain/
│   │   │   │   │   ├── Avaliacao.java, Urgencia.java
│   │   │   │   │   ├── AvaliacaoRepository.java            ← porta
│   │   │   │   │   └── NotificacaoCriticaPublisher.java    ← porta
│   │   │   │   ├── application/
│   │   │   │   │   └── RegistrarAvaliacaoUseCase.java
│   │   │   │   └── infrastructure/
│   │   │   │       ├── web/ (AvaliacaoController.java + dto/)
│   │   │   │       ├── persistence/ (AvaliacaoJpaEntity, SpringDataAvaliacaoRepository, AvaliacaoRepositoryAdapter)
│   │   │   │       └── messaging/
│   │   │   │           └── AzureQueueNotificacaoCriticaPublisher.java
│   │   │   ├── relatorio/                                  ← consulta de relatório (só leitura)
│   │   │   │   ├── domain/
│   │   │   │   │   ├── Relatorio.java, TipoRelatorio.java, StatusRelatorio.java
│   │   │   │   │   └── RelatorioRepository.java            ← porta
│   │   │   │   ├── application/
│   │   │   │   │   └── ConsultarRelatorioUseCase.java
│   │   │   │   └── infrastructure/
│   │   │   │       ├── web/ (RelatorioController.java + dto/)
│   │   │   │       └── persistence/ (RelatorioJpaEntity, SpringDataRelatorioRepository, RelatorioRepositoryAdapter)
│   │   │   ├── config/                   ← segurança, CORS, beans globais (cross-cutting, fora de módulo)
│   │   │   └── shared/                   ← exceções e handler global (cross-cutting, fora de módulo)
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-dev.properties
│   │       └── db/migration/             ← V001, V002, V003
│   └── test/
│       └── java/br/com/edufeedback/      ← testes espelhando o pacote da classe testada
├── pom.xml
└── Dockerfile
```

### Backend — Serviço B (`functions/`)

Exatamente 2 endpoints internos, cada um com uma única responsabilidade (ADR-005), cada um acionado por um Container Apps Job dedicado — gatilho fino, sem lógica de negócio, definido só em `infra/azure/main.bicep` (ADR-007). O módulo `functions/` só contém a parte Quarkus (CDI, Panache) — não há mais classes de gatilho em Java. Camadas de Clean Architecture (ADR-008) dentro de cada módulo: `domain/` (POJO/record puro + portas), `application/` (caso de uso — só nos módulos com regra de negócio própria), `infrastructure/` (web, persistence, e conforme o módulo, email). `avaliacao` e `admin` são módulos só de leitura, dado de apoio a `relatorio`/`notificacao` — sem `application/`. A camada de aplicação nunca chama método estático de active record do Panache; todo acesso a dado passa por uma classe `*PanacheRepository` (padrão repositório do Panache) injetada num adapter que implementa a porta.

```
functions/
├── src/main/java/br/com/edufeedback/functions/
│   ├── notificacao/
│   │   ├── domain/
│   │   │   ├── FeedbackCritico.java                    ← value object puro
│   │   │   └── EmailSender.java                        ← porta
│   │   ├── application/
│   │   │   └── NotificarFeedbackCriticoUseCase.java
│   │   └── infrastructure/
│   │       ├── web/
│   │       │   ├── FeedbackCriticoResource.java        ← endpoint interno, acionado pelo job-feedback-critico
│   │       │   └── FeedbackCriticoPayload.java         ← DTO do corpo da fila/requisição
│   │       └── email/
│   │           └── AzureEmailSender.java               ← implementa EmailSender (Azure Communication Services)
│   ├── relatorio/
│   │   ├── domain/
│   │   │   ├── Relatorio.java, TipoRelatorio.java, StatusRelatorio.java
│   │   │   ├── Agregados.java, AvaliacaoResumo.java
│   │   │   └── RelatorioRepository.java                ← porta
│   │   ├── application/
│   │   │   └── GerarRelatorioAgendadoUseCase.java       ← regra de negócio
│   │   └── infrastructure/
│   │       ├── web/
│   │       │   └── RelatorioAgendadoResource.java      ← endpoint interno, acionado pelo job-relatorio-agendado
│   │       └── persistence/
│   │           ├── RelatorioPanacheEntity.java, RelatorioPanacheRepository.java, RelatorioRepositoryAdapter.java
│   │           └── AgregadosJsonSerializer.java        ← serializa Agregados → JSON (detalhe de persistência)
│   ├── avaliacao/                                       ← só leitura, apoio ao relatório
│   │   ├── domain/
│   │   │   ├── Avaliacao.java
│   │   │   └── AvaliacaoRepository.java                ← porta
│   │   └── infrastructure/persistence/
│   │       └── AvaliacaoPanacheEntity.java, AvaliacaoPanacheRepository.java, AvaliacaoRepositoryAdapter.java
│   ├── admin/                                           ← só leitura, apoio à notificação
│   │   ├── domain/
│   │   │   └── AdminRepository.java                    ← porta (listarEmails)
│   │   └── infrastructure/persistence/
│   │       └── AdminPanacheEntity.java, AdminPanacheRepository.java, AdminRepositoryAdapter.java
│   └── shared/
│       └── InternalSecretValidator.java                ← valida X-Internal-Secret nos endpoints internos (cross-cutting)
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
- Os módulos `relatorio` e `notificacao` do Serviço B não têm dependência entre si; compartilham apenas `shared/InternalSecretValidator`. `relatorio` depende do módulo `avaliacao` só através da porta `avaliacao.domain.AvaliacaoRepository`; `notificacao` depende do módulo `admin` só através da porta `admin.domain.AdminRepository` — única forma permitida de um módulo depender de outro (ADR-002).

## Decisões de arquitetura

Consultar `DECISIONS.md`. Toda mudança de stack ou padrão estrutural exige uma ADR aprovada antes da implementação.
