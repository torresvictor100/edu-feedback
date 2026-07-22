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
| **Serviço A — API principal** | Spring Boot 3 | Login admin (JWT), recebimento de feedback, consulta de relatórios prontos | Azure Container Apps |
| **Serviço B — Funções serverless** | Quarkus 3 | Geração agendada de relatório, solicitação sob demanda, processamento assíncrono, notificação de feedback crítico | Azure Functions |

Os dois serviços compartilham o mesmo banco PostgreSQL.

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

### 1.2 Serviço B — Funções serverless

| Campo | Valor |
|---|---|
| **Framework** | Quarkus |
| **Linguagem** | Java |
| **Versão** | Quarkus 3.x / Java 21 |
| **Build tool** | Maven 3.9.x |

**Por que esta escolha:** startup ultrarrápido (JVM mode neste projeto; native/GraalVM documentado como evolução futura) e baixo consumo de memória — reduz cold start e custo em Azure Functions, que é cobrado por execução.

**Nota de empacotamento (importante para quem for evoluir este projeto):** a extensão oficial `quarkus-azure-functions-http` só suporta função com **HTTP trigger** (ela expõe toda a API REST do Quarkus atrás de uma única function HTTP). Por isso, dentro do módulo `functions/`:
- a função de **solicitação sob demanda** (HTTP trigger) usa Quarkus de verdade (RESTEasy Reactive + Panache + CDI), via `quarkus-azure-functions-http`;
- as funções de **timer**, **fila** e **notificação por feedback crítico** usam o modelo padrão do Azure Functions Java Worker (`azure-functions-java-library`, anotações `@FunctionName`/`@TimerTrigger`/`@QueueTrigger`) no mesmo módulo Maven, com acesso ao Postgres via JDBC simples (sem CDI/Panache, pois essas funções não rodam dentro do container ArC do Quarkus). Isso é documentado como ADR-004 em `DECISIONS.md`.

**Regras obrigatórias (ambos os serviços):**
- Organizar por módulo de domínio, não por camada técnica.
- Controller/Resource: recebe HTTP, valida entrada, delega ao Service.
- Migrations Flyway pertencem exclusivamente ao Serviço A (dono do schema); o Serviço B só lê/escreve nas tabelas já migradas, nunca cria ou altera schema.
- Segredos somente em `.env` ou variável de ambiente / Key Vault. Nunca em `application.properties` commitado.
- Testes de integração usam Testcontainers (Postgres) nos dois serviços.

**Material de referência:**
- **REST** — controllers Spring (`/home/joao/dev/skils/rubro-spec/pdf/APIs RESTful /CURSO-COMPLETO-APIS-RESTFUL.md`)
- **QUARKUS** — resources e functions Quarkus (`/home/joao/dev/skils/rubro-spec/pdf/ Quarkus/CURSO-COMPLETO-QUARKUS.md`)
- **SERVERLESS** — padrões de função única responsabilidade, event-driven (`/home/joao/dev/skils/rubro-spec/pdf/Serverless Computing/CURSO-COMPLETO-SERVERLESS.md`)

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
- O Serviço B (Quarkus/Functions) nunca roda migration; conecta-se ao schema já existente.
- Nenhuma query SQL nativa sem necessidade; preferir JPQL/Panache no Serviço A.

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
- A função HTTP de solicitação de relatório sob demanda (Serviço B) valida o mesmo token JWT (papel `ADMIN`) antes de enfileirar o pedido.
- Segredo `JWT_SECRET` nunca versionado; mesmo valor configurado nos dois serviços via Key Vault/variável de ambiente.
- Senhas de administrador com hash BCrypt.

**Material de referência:**
- ID `SEGURANCA`

---

## 4. APIs e Comunicação

| Estilo | Tecnologia | Quando usar |
|---|---|---|
| REST | Spring MVC (Serviço A) | Login, recebimento de feedback, consulta de relatório |
| REST | Quarkus RESTEasy Reactive (Serviço B, função HTTP) | Solicitação de relatório sob demanda |

**Material de referência:**
- ID `REST`
- ID `QUARKUS`

---

## 5. Frontend

Não aplicável neste projeto — o enunciado do desafio define apenas contratos de API (`POST /avaliação`) e não exige painel visual. O cliente que envia avaliações e o cliente que consulta relatórios são externos a este repositório.

---

## 6. Deploy e Infraestrutura

| Item | Serviço A (API) | Serviço B (Functions) |
|---|---|---|
| **Plataforma** | Azure Container Apps | Azure Functions (Consumption/Premium) |
| **Registro de artefatos** | Azure Container Registry (ACR) | Azure Functions deployment package (zip) via Maven plugin |
| **Monitoramento** | Application Insights | Application Insights |
| **Segredos** | Azure Key Vault (referenciado via variáveis de ambiente do Container App) | Azure Key Vault (referenciado via App Settings do Function App) |
| **Identidade** | Managed Identity (acesso a Key Vault, Storage, Postgres) | Managed Identity (acesso a Key Vault, Storage, ACS) |

