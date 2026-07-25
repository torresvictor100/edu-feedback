# Checklist de entrega — Tech Challenge Fase 4 (EduFeedback)

Este documento cruza o enunciado do desafio com o que já está pronto no repositório e o que ainda depende de você. Não é um documento técnico de arquitetura (isso já existe em `TECH-SPEC.md`, `ARCHITECTURE.md`, `AZURE-DEPLOY.md`, `PRODUCTION-READINESS.md`) — é o "onde eu estou, o que falta, o que eu faço agora".

---

## 1. Requisitos do enunciado

| Requisito | Status | Onde está |
|---|---|---|
| Ambiente de nuvem configurado e funcionando, com segurança e governança de acesso | 🟡 Infra pronta como código, **não provisionada de verdade** | `infra/azure/main.bicep` (RBAC real: `AcrPull`, `Key Vault Secrets User`, `Storage Queue Data Contributor`) |
| Configuração dos componentes de suporte (banco de dados etc.) | 🟡 Definido no Bicep, **não provisionado** | PostgreSQL Flexible Server em `main.bicep` |
| Deploy automatizado dos componentes atualizáveis (funções) | 🟡 Pipeline pronto, **nunca executado** | `.github/workflows/deploy-azure.yml` |
| Aplicação monitorada | 🟡 Alertas definidos no Bicep, **não provisionados** | Application Insights + 2 Metric Alerts + Action Group em `main.bicep` |
| Notificações automáticas para itens críticos | 🟢 Implementado e testado localmente | `FeedbackCriticoTrigger` + `FeedbackCriticoResource` (Quarkus) |
| Relatório semanal com média de avaliações | 🟢 Implementado e testado localmente | `RelatorioAgendadoTrigger` + `RelatorioAgendadoResource` (Quarkus, cron padrão semanal) |

🟢 = pronto e validado localmente · 🟡 = código/infra pronta, falta executar na Azure de verdade · 🔴 = não existe ainda

## 2. Regras obrigatórias da aplicação

| Regra | Status |
|---|---|
| Implementar serverless | 🟢 2 Azure Functions (gatilho Timer + gatilho Queue), cada uma repassando para um endpoint Quarkus interno |
| Rodar em ambiente cloud | 🟡 Desenhado para Azure; falta o provisionamento real |
| Mínimo 2 funções serverless, com Responsabilidade Única por componente | 🟢 Exatamente 2 funções, cada uma com um único trigger e uma única responsabilidade (ver ADR-005 e ADR-006 em `DECISIONS.md`) |

## 3. Artefatos de entrega

| Artefato | Status | O que falta |
|---|---|---|
| Repositório aberto com o código-fonte | 🟢 Publicado no GitHub | https://github.com/torresvictor100/edu-feedback |
| Vídeo de demonstração (app funcionando, funções serverless ativas, configurações do projeto) | 🔴 **Não existe** | Precisa ser gravado por você depois do deploy real — é ação 100% humana, nenhum agente de IA grava vídeo. Roteiro sugerido na seção 5. |

## 4. Critérios de avaliação

| Critério | Status |
|---|---|
| Explicação do modelo de cloud escolhido e dos componentes envolvidos | 🟢 Documentado em `TECH-SPEC.md` (seções 1, 6, 7) e `ARCHITECTURE.md` — reforçar falando isso no vídeo |
| Funcionamento correto da aplicação | 🟡 Correto localmente (testes automatizados passando); falta validar rodando de verdade na Azure |
| Qualidade do código, com documentação | 🟢 `AGENTS.md`, `docs/`, javadoc nos pontos não óbvios, testes unitários e de integração |
| Descrição do projeto: arquitetura, instruções de deploy, monitoramento, documentação das funções | 🟢 `ARCHITECTURE.md`, `AZURE-DEPLOY.md`, `TECH-SPEC.md` (seção 10) |
| Configuração do ambiente de nuvem e funções serverless, com explicações do modelo e segurança | 🟡 Desenhado e documentado; falta executar e depois explicar o que rodou de verdade (não só o que está no código) |

---

## 5. Próximos passos (nesta ordem)

### Passo 1 — Testar tudo localmente (antes de gastar crédito de nuvem)

```bash
cd backend && ./mvnw test      # 13 testes, incluindo integração com Postgres real (Testcontainers)
cd functions && mvn test        # testes unitários + integração com Postgres real (Testcontainers)

cp .env.example .env            # preencher valores locais
docker compose up -d db
cd backend && ./mvnw spring-boot:run
```

Teste manual com a coleção Postman (`postman/edu-feedback.postman_collection.json` + `postman/local.postman_environment.json`): health check, login, enviar avaliação normal e crítica, consultar relatório.

### Passo 2 — Preparar a conta Azure

1. Ter uma assinatura Azure ativa (conta de estudante/trial serve).
2. `az login` e `az account set --subscription "<id>"`.
3. Decidir a região (`brazilsouth` é o padrão no Bicep) e criar o resource group:
   ```bash
   az group create --name rg-edu-feedback --location brazilsouth
   ```

### Passo 3 — Preencher os parâmetros reais (nunca commitar isso)

