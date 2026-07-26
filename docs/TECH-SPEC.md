# Especificação Técnica — EduFeedback

> **Documento obrigatório.** Criado na fundação do projeto. Toda decisão de tecnologia futura deve ser registrada aqui antes de ser implementada.
>
> Antes de implementar qualquer tecnologia listada neste documento, leia o material de curso correspondente em `/home/joao/dev/skils/rubro-spec/KNOWLEDGE-BASE.md`.

- **Projeto:** EduFeedback (`edu-feedback`)
- **Data de criação:** 2026-07-22
- **Plataforma de execução:** Microsoft Azure

## Contexto: arquitetura de dois serviços

Este projeto é a resposta ao Tech Challenge Fase 4 (FIAP PosTech): plataforma de feedback de aulas com exigência obrigatória de **serverless + cloud**, no mínimo duas funções com responsabilidade única. Por isso o backend é dividido em **dois serviços independentes**, cada um com seu próprio deploy:

| Serviço | Framework | Responsabilidade | Deploy |
|---|---|---|---|
| **Serviço A — API principal** | Spring Boot 3 | Login admin (JWT), recebimento de feedback, consulta de relatórios prontos | Azure Container Apps (Container App público) |
| **Serviço B — Componentes serverless** | Gatilho fino (Azure Container Apps Jobs) + endpoint interno Quarkus | Geração agendada de relatório, notificação de feedback crítico | Azure Container Apps (Container App interno + 2 Jobs) |

Os dois serviços compartilham o mesmo banco PostgreSQL. O Serviço B tem exatamente **2 componentes serverless com responsabilidade única** (ver ADR-005, ADR-006 e ADR-007 em `DECISIONS.md`) — mapeando direto nas duas automações que o enunciado do desafio pede: "o envio de notificações e a geração de relatórios". Cada um combina um gatilho fino (Azure Container Apps Job — Schedule ou Event/KEDA) com um endpoint Quarkus interno que concentra a lógica de negócio de verdade (CDI, Panache).

---

## 1. Backend

### 1.1 Serviço A — API principal

| Campo | Valor |
|---|---|
| **Framework** | Spring Boot |
| **Linguagem** | Java |
| **Versão** | Spring Boot 3.3.x / Java 21 |
| **Build tool** | Maven 3.9.x |

**Por que esta escolha:** ecossistema maduro, ampla adoção, integração nativa com Spring Security e Spring Data JPA — adequado para a API de autenticação e CRUD de feedback, que não tem restrição de cold start (roda em Container App sempre ativo).

### 1.2 Serviço B — Componentes serverless

| Campo | Valor |
|---|---|
| **Framework** | Quarkus 3.x (lógica de negócio, endpoints internos) + Azure Container Apps Jobs (gatilho fino, sem código Java) |
| **Linguagem** | Java (endpoints) — os 2 Jobs são só script `sh`/`az cli` definido em `infra/azure/main.bicep` |
| **Versão** | Quarkus 3.x / Java 21 |
| **Build tool** | Maven 3.9.x (`quarkus-maven-plugin`) |

**Por que esta escolha:** objetivo explícito de praticar Quarkus (conteúdo do curso) com deploy real na Azure. Depois de testar o modelo híbrido Quarkus + Azure Functions Java Worker (ADR-006), o gatilho fino migrou para Azure Container Apps Jobs (ADR-007) — elimina a necessidade de uma segunda plataforma de deploy só para hospedar 2 gatilhos, mantendo o requisito obrigatório de serverless (Jobs são cobrados por execução, `minExecutions: 0`). Cada componente combina duas peças:
- um **gatilho fino** (Azure Container Apps Job — `Schedule` ou `Event`/KEDA — sem lógica de negócio) que só dispara no momento certo e repassa a chamada via HTTP;
- um **endpoint interno Quarkus** (`/internal/...`, protegido por segredo compartilhado, exposto só no Container App interno sem ingress externo) que concentra toda a lógica de negócio de verdade: CDI, Panache, injeção de dependência.

**Os 2 componentes (responsabilidade única cada):**
1. **Notificação de feedback crítico** — Job `job-feedback-critico` (`Event` trigger, escalado pelo KEDA via profundidade da fila `notificacoes-criticas`) consome 1 mensagem e repassa para `FeedbackCriticoResource` (`POST /internal/feedback-critico`), que usa `EmailService` (CDI) e `AdminEntity` (Panache) para enviar o e-mail.
2. **Geração de relatório agendado** — Job `job-relatorio-agendado` (`Schedule` trigger, cron semanal) repassa para `RelatorioAgendadoResource` (`POST /internal/relatorio-agendado`), que usa `RelatorioService` (CDI) e as entidades Panache `AvaliacaoEntity`/`RelatorioEntity` para calcular e persistir o relatório.

