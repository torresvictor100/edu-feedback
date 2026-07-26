# Publicação na Azure — EduFeedback

Este runbook prepara a primeira publicação de `edu-feedback` sem armazenar credenciais de longa duração no repositório.

## Arquitetura provisionada

`infra/azure/main.bicep` provisiona, num único resource group:

| Recurso | Nome (padrão `{recurso}-edufeedback-{ambiente}`) | Papel |
|---|---|---|
| PostgreSQL Flexible Server | `psql-edufeedback-<ambiente>` | Banco compartilhado pelos dois serviços |
| Storage Account + 1 fila | `stedufeedback<ambiente>` | Fila `notificacoes-criticas` |
| Azure Container Registry | `acredufeedback<ambiente>` | Imagem do Serviço A e do Serviço B |
| Container Apps Environment | `cae-edufeedback-<ambiente>` | Ambiente único, compartilhado pelos 2 Container Apps e pelos 2 Jobs abaixo |
| Container App (Serviço A) | `app-edufeedback-<ambiente>` | API pública (Spring Boot), ingress externo |
| Container App (Serviço B) | `app-func-edufeedback-<ambiente>` | Endpoints internos Quarkus, **sem ingress externo** — só alcançável de dentro do Container Apps Environment |
| Container Apps Job (Schedule) | `job-relatorio-agendado-edufeedback-<ambiente>` | Gatilho fino semanal — chama `POST /internal/relatorio-agendado` |
| Container Apps Job (Event/KEDA) | `job-feedback-critico-edufeedback-<ambiente>` | Gatilho fino escalado pela fila `notificacoes-criticas` — chama `POST /internal/feedback-critico` |
| Application Insights + Log Analytics | `appi-`/`log-edufeedback-<ambiente>` | Observabilidade dos dois serviços |
| Key Vault + 4 segredos | `kv-edufeedback-<ambiente>` | Guarda `postgres-admin-password`, `jwt-secret`, `internal-trigger-secret` e `storage-connection-string` (RBAC habilitado) |
| Azure Communication Services | `acs-edufeedback-<ambiente>` | Envio de e-mail |
| Action Group + 2 Metric Alerts | `ag-`/`alert-excecoes-`/`alert-postgres-cpu-edufeedback-<ambiente>` | Alerta por e-mail em exceções (App Insights) e CPU alta do Postgres |

Região parametrizada (`location`, padrão `brazilsouth`). Ambiente (`dev`/`staging`/`prod`) é parâmetro do template. Ver ADR-007 em `DECISIONS.md` para o histórico dessa migração (Azure Functions → Container Apps Jobs).

**Governança de acesso (RBAC real, não só documentado):** os 2 Container Apps e os 2 Jobs recebem identidade gerenciada (`SystemAssigned`) própria, e o template concede exatamente as roles que cada um precisa — nada mais:
- Container App do Serviço A → `AcrPull` no Container Registry + `Key Vault Secrets User` no Key Vault (lê `postgres-admin-password` e `jwt-secret` via referência nativa, nunca em variável de ambiente em texto puro).
- Container App do Serviço B → `AcrPull` no Container Registry + `Key Vault Secrets User` no Key Vault (lê `postgres-admin-password` e `internal-trigger-secret`).
- Job `job-relatorio-agendado` → `Key Vault Secrets User` no Key Vault (lê `internal-trigger-secret`).
- Job `job-feedback-critico` → `Key Vault Secrets User` no Key Vault (lê `internal-trigger-secret` e `storage-connection-string`, este último usado tanto pelo scale rule KEDA `azure-queue` quanto pelos comandos `az storage message` dentro do próprio container).

Nenhuma credencial é passada como texto puro para nenhum dos 2 Container Apps ou dos 2 Jobs — todos os segredos (`postgres-password`, `jwt-secret`, `internal-trigger-secret`, `storage-connection`) são referências ao Key Vault (`keyVaultUrl` + identidade gerenciada), nunca valor em texto puro no template ou nas variáveis de ambiente.

## O que já está automatizado

- Todo o Bicep de `infra/azure/main.bicep`: provisionamento declarativo dos recursos acima.
- `.github/workflows/ci.yml`: build e testes dos dois serviços a cada push/PR.
- `.github/workflows/deploy-azure.yml`: login OIDC, build e push das imagens dos 2 Container Apps no ACR (Serviço A e Serviço B) e atualização de ambos via `az containerapp update`. Os 2 Jobs usam a imagem pública `mcr.microsoft.com/azure-cli` (definida no Bicep) — não fazem parte do pipeline de deploy de código.
- `backend/Dockerfile` e `functions/Dockerfile`: build multistage, usuário não-root.

## Ações humanas obrigatórias

1. Ter (ou criar) uma assinatura Azure e um resource group de destino.
2. Autenticar localmente com `az login` para o primeiro provisionamento manual (ou configurar a federação OIDC abaixo para os provisionamentos seguintes via pipeline).
3. Criar a confiança OIDC entre GitHub Actions e Azure (App Registration + Federated Credential apontando para o repositório/branch/environment `production`) — **não usar client secret**.
4. Configurar no GitHub (Settings → Environments → `production`):
   - Secrets: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_ACR_NAME`, `AZURE_RESOURCE_GROUP`.
5. Revisar nomes, região, SKUs e o custo estimado dos recursos em `main.bicep` antes de qualquer `az deployment group create`.
6. Gerar valores reais para `postgresAdminPassword`, `jwtSecret` e `internalTriggerSecret` (nunca reutilizar os valores de exemplo de `.env.example` ou de `main.parameters.example.json`), e definir `alertNotificationEmail` com o e-mail que deve receber os alertas.
7. Após o primeiro deploy, trocar a senha do admin seed (`admin@edufeedback.local`) ou remover o seed e cadastrar o admin real.
8. Se algum dos 2 Container Apps ou 2 Jobs falhar ao ler o Key Vault logo após o primeiro deploy (a role de acesso pode levar alguns minutos para se propagar), reiniciar a revision do Container App afetado ou simplesmente aguardar a próxima execução do Job.
9. Aprovar explicitamente o primeiro deploy em produção.

Não execute comandos de criação sem revisar nomes, região, permissões e custo estimado. Use autenticação OIDC entre GitHub Actions e Azure; não salve senha de service principal no repositório.

## Primeiro provisionamento

```bash
# 1. Login e seleção da assinatura
az login
az account set --subscription "<subscription-id>"