```bash
cp infra/azure/main.parameters.example.json infra/azure/main.parameters.json
```

Editar `infra/azure/main.parameters.json` com valores reais de `postgresAdminPassword`, `jwtSecret`, `internalTriggerSecret` (segredo entre os gatilhos nativos e os endpoints internos do Serviço B) e `alertNotificationEmail` (o e-mail que vai receber os alertas).

### Passo 4 — Validar e provisionar a infraestrutura

```bash
az deployment group validate \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json

az deployment group create \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json
```

**Atenção:** este Bicep nunca foi validado com `az` de verdade (o ambiente onde foi escrito não tinha Azure CLI instalado) — é bem possível que o `validate` acuse algum ajuste pequeno de sintaxe. Revise o erro, ajuste e rode de novo antes do `create`.

Depois do `create`, aceite o e-mail de confirmação do Action Group (chega em `alertNotificationEmail`) — sem isso os alertas não notificam ninguém.

### Passo 5 — Configurar o GitHub para deploy automatizado

1. ~~Criar o repositório no GitHub (público, conforme o enunciado pede "repositório aberto").~~ ✅ Feito: https://github.com/torresvictor100/edu-feedback
2. ~~`git remote add origin <url>` e `git push -u origin main`.~~ ✅ Feito (branch única `main`).
3. Criar a federação OIDC entre GitHub Actions e Azure (App Registration + Federated Credential) — sem client secret.
4. Configurar em Settings → Environments → `production`: secrets `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_ACR_NAME`, `AZURE_RESOURCE_GROUP`.
5. Rodar o workflow `deploy-azure.yml` (manual ou via push em `main`) para publicar a imagem do Serviço A e o pacote do Serviço B de verdade.

### Passo 6 — Verificar que subiu certo

- `curl https://<containerAppFqdn>/actuator/health` → `{"status":"UP"}`.
- Portal do Function App → aba "Functions" → `RelatorioAgendado` e `FeedbackCritico` aparecendo, "Enabled".
- `curl -X POST https://<functionAppName>.azurewebsites.net/api/internal/relatorio-agendado -H "X-Internal-Secret: <valor>"` deve responder 200 (sem o header, 401) — confirma que a chamada function-to-function funciona de verdade em produção, não só localmente.
- Monitor → Alerts → Alert rules → os 2 alertas como "Enabled".
- Rodar a coleção Postman contra a URL real do Container App.
- Enviar uma avaliação crítica (nota ≤ 3) de teste e confirmar que o e-mail chega via Azure Communication Services.
- Se o Container App ou o Function App falharem ao subir por causa do Key Vault, reiniciar a revision/o Function App (a role RBAC pode levar alguns minutos para se propagar — ver nota em `main.bicep`).

### Passo 7 — Trocar credenciais de desenvolvimento

- Trocar a senha do admin seed (`admin@edufeedback.local` / `admin123`) ou remover o seed e cadastrar o admin real direto no banco de produção.

### Passo 8 — Gravar o vídeo de demonstração

Roteiro sugerido (o enunciado pede: aplicação em funcionamento, funções serverless ativas, configurações do projeto):

1. Mostrar o resource group na Azure com todos os recursos provisionados (Container App, Function App, Postgres, Key Vault, Storage, Application Insights).
2. Explicar rapidamente o modelo de cloud escolhido: por que Container Apps para a API e Functions para o serverless, por que 2 funções (mostrar a ADR-005 e a ADR-006 em `docs/DECISIONS.md`) e como cada uma tem responsabilidade única.
3. Explicar a arquitetura interna do Serviço B: gatilho nativo fino (Timer/Queue) chamando um endpoint Quarkus interno protegido por segredo — vale a pena mostrar o código (`RelatorioAgendadoTrigger` → `RelatorioAgendadoResource` → `RelatorioService`) já que é a parte mais não óbvia do projeto e onde entra o Quarkus.
4. Mostrar a segurança/governança configurada: Managed Identity + roles RBAC (não credenciais soltas), segredos no Key Vault.
5. Enviar uma avaliação normal via Postman/curl — mostrar salvando no banco.
6. Enviar uma avaliação crítica — mostrar o e-mail de notificação chegando (via `FeedbackCriticoTrigger` → `FeedbackCriticoResource`).
7. Mostrar a função `RelatorioAgendado` rodando (disparo manual pelo portal ou aguardar o timer) e o relatório sendo persistido — consultar via `GET /relatorios/{id}`.
8. Mostrar o Application Insights com logs/telemetria dos dois serviços e os 2 alertas configurados.
9. Fechar mostrando o repositório no GitHub (código-fonte aberto).

### Passo 9 — Entregar

- Link do repositório GitHub (público): https://github.com/torresvictor100/edu-feedback
- Vídeo de demonstração.
- Conferir que a branch `main` está com o código mais recente (`git status`/`git log`) antes de entregar.

---

## Observação sobre este documento

Atualize a coluna "Status" conforme for completando os passos — este arquivo é o rastreador da entrega, diferente de `docs/PRODUCTION-READINESS.md` (que é o rastreador técnico permanente do projeto).