**Regras obrigatórias (ambos os serviços):**
- Organizar por módulo de domínio, não por camada técnica.
- Controller (Serviço A) / resource Quarkus (Serviço B): recebe a chamada, valida entrada (`InternalSecretValidator` nos endpoints internos), delega a lógica de negócio. O gatilho fino (Job) não tem lógica de negócio nenhuma.
- Migrations Flyway pertencem exclusivamente ao Serviço A (dono do schema); o Serviço B só lê/escreve nas tabelas já migradas via Panache, nunca cria ou altera schema (`quarkus.hibernate-orm.database.generation=none` em dev/prod).
- Segredos somente em `.env` ou variável de ambiente / Key Vault. Nunca versionados — inclui `INTERNAL_TRIGGER_SECRET`, exclusivo da comunicação gatilho→endpoint interno, e `storage-connection-string`, exclusivo do Job de feedback crítico (autenticação do scale rule KEDA e dos comandos `az storage message`).
- Testes de integração usam Testcontainers (Postgres, via Quarkus Dev Services no Serviço B) nos dois serviços.

**Material de referência:**
- **REST** — controllers Spring (`/home/joao/dev/skils/rubro-spec/pdf/APIs RESTful /CURSO-COMPLETO-APIS-RESTFUL.md`)
- **QUARKUS** — resources, CDI e Panache do Serviço B (`/home/joao/dev/skils/rubro-spec/pdf/ Quarkus/CURSO-COMPLETO-QUARKUS.md`)
- **SERVERLESS** — padrões de função única responsabilidade, event-driven (`/home/joao/dev/skils/rubro-spec/pdf/Serverless Computing/CURSO-COMPLETO-SERVERLESS.md`)
- **AZURE** — configuração do Azure Container Apps Jobs (`/home/joao/dev/skils/rubro-spec/pdf/Deploy em Azure/CURSO-COMPLETO-DEPLOY-AZURE.md`)

---

## 2. Banco de Dados

### PostgreSQL

| Campo | Valor |
|---|---|
| **Banco** | PostgreSQL |
| **Tipo** | Relacional SQL |
| **Posição no CAP** | CA (nó único gerenciado, consistência forte, prioriza disponibilidade dentro da região) |
| **Versão** | 16 |

**Por que esta escolha:** os dados são estruturados e relacionais (avaliações, relatórios, administradores) e precisam de agregações (médias, contagens por dia/urgência) — SQL com índices é o ajuste natural. Compartilhado pelos dois serviços para evitar duplicação/sincronização de dados entre API e funções.

**Regras obrigatórias:**
- Migrations versionadas com Flyway, de propriedade exclusiva do Serviço A.
- O Serviço B (Quarkus) nunca roda migration; conecta-se ao schema já existente via Panache, com `database.generation=none`.
- Nenhuma query SQL nativa sem necessidade; preferir JPQL/Panache nos dois serviços.

**Material de referência:**
- ID `JPA-NOSQL`
- ID `MODELAGEM-BD`

---

## 3. Autenticação e Autorização

| Campo | Valor |
|---|---|
| **Mecanismo** | JWT stateless (HS256), sem refresh token nesta primeira versão |
| **Biblioteca** | `io.jsonwebtoken:jjwt` (usada nos dois serviços para emitir/validar com o mesmo segredo compartilhado) |

**Por que esta escolha:** stateless, escalável horizontalmente sem session store compartilhado, compatível com Azure Container Apps e validável de forma leve dentro de uma função serverless sem precisar de um provedor OIDC externo.

**Regras obrigatórias:**
- `POST /avaliação` é público, sem autenticação — contrato do enunciado do desafio.
- Login (`POST /auth/login`) e consulta de relatório (`GET /relatorios/{id}`) exigem `ADMIN` autenticado no Serviço A.
- O Serviço B não valida JWT (não tem conceito de usuário). Os endpoints internos (`/internal/*`), expostos só pelo Container App interno (sem ingress externo), são protegidos por um segredo compartilhado separado (`INTERNAL_TRIGGER_SECRET`, cabeçalho `X-Internal-Secret`), verificado por `InternalSecretValidator` — só os 2 Container Apps Jobs conhecem esse valor.
- Segredo `JWT_SECRET` nunca versionado; usado apenas pelo Serviço A.
- Senhas de administrador com hash BCrypt.

