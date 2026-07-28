# Relatório do Projeto — EduFeedback

**Tech Challenge — Fase 4 (Cloud Computing, Serverless e Deploy)**
**Repositório:** https://github.com/torresvictor100/edu-feedback
**Data:** 28/07/2026

---

## 1. Descrição do projeto

O EduFeedback é uma plataforma para avaliação de aulas: estudantes enviam feedback (nota de 0 a 10 e descrição) e administradores acompanham a satisfação por meio de relatórios periódicos e recebem notificações automáticas quando um feedback crítico é registrado.

O projeto atende aos requisitos obrigatórios do desafio:

- Aplicação hospedada em ambiente de nuvem (Microsoft Azure).
- No mínimo duas funções serverless, cada uma com responsabilidade única.
- Deploy automatizado dos componentes atualizáveis.
- Aplicação monitorada, com notificações automáticas para problemas críticos.
- Relatório semanal com média de avaliações.

A solução foi dividida em **dois serviços independentes**, que compartilham o mesmo banco de dados PostgreSQL:

| Serviço | Responsabilidade | Tecnologia |
|---|---|---|
| **Serviço A** — API principal | Login de administrador (JWT), recebimento de feedback (`POST /avaliação`), consulta de relatórios | Java 21 + Spring Boot 3 |
| **Serviço B** — Componentes serverless | Notificação de feedback crítico e geração agendada de relatório | Java 21 + Quarkus 3 (endpoint interno) + Azure Container Apps Jobs (gatilho) |

---

## 2. Arquitetura da solução

### 2.1 Visão geral do fluxo

```
Estudante (cliente externo, sem autenticação)
  └─▶ POST /avaliação  ──▶  Serviço A (Spring Boot)
                               ├─▶ PostgreSQL (tabela avaliacoes)
                               └─▶ nota ≤ 3 → publica na fila "notificacoes-criticas"
                                              (Azure Storage Queue)

Administrador (cliente externo)
  ├─▶ POST /auth/login          ──▶ Serviço A ──▶ JWT
  └─▶ GET /relatorios[/{id}]    ──▶ Serviço A ──▶ PostgreSQL (JWT obrigatório)

Serviço B — Container App interno (Quarkus, sem ingress externo)
  + 2 Container Apps Jobs (gatilho fino, serverless)

  Job "job-relat-*"  (Schedule, cron semanal, seg. 08h UTC)
     └─▶ POST /internal/relatorio-agendado (X-Internal-Secret)
            └─▶ GerarRelatorioAgendadoUseCase ──▶ PostgreSQL (lê avaliações, grava relatório)

  Job "job-crit-*"   (Event, escalado por KEDA sobre a fila "notificacoes-criticas")
     └─▶ consome 1 mensagem da fila
     └─▶ POST /internal/feedback-critico (X-Internal-Secret)
            └─▶ NotificarFeedbackCriticoUseCase ──▶ lê e-mails dos admins (PostgreSQL)
                                                  ──▶ envia e-mail via Azure Communication Services
```

### 2.2 Modelo de nuvem escolhido

**Azure Container Apps** para tudo — API sempre ativa (Serviço A), endpoint interno (Serviço B) e os dois componentes serverless (Container Apps Jobs). Essa escolha unificou toda a solução numa única plataforma gerenciada, sem Kubernetes para administrar, com:

- **Serviço A** — Container App público, com ingress externo, `minReplicas: 0` a 3 réplicas, escala automática.
- **Serviço B (endpoint interno)** — Container App **sem ingress externo**, só alcançável de dentro do Container Apps Environment (ou seja, só os 2 Jobs conseguem chamá-lo).
- **Serviço B (gatilhos)** — 2 **Container Apps Jobs**, cobrados por execução, `minExecutions: 0` (nada fica rodando entre disparos) — esse é o componente serverless propriamente dito exigido pelo desafio.

O projeto passou por três desenhos até chegar aqui: começou com Azure Functions puro, depois um híbrido Quarkus + Azure Functions Java Worker, e por fim migrou os gatilhos de Azure Functions para Container Apps Jobs — eliminando a necessidade de uma segunda plataforma de deploy só para hospedar os gatilhos, mantendo o requisito de serverless (execuções sob demanda, sem servidor dedicado).

### 2.3 Componentes provisionados (Infraestrutura como Código)

Todo o ambiente é provisionado via `infra/azure/main.bicep` (Bicep), em um único resource group:

