# Comandos de deploy — EduFeedback (Azure)

Checklist só com os comandos, na ordem certa de execução. Contexto e explicação de cada
recurso está em `docs/AZURE-DEPLOY.md`; este arquivo é o "cola" pra rodar direto no terminal.

Valores já usados neste ambiente (`dev`): resource group `rg-edu-feedback`, região
`brazilsouth`, sufixo `edufeedback01-dev`, ACR `acredufeedback01dev`. Ajuste se subir um
ambiente novo (`staging`/`prod`) com `projectName`/`environmentName` diferentes.

## 1. Login na Azure

```bash
az login
az account list -o table
az account set --subscription "<subscription-id>"
az account show
```

## 2. Criar o resource group (só na primeira vez)

```bash
az group create --name rg-edu-feedback --location brazilsouth
```

## 3. Preparar os parâmetros reais (só na primeira vez, ou ao trocar segredo)

```bash
cp infra/azure/main.parameters.example.json infra/azure/main.parameters.json
# edite infra/azure/main.parameters.json com valores reais:
#   postgresAdminPassword, jwtSecret, internalTriggerSecret, alertNotificationEmail
# NUNCA commitar esse arquivo (já está no .gitignore).
```

## 4. Validar o template (dry-run, sempre antes de aplicar)

```bash
az deployment group validate \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json
```

## 5. Provisionar/atualizar a infraestrutura

```bash
az deployment group create \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json
```

Se der erro de nome de recurso (ex.: `ContainerAppInvalidName`), o nome estourou o limite
de caracteres do tipo de recurso — corrigir no `main.bicep` antes de rodar de novo, não só
tentar de novo.

## 6. Verificar se o provisionamento subiu certo (antes de ir pro build das imagens)

```bash
# Status do deployment em si (sucesso/erro) e outputs do template
az deployment group show \
  --resource-group rg-edu-feedback \
  --name main \
  --query "{status:properties.provisioningState, outputs:properties.outputs}"

# Todos os recursos que deveriam ter sido criados no grupo
az resource list -g rg-edu-feedback -o table

# Os 2 Container Apps existem
az containerapp list -g rg-edu-feedback -o table

# Os 2 Jobs existem (nomes curtos: job-relat-*, job-crit-* — ver ADR-008 em docs/DECISIONS.md)
az containerapp job list -g rg-edu-feedback -o table

# Key Vault com os 4 segredos esperados
az keyvault secret list --vault-name kv-edufeedback01-dev -o table

# Cada Container App conseguiu ler os segredos do Key Vault e ficou "Running" na subida
# (se a role RBAC ainda não propagou, pode aparecer erro de Key Vault aqui — ver item 8 de
# "Ações humanas obrigatórias" em docs/AZURE-DEPLOY.md; nesse caso, reiniciar a revision)
az containerapp show --name app-edufeedback01-dev -g rg-edu-feedback --query "properties.runningStatus" -o tsv
az containerapp show --name app-func-edufeedback01-dev -g rg-edu-feedback --query "properties.runningStatus" -o tsv

# Revisions das 2 apps — normal já aparecerem rodando a imagem placeholder
# (mcr.microsoft.com/k8se/quickstart) até o passo 8 substituir pela imagem real
az containerapp revision list --name app-edufeedback01-dev -g rg-edu-feedback -o table
az containerapp revision list --name app-func-edufeedback01-dev -g rg-edu-feedback -o table
```

Se algum desses comandos não retornar o recurso esperado, pare aqui e resolva antes de
seguir — build/deploy de imagem em cima de infra incompleta só mascara o problema.

## 7. Build e push das imagens no ACR (manual, enquanto o OIDC do pipeline não estiver configurado)

```bash
# Serviço A (Spring Boot)
az acr build \
  --registry acredufeedback01dev \
  --image edu-feedback-backend:v1 \
  --file backend/Dockerfile backend

# Serviço B (Quarkus)
az acr build \
  --registry acredufeedback01dev \
  --image edu-feedback-functions:v1 \
  --file functions/Dockerfile functions
```

Conferir o que já está publicado:

```bash
az acr repository list --name acredufeedback01dev -o table
az acr repository show-tags --name acredufeedback01dev --repository edu-feedback-backend -o table
az acr repository show-tags --name acredufeedback01dev --repository edu-feedback-functions -o table
```

## 8. Apontar os Container Apps para as imagens publicadas