**Material de referência:**
- ID `SEGURANCA`

---

## 4. APIs e Comunicação

| Estilo | Tecnologia | Quando usar |
|---|---|---|
| REST | Spring MVC (Serviço A) | Login, recebimento de feedback, consulta de relatório |
| REST | Quarkus RESTEasy Reactive (Serviço B) | Endpoints internos `/internal/*`, chamados só pelos 2 Container Apps Jobs — nunca por clientes externos |

O Serviço B não expõe nenhuma API pública — o Container App interno não tem ingress externo; os 2 componentes são disparados por Schedule/Event trigger dos Jobs, e os endpoints Quarkus só existem para uso interno entre o gatilho fino e a lógica de negócio.

**Material de referência:**
- ID `REST`
- ID `QUARKUS`

---

## 5. Frontend

Não aplicável neste projeto — o enunciado do desafio define apenas contratos de API (`POST /avaliação`) e não exige painel visual. O cliente que envia avaliações e o cliente que consulta relatórios são externos a este repositório.

---

## 6. Deploy e Infraestrutura

| Item | Serviço A (Container App público) | Serviço B (Container App interno + 2 Jobs) |
|---|---|---|
| **Plataforma** | Azure Container Apps | Azure Container Apps (mesmo Environment) |
| **Registro de artefatos** | Azure Container Registry (ACR) | Azure Container Registry (ACR) — os 2 Jobs usam a imagem pública `mcr.microsoft.com/azure-cli`, não passam pelo ACR |
| **Monitoramento** | Application Insights | Application Insights |
| **Segredos** | Azure Key Vault, referenciado nativamente (`keyVaultUrl` + identidade gerenciada) nos `secrets` do Container App — nunca em texto puro | Azure Key Vault, referenciado nativamente (`keyVaultUrl` + identidade gerenciada) nos `secrets` do Container App e dos 2 Jobs — nunca em texto puro |
| **Identidade** | Managed Identity com roles concedidas via Bicep: `AcrPull` no ACR + `Key Vault Secrets User` no Key Vault | Managed Identity própria para o Container App interno (`AcrPull` + `Key Vault Secrets User`) e para cada um dos 2 Jobs (`Key Vault Secrets User`) |

**Componentes de infraestrutura compartilhados:**
- Azure Database for PostgreSQL — Flexible Server (banco único, acessado pelos dois serviços).
- Azure Storage Account — fila `notificacoes-criticas`.
- Azure Communication Services (Email) — envio da notificação de feedback crítico.

**Por que esta escolha:** Container Apps é gerenciado, sem configuração de Kubernetes, com escala automática — adequado para uma API sempre ativa com login. Container Apps Jobs (`Schedule`/`Event`) é serverless por definição, cobrado por execução, com `minExecutions: 0` — atende à exigência obrigatória do desafio e é ideal para cargas esporádicas (cron semanal, picos de feedback crítico), sem precisar de uma segunda plataforma de deploy (ver ADR-007 em `DECISIONS.md`).

**Regras obrigatórias:**
- Containers com usuário não-root e build multistage.
- Nenhuma credencial de longa duração no repositório; deploy via OIDC entre GitHub Actions e Azure.
- Varredura de imagem antes de publicar no ACR.

**Material de referência:**
- ID `AZURE`
- ID `CLOUD`
- ID `DOCKER`

---

## 7. Mensageria Assíncrona

Uma fila do Azure Storage Queue desacopla o Serviço A do Serviço B:

| Fila | Produtor | Consumidor | Caso de uso |
|---|---|---|---|
| `notificacoes-criticas` | Serviço A, ao salvar avaliação com nota ≤ 3 | Job `job-feedback-critico` (Event trigger, KEDA azure-queue, Serviço B) → `FeedbackCriticoResource` (Quarkus) | Desacopla o recebimento do feedback do envio da notificação por e-mail. |

Não é Kafka nem RabbitMQ — não há material de curso específico para Azure Storage Queue; seguir a documentação oficial do Azure (`ID SERVERLESS` cobre os padrões event-driven equivalentes).

---

## 8. Resiliência

