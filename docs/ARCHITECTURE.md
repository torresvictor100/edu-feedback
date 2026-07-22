# Arquitetura — EduFeedback

## Status

Esta arquitetura foi definida em 2026-07-22 e revisada no mesmo dia (ver ADR-005 em `DECISIONS.md`: redução do Serviço B de 4 para 2 funções, sem Quarkus). Mudanças estruturais devem ser registradas em `DECISIONS.md` antes de serem implementadas.

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

Serviço B — Funções serverless (Azure Functions Java Worker puro, sem framework de aplicação)
  → [Timer trigger] RelatorioAgendadoFunction: gera relatório periódico → grava em PostgreSQL (relatorios)
  → [Queue trigger] FeedbackCriticoFunction: processa "notificacoes-criticas" → Azure Communication Services (e-mail ao admin)

  → Sem integrações externas além de Azure Storage Queue e Azure Communication Services no escopo inicial
```

## Stack

| Camada         | Tecnologia                     |
|----------------|-------------------------------|
| Backend (API)  | Java 21 + Spring Boot 3.3.x (Maven) |
| Backend (funções) | Java 21 + Azure Functions Java Worker puro, sem framework de aplicação (Maven) |
| Frontend       | Não aplicável (sem frontend) |
| Banco de dados | PostgreSQL 16 (compartilhado pelos dois serviços) |
| Autenticação   | JWT stateless (HS256, `io.jsonwebtoken:jjwt`) — só no Serviço A |
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

Exatamente 2 funções, cada uma com um único trigger e uma única responsabilidade (ver ADR-005). Nenhum framework de aplicação — classes simples com anotações do `azure-functions-java-library`, acesso a dados via JDBC puro.

```
functions/
├── src/main/java/br/com/edufeedback/functions/
│   ├── timer/
│   │   └── RelatorioAgendadoFunction.java    ← Timer Trigger: só gera e persiste o relatório
│   ├── notificacao/
│   │   └── FeedbackCriticoFunction.java      ← Queue Trigger: só envia o e-mail de alerta
│   └── shared/                                ← JDBC, cliente de e-mail, serialização do relatório
│       ├── JdbcRelatorioDao.java
│       ├── Agregados.java
│       ├── AgregadosJsonSerializer.java
│       └── EmailSender.java
├── host.json
├── local.settings.json.example
└── pom.xml
```

### Frontend

Não aplicável — cliente é externo a este repositório.

## Fronteiras entre módulos

- Um módulo não acessa diretamente o código interno de outro módulo.
- Comunicação entre o Serviço A e o Serviço B ocorre exclusivamente via banco de dados compartilhado e a fila `notificacoes-criticas` — nunca chamada HTTP síncrona direta entre os dois.
- Dentro do Serviço B, `RelatorioAgendadoFunction` e `FeedbackCriticoFunction` não têm nenhuma dependência entre si; compartilham apenas classes utilitárias simples em `shared/` (acesso a dados e envio de e-mail), reaproveitadas por composição, não por herança ou acoplamento de responsabilidade.

## Decisões de arquitetura

Consultar `DECISIONS.md`. Toda mudança de stack ou padrão estrutural exige uma ADR aprovada antes da implementação.