```bash
az containerapp update \
  --name app-edufeedback01-dev \
  -g rg-edu-feedback \
  --image acredufeedback01dev.azurecr.io/edu-feedback-backend:v1

az containerapp update \
  --name app-func-edufeedback01-dev \
  -g rg-edu-feedback \
  --image acredufeedback01dev.azurecr.io/edu-feedback-functions:v1
```

## 9. Verificação pós-deploy (agora com a imagem real rodando)

```bash
# Serviço A responde
curl https://app-edufeedback01-dev.proudplant-0fc9b585.brazilsouth.azurecontainerapps.io/actuator/health

# Revisions saudáveis, sem loop de restart (comparar com o passo 6 — a imagem deve ter mudado)
az containerapp revision list --name app-edufeedback01-dev -g rg-edu-feedback -o table
az containerapp revision list --name app-func-edufeedback01-dev -g rg-edu-feedback -o table

# Confirmar que o Serviço B não está mais em "startup probe failed" (sinal de imagem placeholder)
az containerapp revision show \
  --name app-func-edufeedback01-dev \
  -g rg-edu-feedback \
  --revision $(az containerapp revision list --name app-func-edufeedback01-dev -g rg-edu-feedback --query "[0].name" -o tsv) \
  --query "{healthState:properties.healthState, runningState:properties.runningState}"
```

## 10. Disparar os Jobs manualmente (sem esperar o agendamento/fila) e ver se rodaram

```bash
# Job do relatório agendado (cron semanal, ver main.bicep: cronRelatorioAgendado)
az containerapp job start --name job-relat-edufeedback01-dev -g rg-edu-feedback

# Job do feedback crítico (normalmente disparado por KEDA via fila; start manual também funciona pra teste)
az containerapp job start --name job-crit-edufeedback01-dev -g rg-edu-feedback

# Conferir status das execuções — repetir até sair de "Running" (Succeeded ou Failed)
az containerapp job execution list --name job-relat-edufeedback01-dev -g rg-edu-feedback -o table
az containerapp job execution list --name job-crit-edufeedback01-dev -g rg-edu-feedback -o table
```

## 11. Investigar logs (Application Insights / Log Analytics)

```bash
WS_ID=$(az monitor log-analytics workspace show -g rg-edu-feedback -n log-edufeedback01-dev --query customerId -o tsv)

# Logs de console dos Container Apps (App A e App B)
az monitor log-analytics query -w "$WS_ID" --analytics-query "
ContainerAppConsoleLogs_CL
| where TimeGenerated > ago(1h)
| project TimeGenerated, ContainerAppName_s, Log_s
| order by TimeGenerated desc
| take 50
" -o table

# Eventos de sistema (probes, scale, jobs, pods) — geralmente o mais útil pra diagnosticar falha
az monitor log-analytics query -w "$WS_ID" --analytics-query "
ContainerAppSystemLogs_CL
| where TimeGenerated > ago(1h)
| project TimeGenerated, ContainerAppName_s, Log_s, Reason_s, Type_s
| order by TimeGenerated desc
| take 50
" -o table
```

`az containerapp job logs show --name <job> -g rg-edu-feedback --container trigger` também
funciona, mas só enquanto o pod da execução ainda existe (some rápido) — o Log Analytics é
mais confiável pra olhar depois do fato.

## 12. Consultar direto no Postgres (não existe endpoint de listagem de relatórios, é intencional)

```bash
psql "host=psql-edufeedback01-dev.postgres.database.azure.com port=5432 dbname=edufeedback user=edufeedback_admin sslmode=require" \
  -c "SELECT id, status, solicitado_em FROM relatorios ORDER BY solicitado_em DESC LIMIT 5;"
```

## 13. Rollback rápido (se um deploy quebrar)

```bash
az containerapp revision list --name app-edufeedback01-dev -g rg-edu-feedback -o table
az containerapp update --name app-edufeedback01-dev -g rg-edu-feedback --image <imagem-anterior>
# ou, sem novo build:
az containerapp revision activate --revision <revision-anterior>
az containerapp revision deactivate --revision <revision-com-problema>
```

## 14. Pendência conhecida (não precisa pra subir manualmente, mas destrava o pipeline automático)