Projeto de porte pequeno — sem microsserviços em cadeia nem chamadas síncronas entre Serviço A e Serviço B (a comunicação é só via banco compartilhado e filas). Resiliência aplicada:
- Filas dão retry automático de mensagens não confirmadas (visibilidade/redelivery do Azure Storage Queue).
- Timeout configurado no client HTTP do Azure Communication Services.
- Circuit Breaker não se justifica neste porte; será reavaliado se o projeto evoluir para múltiplos serviços síncronos dependentes entre si.

---

## 9. Testes

| Camada | Ferramenta |
|---|---|
| Unitário (Serviço A) | JUnit 5, Mockito |
| Integração (Serviço A) | Spring Boot Test, MockMvc, Testcontainers (Postgres) |
| Unitário (Serviço B) | JUnit 5, AssertJ (ex.: serialização do relatório) |
| Integração (Serviço B) | `@QuarkusTest` + RestAssured + Dev Services (Postgres via Testcontainers) contra os 2 endpoints internos |

**Abordagem:** Testes escritos em paralelo ao código. Unitários para lógica de negócio (limite de nota crítica, cálculo de médias), integração para os endpoints e para o acesso ao banco compartilhado.

**Material de referência:**
- ID `TESTES`

---

## 10. Observabilidade

- Application Insights conectado aos dois Container Apps (Serviço A e Serviço B) via connection string em variável de ambiente.
- Serviço A expõe Actuator (`/actuator/health`, `/actuator/metrics`) usado como health check do Container App.
- Serviço B expõe `/health` (SmallRye Health) no Container App interno; os 2 Jobs são observados via logs estruturados (stdout/stderr) e execuções no Azure Monitor (Container Apps Jobs).
- **Alertas provisionados de fato em `infra/azure/main.bicep`** (não só documentados): Action Group de e-mail (`alertNotificationEmail`) acionado por 2 Metric Alerts —
  1. `alert-excecoes` — qualquer exceção reportada no Application Insights (cobre falha de execução dos 2 Jobs/endpoint interno e erros da API).
  2. `alert-postgres-cpu` — CPU do PostgreSQL Flexible Server acima de 80% por 15 minutos.

**Material de referência:**
- ID `AZURE`

---

## 11. Segurança

- HTTPS obrigatório no ingress do Container App do Serviço A; o Container App do Serviço B não tem ingress externo (só alcançável de dentro do Container Apps Environment).
- `JWT_SECRET` fora do código, gerenciado por Key Vault em produção (referência nativa, nunca copiado como texto puro em variável de ambiente).
- Senha de administrador com hash BCrypt, nunca em texto plano.
- Managed Identity própria para cada componente, com **roles RBAC concedidas explicitamente em `infra/azure/main.bicep`** (não apenas identidade criada sem permissão): `AcrPull` + `Key Vault Secrets User` para os 2 Container Apps (Serviço A e Serviço B); `Key Vault Secrets User` para cada um dos 2 Jobs. Nenhuma tem mais acesso do que precisa.
- RBAC no Azure restringindo quem pode alterar recursos do resource group.
- Nenhum dado sensível (nota, descrição do feedback) exposto em logs.
- Imagem do Container App roda com usuário não-root.

**Material de referência:**
- ID `SEGURANCA`
- ID `CLOUD` (IAM/RBAC)

---

## 12. Variáveis de Ambiente

> Lista completa em `.env.example`. Os valores reais ficam no Azure Key Vault em produção e em `.env` local, nunca versionado.