| Recurso | Nome | Papel |
|---|---|---|
| PostgreSQL Flexible Server | `psql-edufeedback-dev` | Banco compartilhado pelos dois serviços |
| Storage Account + fila | `stedufeedbackdev` | Fila `notificacoes-criticas` |
| Azure Container Registry | `acredufeedbackdev` | Imagens Docker dos dois serviços |
| Container Apps Environment | `cae-edufeedback-dev` | Ambiente único compartilhado por todos os Container Apps/Jobs |
| Container App (Serviço A) | `app-edufeedback-dev` | API pública, ingress externo |
| Container App (Serviço B) | `app-func-edufeedback-dev` | Endpoints internos Quarkus, sem ingress externo |
| Container Apps Job (Schedule) | `job-relat-edufeedback-dev` | Gatilho semanal do relatório |
| Container Apps Job (Event/KEDA) | `job-crit-edufeedback-dev` | Gatilho por fila da notificação crítica |
| Application Insights + Log Analytics | `appi-`/`log-edufeedback-dev` | Observabilidade dos dois serviços |
| Key Vault | `kv-edufeedback-dev` | 4 segredos (senha do Postgres, JWT, segredo interno, connection string) |
| Azure Communication Services | `acs-edufeedback-dev` | Envio de e-mail |
| Action Group + 2 Metric Alerts | `ag-`/`alert-excecoes-`/`alert-postgres-cpu-edufeedback-dev` | Notificação por e-mail em exceções e CPU alta |

### 2.4 Segurança e governança de acesso

- **Identidade gerenciada (User-Assigned)** própria para os Container Apps (`id-apps-edufeedback-dev`) e para os Jobs (`id-jobs-edufeedback-dev`) — nenhuma credencial de longa duração fica no template ou nas variáveis de ambiente.
- **RBAC de menor privilégio**, concedido explicitamente no Bicep:
  - Container Apps → `AcrPull` (puxar imagem) + `Key Vault Secrets User` (ler segredos).
  - Jobs → só `Key Vault Secrets User`.
- **Todos os segredos** (`postgres-admin-password`, `jwt-secret`, `internal-trigger-secret`, `storage-connection-string`) ficam no **Azure Key Vault** e são referenciados nativamente pelos Container Apps/Jobs (`keyVaultUrl` + identidade gerenciada) — nunca em texto puro.
- **Autenticação da API**: JWT stateless (HS256) só no Serviço A — `POST /avaliação` é público (contrato do desafio), login e consulta de relatório exigem token de administrador.
- **Comunicação interna protegida**: os endpoints `/internal/*` do Serviço B são validados por um segredo compartilhado (`X-Internal-Secret`, verificado por `InternalSecretValidator`) — sem ele, retornam 401. Como o Container App que os hospeda não tem ingress externo, eles nem são alcançáveis pela internet.
- **Deploy sem credenciais estáticas**: o pipeline usa federação **OIDC** entre GitHub Actions e Azure (App Registration + Federated Credential), sem client secret salvo em lugar nenhum.
- Containers rodam com **usuário não-root**, build multistage (`backend/Dockerfile`, `functions/Dockerfile`).
- Firewall do PostgreSQL restrito a `AllowAzureServices`.

---

## 3. Instruções de deploy

### 3.1 Provisionamento inicial da infraestrutura (manual, uma vez)

```bash
az login
az account set --subscription "<subscription-id>"

az group create --name rg-edu-feedback --location brazilsouth

cp infra/azure/main.parameters.example.json infra/azure/main.parameters.json
# preencher postgresAdminPassword, jwtSecret, internalTriggerSecret e
# alertNotificationEmail com valores reais (nunca commitar este arquivo)

az deployment group validate \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json

az deployment group create \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json
```

Isso cria todos os recursos listados na seção 2.3. Ao final, é preciso confirmar o e-mail de ativação do Action Group (enviado para `alertNotificationEmail`) — sem essa confirmação os alertas não notificam ninguém.

### 3.2 Deploy contínuo do código (automatizado, `.github/workflows/deploy-azure.yml`)

1. Login na Azure via **OIDC** (sem senha/secret estático).
2. Build da imagem do Serviço A a partir de `backend/Dockerfile` → push no Azure Container Registry.
3. Build da imagem do Serviço B a partir de `functions/Dockerfile` → push no Azure Container Registry.
4. `az containerapp update` nos dois Container Apps, apontando para a nova tag de imagem.
5. Os 2 Container Apps Jobs **não** fazem parte deste pipeline — usam a imagem pública `mcr.microsoft.com/azure-cli`, definida direto no Bicep (só o script inline muda se `main.bicep` for alterado).

