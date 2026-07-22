# Arquitetura — EduFeedback

## Status

Esta arquitetura foi definida em 2026-07-22. Mudanças estruturais devem ser registradas em `DECISIONS.md` antes de serem implementadas.

## Visão geral

```
Estudante (cliente externo)
  → POST /avaliação (público, sem autenticação)
      → Serviço A — API (Spring Boot 3, módulo avaliacao)
          → PostgreSQL (tabela avaliacoes)
          → nota <= 3? → enfileira em "notificacoes-criticas" (Azure Storage Queue)

Administrador (cliente externo)
  → POST /auth/login → Serviço A → JWT
  → GET /relatorios/{id} (JWT) → Serviço A → PostgreSQL (tabela relatorios)

Serviço B — Funções serverless (Quarkus + Azure Functions Java Worker)
  → [Timer trigger] gera relatório periódico → grava em PostgreSQL (relatorios)
  → [HTTP trigger, JWT] recebe solicitação sob demanda → enfileira em "solicitacoes-relatorio"
  → [Queue trigger] processa "solicitacoes-relatorio" → lê avaliacoes → grava relatorio pronto → Azure Communication Services (e-mail ao admin)
  → [Queue trigger] processa "notificacoes-criticas" → Azure Communication Services (e-mail ao admin)

  → Sem integrações externas além de Azure Storage Queue e Azure Communication Services no escopo inicial
```

## Stack

| Camada         | Tecnologia                     |
|----------------|-------------------------------|
| Backend (API)  | Java 21 + Spring Boot 3.3.x (Maven) |
| Backend (funções) | Java 21 + Quarkus 3.x + Azure Functions Java Worker (Maven) |
| Frontend       | Não aplicável (sem frontend) |
| Banco de dados | PostgreSQL 16 (compartilhado pelos dois serviços) |
| Autenticação   | JWT stateless (HS256, `io.jsonwebtoken:jjwt`) |
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

```
functions/
├── src/main/java/br/com/edufeedback/functions/
│   ├── relatorio/                        ← função HTTP (Quarkus real: CDI + Panache)
│   │   ├── SolicitarRelatorioResource.java
│   │   └── RelatorioEntity.java
│   ├── timer/                            ← função Timer (Azure Functions Java Worker puro)
│   │   └── RelatorioAgendadoFunction.java
│   ├── queue/                            ← funções Queue Trigger (Azure Functions Java Worker puro)
│   │   └── ProcessarRelatorioFunction.java
│   ├── notificacao/
│   │   └── FeedbackCriticoFunction.java
│   └── shared/                           ← JDBC, cliente de e-mail, cálculo de agregados
│       ├── JdbcRelatorioDao.java
│       ├── AgregadosCalculator.java
│       └── EmailSender.java
├── src/main/resources/application.properties
├── host.json
├── local.settings.json.example
└── pom.xml
```

### Frontend

Não aplicável — cliente é externo a este repositório.

## Fronteiras entre módulos

- Um módulo não acessa diretamente o código interno de outro módulo.
- Comunicação entre o Serviço A e o Serviço B ocorre exclusivamente via banco de dados compartilhado e filas — nunca chamada HTTP síncrona direta entre os dois.
- Dentro do Serviço B, a função HTTP (Quarkus/CDI) e as funções Timer/Queue (Azure Functions Java Worker puro) não compartilham container de injeção de dependência; compartilham apenas classes utilitárias simples em `shared/`.

## Decisões de arquitetura

Consultar `DECISIONS.md`. Toda mudança de stack ou padrão estrutural exige uma ADR aprovada antes da implementação.