**Componentes de infraestrutura compartilhados:**
- Azure Database for PostgreSQL — Flexible Server (banco único, acessado pelos dois serviços).
- Azure Storage Account — duas filas: `solicitacoes-relatorio` e `notificacoes-criticas`.
- Azure Communication Services (Email) — envio das notificações e do aviso de relatório pronto.

**Por que esta escolha:** Container Apps é gerenciado, sem configuração de Kubernetes, com escala automática — adequado para uma API sempre ativa com login. Functions é serverless por definição, cobrado por execução — atende à exigência obrigatória do desafio e é ideal para cargas esporádicas (timer semanal, picos de solicitação sob demanda).

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

Duas filas do Azure Storage Queue coordenam o trabalho assíncrono exigido pelo produto:

| Fila | Produtor | Consumidor | Caso de uso |
|---|---|---|---|
| `solicitacoes-relatorio` | Função HTTP (Serviço B) | Função Queue Trigger (Serviço B) | Admin pede relatório sob demanda; geração acontece em segundo plano porque pode demorar. |
| `notificacoes-criticas` | Serviço A, ao salvar avaliação com nota ≤ 3 | Função Queue Trigger (Serviço B) | Desacopla o recebimento do feedback do envio da notificação por e-mail. |

Não é Kafka nem RabbitMQ — não há material de curso específico para Azure Storage Queue; seguir a documentação oficial do Azure (`ID SERVERLESS` cobre os padrões event-driven equivalentes: fan-out, processamento assíncrono).

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
| Unitário (Serviço B) | JUnit 5 |
| Integração (Serviço B) | Quarkus Test (`@QuarkusTest`), Testcontainers (Postgres) para a função HTTP |

**Abordagem:** Testes escritos em paralelo ao código. Unitários para lógica de negócio (limite de nota crítica, cálculo de médias), integração para os endpoints e para o acesso ao banco compartilhado.

**Material de referência:**
- ID `TESTES`

---

## 10. Observabilidade

- Application Insights conectado aos dois serviços (Container App e Function App) via connection string em variável de ambiente.
- Serviço A expõe Actuator (`/actuator/health`, `/actuator/metrics`) usado como health check do Container App.
- Serviço B expõe `/health` (SmallRye Health) na função HTTP; funções não-HTTP são observadas via logs estruturados e métricas nativas do Function App no Azure Monitor.
- Alertas mínimos: falha de execução de função (qualquer uma das 4), tamanho da fila `notificacoes-criticas` acima do esperado (indício de falha no consumidor), tempo de resposta acima do limite no Container App.

**Material de referência:**
- ID `AZURE`

---

## 11. Segurança

- HTTPS obrigatório em ambos os serviços (ingress do Container App e endpoint do Function App).
- `JWT_SECRET` fora do código, gerenciado por Key Vault em produção.
- Senha de administrador com hash BCrypt, nunca em texto plano.
- Managed Identity para os dois serviços acessarem Key Vault, Storage e Postgres sem connection string fixa em produção.
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
- **Segurança:** `JWT_SECRET`, `JWT_EXPIRATION_MS`
- **Regra de negócio:** `NOTA_CRITICA_LIMITE`
- **Azure (mensageria/e-mail):** `AZURE_STORAGE_CONNECTION_STRING`, `AZURE_COMMUNICATION_SERVICES_CONNECTION_STRING`, `AZURE_COMMUNICATION_SERVICES_SENDER_ADDRESS`
- **Azure (deploy/observabilidade):** `APPLICATIONINSIGHTS_CONNECTION_STRING`

---

## 13. Tecnologias fora do escopo inicial

- Kubernetes / AKS (usando Azure Container Apps + Azure Functions).
- GraphQL e gRPC (usando REST).
- Kafka / RabbitMQ (usando Azure Storage Queue, suficiente para o volume esperado).
- MongoDB / Cassandra (usando apenas PostgreSQL).
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
  → Ler sempre ao configurar infraestrutura (Container Apps, Functions, ACR, Key Vault, Application Insights).
- **CLOUD**: `/home/joao/dev/skils/rubro-spec/pdf/Fundamentos de Cloud Computing/CURSO-COMPLETO-CLOUD-COMPUTING.md`
  → Ler ao decidir modelo de hospedagem e segurança/governança de acesso.
- **SERVERLESS**: `/home/joao/dev/skils/rubro-spec/pdf/Serverless Computing/CURSO-COMPLETO-SERVERLESS.md`
  → Ler antes de implementar qualquer uma das 4 funções.
- **REST**: `/home/joao/dev/skils/rubro-spec/pdf/APIs RESTful /CURSO-COMPLETO-APIS-RESTFUL.md`
  → Ler ao criar controllers do Serviço A.
- **QUARKUS**: `/home/joao/dev/skils/rubro-spec/pdf/ Quarkus/CURSO-COMPLETO-QUARKUS.md`
  → Ler antes de qualquer código Quarkus do Serviço B.
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