- **Banco de dados:** `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- **Segurança:** `JWT_SECRET`, `JWT_EXPIRATION_MS` (só Serviço A), `INTERNAL_TRIGGER_SECRET` (só Serviço B, entre gatilho e endpoint interno)
- **Regra de negócio:** `NOTA_CRITICA_LIMITE`
- **Azure (mensageria/e-mail):** `AZURE_STORAGE_CONNECTION_STRING`, `AZURE_COMMUNICATION_SERVICES_CONNECTION_STRING`, `AZURE_COMMUNICATION_SERVICES_SENDER_ADDRESS`
- **Azure (deploy/observabilidade):** `APPLICATIONINSIGHTS_CONNECTION_STRING`
- **Azure (gatilho fino, só dentro dos 2 Jobs, ver ADR-007):** `INTERNAL_APP_URL`, `STORAGE_CONNECTION_STRING` — não fazem parte de `.env.example` porque os Jobs não rodam localmente; são env vars definidas direto em `infra/azure/main.bicep`.

---

## 13. Tecnologias fora do escopo inicial

- Kubernetes / AKS (usando Azure Container Apps, incluindo Container Apps Jobs para o gatilho fino).
- Azure Functions (usado até a ADR-006; substituído por Azure Container Apps Jobs na ADR-007).
- GraphQL e gRPC (usando REST).
- Kafka / RabbitMQ (usando Azure Storage Queue, suficiente para o volume esperado).
- MongoDB / Cassandra (usando apenas PostgreSQL).
- Solicitação de relatório sob demanda como função serverless (ver ADR-005 — removida; se reintroduzida, deve ser um endpoint comum do Serviço A).
- Endpoints internos do Serviço B (`/internal/*`) expostos como API pública — servem só para os 2 Container Apps Jobs (ver ADR-006 e ADR-007), e o Container App que os hospeda nem tem ingress externo.
- Frontend/painel visual (fora do escopo do enunciado; pode ser adicionado depois como novo módulo).
- Login social / OAuth externo (JWT próprio, sem provedor de identidade externo).
- IA integrada (não faz parte deste desafio).

---

## 14. Materiais de curso relevantes para este projeto

Leia estes materiais **antes** de implementar a tecnologia correspondente:

- **SOLID**: `/home/joao/dev/skils/rubro-spec/pdf/ Princípios do SOLID /CURSO-COMPLETO-SOLID.md`
  → Ler ao revisar design de classes, especialmente a separação entre os dois serviços.
- **OOP**: `/home/joao/dev/skils/rubro-spec/pdf/Paradigma da Orientação a Objetos /CURSO-COMPLETO-OOP.md`
  → Ler ao criar hierarquias/entidades.
- **TESTES**: `/home/joao/dev/skils/rubro-spec/pdf/Testes Unitários e de integração /CURSO-COMPLETO-TESTES.md`
  → Ler ao escrever testes unitários e de integração.
- **AZURE**: `/home/joao/dev/skils/rubro-spec/pdf/Deploy em Azure/CURSO-COMPLETO-DEPLOY-AZURE.md`
  → Ler sempre ao configurar infraestrutura (Container Apps, Container Apps Jobs, ACR, Key Vault, Application Insights).
- **CLOUD**: `/home/joao/dev/skils/rubro-spec/pdf/Fundamentos de Cloud Computing/CURSO-COMPLETO-CLOUD-COMPUTING.md`
  → Ler ao decidir modelo de hospedagem e segurança/governança de acesso.
- **SERVERLESS**: `/home/joao/dev/skils/rubro-spec/pdf/Serverless Computing/CURSO-COMPLETO-SERVERLESS.md`
  → Ler antes de implementar qualquer um dos 2 componentes serverless do Serviço B (Container Apps Jobs).
- **REST**: `/home/joao/dev/skils/rubro-spec/pdf/APIs RESTful /CURSO-COMPLETO-APIS-RESTFUL.md`
  → Ler ao criar controllers do Serviço A.
- **QUARKUS**: `/home/joao/dev/skils/rubro-spec/pdf/ Quarkus/CURSO-COMPLETO-QUARKUS.md`
  → Ler antes de qualquer código Quarkus do Serviço B (resources, CDI, Panache).
- **JPA-NOSQL**: `/home/joao/dev/skils/rubro-spec/pdf/Spring Data JPA SQL e NoSQL /CURSO-COMPLETO-SPRING-DATA-JPA.md`
  → Ler ao criar entidades JPA.
- **MODELAGEM-BD**: `/home/joao/dev/skils/rubro-spec/pdf/Modelagem de banco de dados /CURSO-COMPLETO-MODELAGEM-BD.md`
  → Ler ao modelar o schema (avaliações, relatórios, administradores).
- **SEGURANCA**: `/home/joao/dev/skils/rubro-spec/pdf/Segurança em Aplicações Java /CURSO-COMPLETO-SEGURANCA-JAVA.md`
  → Ler ao configurar JWT e hashing de senha.
- **DOCKER**: `/home/joao/dev/skils/rubro-spec/pdf/Containers e Dockerização /CURSO-COMPLETO-DOCKER.md`
  → Ler ao criar o Dockerfile do Serviço A e o compose local.

---

*Documento gerado automaticamente pela skill `criar-projeto`. Mantenha atualizado conforme o projeto evolui.*