Pré-requisito no GitHub (Settings → Environments → `production`): secrets `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_ACR_NAME`, `AZURE_RESOURCE_GROUP`.

### 3.3 Verificação pós-deploy

- `GET https://<containerAppFqdn>/actuator/health` → `{"status":"UP"}`.
- Testar os endpoints internos disparando manualmente um Job: `az containerapp job start --name job-relat-edufeedback-dev --resource-group rg-edu-feedback`.
- Conferir "Execution history" dos 2 Jobs no portal (Container Apps → Jobs).
- Rodar a coleção Postman (`postman/edu-feedback.postman_collection.json`) contra a URL pública real.
- Enviar uma avaliação crítica (nota ≤ 3) e confirmar: mensagem na fila → Job `job-crit-*` disparado → e-mail recebido.

### 3.4 Rollback

- Serviço A / Serviço B (Container Apps): `az containerapp revision list` + `az containerapp update --image <imagem-anterior>`.
- Jobs: não versionam imagem própria (usam `mcr.microsoft.com/azure-cli`); qualquer rollback de comportamento é feito revertendo `infra/azure/main.bicep`.
- Banco de dados: migrations Flyway são incrementais, sem rollback automático.

---

## 4. Configuração do monitoramento

### 4.1 Observabilidade

- **Application Insights** (`appi-edufeedback-dev`), com **Log Analytics Workspace** (`log-edufeedback-dev`) como back-end, conectado aos dois Container Apps via `APPLICATIONINSIGHTS_CONNECTION_STRING`.
- Serviço A expõe **Actuator** (`/actuator/health`, `/actuator/metrics`), usado como health check do Container App.
- Serviço B expõe **SmallRye Health** (`/health`) no Container App interno; os 2 Jobs são observados pelos logs estruturados (stdout/stderr) e pelo histórico de execuções em Container Apps Jobs.
- No portal: **Monitor → Métricas/Logs**, ou dentro de cada recurso (App/Job/Postgres), aba **Monitoring**.

### 4.2 Alertas configurados (Azure Monitor)

Provisionados como código em `infra/azure/main.bicep`, com um **Action Group** de e-mail acionando alertas de métrica. Confirmado no portal (Monitor → Alerts → Regras de alerta) que todas as regras estão **Habilitadas** e ativas:

| Alerta | Condição | Severidade | Recurso monitorado | Tipo de recurso | Status |
|---|---|---|---|---|---|
| `alert-excecoes-edufeedback-dev` | `exceptions/count > 0` — qualquer exceção reportada no Application Insights | 2 — Aviso | `appi-edufeedback-dev` | Application Insights | 🟢 Habilitado |
| `alert-excecoes-edufeedback01-dev` | `exceptions/count > 0` — qualquer exceção reportada no Application Insights | 2 — Aviso | `appi-edufeedback01-dev` | Application Insights | 🟢 Habilitado |
| `alert-postgres-cpu-edufeedback-dev` | `cpu_percent > 80` — CPU do PostgreSQL acima de 80% | 3 — Informativo | `psql-edufeedback-dev` | Banco de Dados do Azure para servidor flexível | 🟢 Habilitado |
| `alert-postgres-cpu-edufeedback01-dev` | `cpu_percent > 80` — CPU do PostgreSQL acima de 80% | 3 — Informativo | `psql-edufeedback01-dev` | Banco de Dados do Azure para servidor flexível | 🟢 Habilitado |

Todos os alertas disparam um **Action Group** de e-mail, que envia a notificação para o administrador cadastrado (`alertNotificationEmail`). Existem duas séries de alertas (`-dev` e `01-dev`) porque o ambiente foi provisionado/reprovisionado durante a validação — ambas as séries permanecem habilitadas e monitorando seus respectivos recursos.

Adicionalmente, durante a validação manual do monitoramento, foi criado pelo portal um alerta extra de teste sobre o Container App (`app-edufeedback01-dev`, CPU > 10%, grupo de ações `monitorar-edu`/`monitor-cpu` com e-mail `torresvictor100@gmail.com`), usado para demonstrar de ponta a ponta o recebimento real da notificação por e-mail no vídeo de entrega.

---

## 5. Documentação das funções criadas