# 2. Criar o resource group (se ainda não existir)
az group create --name rg-edu-feedback --location brazilsouth

# 3. Copiar e preencher os parâmetros reais (nunca commitar o arquivo preenchido)
cp infra/azure/main.parameters.example.json infra/azure/main.parameters.json
# edite infra/azure/main.parameters.json com valores reais de postgresAdminPassword, jwtSecret,
# internalTriggerSecret e alertNotificationEmail

# 4. Validar o template (dry-run)
az deployment group validate \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json

# 5. Provisionar de fato
az deployment group create \
  --resource-group rg-edu-feedback \
  --template-file infra/azure/main.bicep \
  --parameters @infra/azure/main.parameters.json
```

Após o provisionamento inicial, os deploys seguintes do código (imagens do Serviço A e do Serviço B) acontecem via `.github/workflows/deploy-azure.yml`.

## Verificação pós-deploy

- `curl https://<containerAppFqdn>/actuator/health` deve responder `{"status":"UP"}`.
- O Serviço B não expõe endpoint HTTP público — o Container App interno não tem ingress externo, só é alcançável de dentro do Container Apps Environment. Para testar os endpoints `/internal/*` sem esperar um disparo real dos Jobs, use `az containerapp exec` num dos 2 Container Apps do ambiente (ou rode manualmente um dos Jobs: `az containerapp job start --name job-relatorio-agendado-edufeedback-<ambiente> --resource-group <rg>`) e confirme 200 com o `X-Internal-Secret` correto (401 sem o cabeçalho).
- Verificar as execuções dos 2 Jobs pela aba "Execution history" do portal do Container Apps Job (`job-relatorio-agendado-...`, `job-feedback-critico-...`) — status "Succeeded" e sem erro — e pelos logs no Application Insights.
- Logs dos 2 Container Apps no Application Insights (`appi-edufeedback-<ambiente>`) — confirmar ausência de erros na inicialização.
- Confirmar que os 2 Container Apps conseguem conectar no PostgreSQL Flexible Server (verificar firewall rules — pode ser necessário liberar o IP de saída além da regra `AllowAzureServices` já criada pelo Bicep).
- Enviar um `POST /avaliação` de teste (nota ≤ 3) e confirmar que a fila `notificacoes-criticas` recebeu mensagem, que o Job `job-feedback-critico` disparou (escalado pelo KEDA) e que o e-mail chegou via Azure Communication Services.
- Rodar a coleção Postman (`postman/edu-feedback.postman_collection.json`) contra a URL real publicada do Serviço A.
- Verificar as "revisions" dos 2 Container Apps (`az containerapp revision list`) e o histórico de execução dos 2 Jobs (`az containerapp job execution list`).
- Confirmar que os 2 Metric Alerts (`alert-excecoes-...`, `alert-postgres-cpu-...`) aparecem como "Enabled" no portal (Monitor → Alerts → Alert rules) e que o Action Group recebeu a confirmação de e-mail (a Azure envia um e-mail de confirmação para `alertNotificationEmail` na primeira vez).
- Confirmar que os 2 Container Apps e os 2 Jobs conseguem ler os segredos do Key Vault (sem esse acesso, a aplicação/execução falha — ver item 8 das ações humanas acima).

## Rollback

- **Serviço A (Container App):** `az containerapp revision list` para localizar a revision anterior, depois `az containerapp update --image <imagem-anterior>` ou `az containerapp revision activate/deactivate` para reverter tráfego sem novo build.
- **Serviço B (Container App interno):** mesmo mecanismo do Serviço A — `az containerapp update --image <imagem-anterior>` no Container App `app-func-edufeedback-<ambiente>`.
- **Serviço B (Jobs):** os 2 Jobs (`job-relatorio-agendado`, `job-feedback-critico`) não versionam imagem própria — usam a imagem pública `mcr.microsoft.com/azure-cli` e um script definido no Bicep; qualquer rollback de comportamento passa por reverter `infra/azure/main.bicep` e reaplicar o deployment.
- **Banco de dados:** migrations Flyway são incrementais e não têm rollback automático — qualquer migration destrutiva precisa de uma migration de correção manual; nunca editar uma migration já aplicada em produção.
- Sempre validar que a migration aplicada é compatível com a versão do código para a qual está revertendo antes de trocar a imagem de volta.

## Referências oficiais

- Azure Container Apps para Java: https://learn.microsoft.com/azure/container-apps/java-overview
- Azure Container Apps Jobs: https://learn.microsoft.com/azure/container-apps/jobs
- Escalonamento de Container Apps (KEDA, `azure-queue`, autenticação por managed identity ou secret): https://learn.microsoft.com/azure/container-apps/scale-app
- GitHub Actions com OIDC na Azure: https://docs.github.com/actions/security-for-github-actions/security-hardening-your-deployments/configuring-openid-connect-in-azure