O `.github/workflows/deploy-azure.yml` já existe mas nunca rodou — faltam os secrets de
OIDC no ambiente `production` do GitHub. Enquanto isso não estiver configurado, repita os
passos 7 e 8 manualmente a cada mudança de código. Setup do OIDC (`docs/AZURE-DEPLOY.md`,
seção "Ações humanas obrigatórias", itens 3-4):

```bash
# criar o App Registration
az ad app create --display-name "edu-feedback-github-oidc"
APP_ID=$(az ad app list --display-name "edu-feedback-github-oidc" --query "[0].appId" -o tsv)
az ad sp create --id "$APP_ID"

# criar a federated credential apontando pro repo/branch/environment "production"
az ad app federated-credential create --id "$APP_ID" --parameters '{
  "name": "edu-feedback-production",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:<org>/<repo>:environment:production",
  "audiences": ["api://AzureADTokenExchange"]
}'

# dar ao service principal a role necessária no resource group
az role assignment create \
  --assignee "$APP_ID" \
  --role Contributor \
  --scope /subscriptions/<subscription-id>/resourceGroups/rg-edu-feedback

# configurar no GitHub (Settings → Environments → production → Secrets):
#   AZURE_CLIENT_ID=$APP_ID
#   AZURE_TENANT_ID=<tenant-id>
#   AZURE_SUBSCRIPTION_ID=<subscription-id>
#   AZURE_ACR_NAME=acredufeedback01dev
#   AZURE_RESOURCE_GROUP=rg-edu-feedback
```

~~Atenção: o `deploy-azure.yml` hoje usa `app-edu-feedback`/`app-func-edu-feedback` como nome
dos Container Apps (linhas 48 e 76) — não bate com os nomes reais provisionados.~~ ✅ Corrigido
em 2026-07-27: o workflow já usa `app-edufeedback01-dev`/`app-func-edufeedback01-dev`.

## 15. Governança do repositório GitHub (revisar/remover quando não precisar mais)

Configurado em 2026-07-27 porque o repo é público (exigência do enunciado) e o workflow de
deploy dispara em `push` para `main` — essas 3 travas impedem alguém sem permissão de
push/PR de acionar ou aprovar um deploy sem você saber. Nenhuma delas afeta o pipeline em si
(o workflow só dispara em `push`/`workflow_dispatch`, nunca em `pull_request`, e a federated
credential só aceita token vindo deste repo exato — um fork não consegue autenticar de
qualquer forma).

**Interaction limit** — só colaboradores (hoje, só você) podem abrir PR/issue/comentário.
**Expira sozinho em 2027-01-27** (teto de 6 meses da API do GitHub, não existe opção
permanente) — renove rodando de novo antes de vencer, ou remova se não precisar mais:

```bash
# renovar (mais 6 meses a partir de quando rodar)
gh api --method PUT repos/torresvictor100/edu-feedback/interaction-limits \
  --input - <<'EOF'
{
  "limit": "collaborators_only",
  "expiry": "six_months"
}
EOF

# ver se está ativo e quando expira
gh api repos/torresvictor100/edu-feedback/interaction-limits

# remover de vez
gh api --method DELETE repos/torresvictor100/edu-feedback/interaction-limits
```

**Branch protection em `main`** — bloqueia push direto/force-push de quem não é admin do
repo; exige PR com 1 aprovação (você, como admin, pode mergear sem esperar segunda pessoa
porque `enforce_admins` está `false`):

```bash
gh api --method PUT repos/torresvictor100/edu-feedback/branches/main/protection \
  --input - <<'EOF'
{
  "required_status_checks": null,
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF

# remover a proteção
gh api --method DELETE repos/torresvictor100/edu-feedback/branches/main/protection
```

**Required reviewer no Environment `production`** — pausa a run do Actions esperando um
clique manual seu de "Approve" antes de rodar os steps de deploy (autenticação Azure só
acontece depois disso):

```bash
MY_ID=$(gh api user --jq .id)

gh api --method PUT repos/torresvictor100/edu-feedback/environments/production \
  --input - <<EOF
{
  "reviewers": [{"type": "User", "id": $MY_ID}],
  "deployment_branch_policy": {
    "protected_branches": true,
    "custom_branch_policies": false
  }
}
EOF

# remover os reviewers exigidos (mantém o environment, só tira a trava de aprovação)
gh api --method PUT repos/torresvictor100/edu-feedback/environments/production \
  --input - <<'EOF'
{"reviewers": []}
EOF
```