O Serviço B implementa **exatamente 2 componentes serverless**, cada um com responsabilidade única. Cada um combina um **gatilho fino** (Azure Container Apps Job, sem lógica de negócio, definido só em `infra/azure/main.bicep`) com um **endpoint interno Quarkus** que concentra toda a regra de negócio (CDI, Panache), protegido por um segredo compartilhado (`X-Internal-Secret`).

### 5.1 Função 1 — Notificação de feedback crítico

| Campo | Valor |
|---|---|
| Gatilho | Container Apps Job `job-crit-edufeedback-dev` — **Event trigger**, escalado por regra KEDA `azure-queue` sobre a fila `notificacoes-criticas` |
| Responsabilidade única | Consumir 1 mensagem da fila e notificar os administradores por e-mail |
| Script do gatilho | `az storage message get` (lê 1 mensagem) → `curl POST /internal/feedback-critico` → `az storage message delete` (confirma consumo) |
| Endpoint interno | `POST /internal/feedback-critico` — classe `FeedbackCriticoResource` (`functions/.../notificacao/infrastructure/web/`) |
| Autenticação | Header `X-Internal-Secret`, validado por `InternalSecretValidator`; sem ele → `401` |
| Corpo da requisição | `{ "descricao": string, "urgencia": string, "dataEnvio": string }` |
| Lógica de negócio | `NotificarFeedbackCriticoUseCase` — busca e-mails dos administradores (`AdminRepository`, Panache) e envia via `AzureEmailSender` (Azure Communication Services) |
| Dados enviados no e-mail | Descrição, urgência e data de envio (conforme exigido pelo enunciado) |

### 5.2 Função 2 — Geração de relatório agendado

| Campo | Valor |
|---|---|
| Gatilho | Container Apps Job `job-relat-edufeedback-dev` — **Schedule trigger**, cron `0 8 * * 1` (toda segunda-feira, 08:00 UTC) |
| Responsabilidade única | Calcular agregados das avaliações e persistir um novo relatório |
| Script do gatilho | `curl -X POST /internal/relatorio-agendado` com `X-Internal-Secret` |
| Endpoint interno | `POST /internal/relatorio-agendado` — classe `RelatorioAgendadoResource` (`functions/.../relatorio/infrastructure/web/`) |
| Autenticação | Header `X-Internal-Secret`, validado por `InternalSecretValidator`; sem ele → `401` |
| Lógica de negócio | `GerarRelatorioAgendadoUseCase` — lê avaliações via `AvaliacaoRepository` (Panache), calcula médias e contagens, persiste `Relatorio` via `RelatorioRepository` |
| Dados calculados | Média de avaliações, quantidade de avaliações por dia, quantidade por urgência, lista das avaliações (descrição, urgência, data de envio) |
| Resposta | `200 OK` com o objeto `Agregados` gerado (JSON) |

### 5.3 Por que essa arquitetura (gatilho fino + endpoint interno)

A extensão oficial do Quarkus para Azure Functions (`quarkus-azure-functions-http`) só suporta **gatilho HTTP** — não há suporte oficial para Timer/Queue trigger com CDI. Para manter Quarkus (objetivo de aprendizado do time) sem abrir mão do serverless real nem misturar responsabilidades, a solução foi separar em duas peças por função: um gatilho nativo (hoje, Container Apps Job) totalmente burro, que só dispara e repassa via HTTP, e um endpoint Quarkus interno — nunca exposto à internet — que concentra toda a lógica de negócio de verdade.

---

## 6. Endpoints da API principal (Serviço A)

| Método | Rota | Autenticação | Descrição |
|---|---|---|---|
| `POST` | `/avaliação` | Nenhuma (público) | Recebe `{descricao, nota}`, persiste, e se `nota ≤ 3` publica na fila `notificacoes-criticas` |
| `POST` | `/auth/login` | Nenhuma | Recebe `{email, senha}`, retorna JWT |
| `GET` | `/relatorios` | JWT (admin) | Lista todos os relatórios já gerados |
| `GET` | `/relatorios/{id}` | JWT (admin) | Consulta um relatório específico |
| `GET` | `/actuator/health` | Nenhuma | Health check usado pelo Container App |

---

## 7. Referências internas do repositório

- `infra/azure/main.bicep` — infraestrutura como código (todos os recursos descritos na seção 2.3).
- `.github/workflows/deploy-azure.yml` — pipeline de deploy automatizado.
- `.github/workflows/ci.yml` — build e testes a cada push/PR.
