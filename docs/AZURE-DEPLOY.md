# Publicação na Azure — EduFeedback

Este runbook prepara a primeira publicação de `edu-feedback` sem armazenar credenciais de longa duração no repositório.

## Arquitetura provisionada

`infra/azure/main.bicep` provisiona, num único resource group:

| Recurso | Nome (padrão `{recurso}-edufeedback-{ambiente}`) | Papel |
|---|---|---|
| PostgreSQL Flexible Server | `psql-edufeedback-<ambiente>` | Banco compartilhado pelos dois serviços |
| Storage Account + 2 filas | `stedufeedback<ambiente>` | Filas `solicitacoes-relatorio` e `notificacoes-criticas` |
| Azure Container Registry | `acredufeedback<ambiente>` | Imagem do Serviço A |
| Container Apps Environment + Container App | `cae-`/`app-edufeedback-<ambiente>` | Deploy do Serviço A (API) |
| Function App (Consumption, Linux, Java 21) + App Service Plan | `func-`/`asp-edufeedback-<ambiente>` | Deploy do Serviço B (funções) |
| Application Insights + Log Analytics | `appi-`/`log-edufeedback-<ambiente>` | Observabilidade dos dois serviços |
| Key Vault | `kv-edufeedback-<ambiente>` | Guarda de segredos (RBAC habilitado) |
| Azure Communication Services | `acs-edufeedback-<ambiente>` | Envio de e-mail |

Região parametrizada (`location`, padrão `brazilsouth`). Ambiente (`dev`/`staging`/`prod`) é parâmetro do template.

## O que já está automatizado

- Todo o Bicep de `infra/azure/main.bicep`: provisionamento declarativo dos recursos acima.
- `.github/workflows/ci.yml`: build e testes dos dois serviços a cada push/PR.
- `.github/workflows/deploy-azure.yml`: login OIDC, build e push da imagem no ACR, atualização do Container App, build e deploy do pacote de funções via `azure-functions-maven-plugin`.
- `backend/Dockerfile`: build multistage, usuário não-root.

## Ações humanas obrigatórias

1. Ter (ou criar) uma assinatura Azure e um resource group de destino.
2. Autenticar localmente com `az login` para o primeiro provisionamento manual (ou configurar a federação OIDC abaixo para os provisionamentos seguintes via pipeline).
3. Criar a confiança OIDC entre GitHub Actions e Azure (App Registration + Federated Credential apontando para o repositório/branch/environment `production`) — **não usar client secret**.
4. Configurar no GitHub (Settings → Environments → `production`):
   - Secrets: `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_ACR_NAME`, `AZURE_RESOURCE_GROUP`.
5. Revisar nomes, região, SKUs e o custo estimado dos recursos em `main.bicep` antes de qualquer `az deployment group create`.
6. Gerar valores reais para `postgresAdminPassword` e `jwtSecret` (nunca reutilizar os valores de exemplo de `.env.example` ou de `main.parameters.example.json`).
7. Após o primeiro deploy, trocar a senha do admin seed (`admin@edufeedback.local`) ou remover o seed e cadastrar o admin real.
8. Aprovar explicitamente o primeiro deploy em produção.

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
# edite infra/azure/main.parameters.json com valores reais de postgresAdminPassword e jwtSecret

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

Após o provisionamento inicial, os deploys seguintes do código (imagem do backend e pacote das funções) acontecem via `.github/workflows/deploy-azure.yml`.

## Verificação pós-deploy

- `curl https://<containerAppFqdn>/actuator/health` deve responder `{"status":"UP"}`.
- `curl https://<functionAppName>.azurewebsites.net/api/health` deve responder 200 (função HTTP do Serviço B).
- Logs do Container App e do Function App no Application Insights (`appi-edufeedback-<ambiente>`) — confirmar ausência de erros na inicialização.
- Confirmar que o Container App consegue conectar no PostgreSQL Flexible Server (verificar firewall rules — pode ser necessário liberar o IP de saída do Container App além da regra `AllowAzureServices` já criada pelo Bicep).
- Enviar um `POST /avaliação` de teste (nota ≤ 3) e confirmar que a fila `notificacoes-criticas` recebeu mensagem e que o e-mail chegou via Azure Communication Services.
- Rodar a coleção Postman (`postman/edu-feedback.postman_collection.json`) contra as URLs reais publicadas.
- Verificar as "revisions" do Container App (`az containerapp revision list`) e o status de execução das 4 funções no portal do Function App.

## Rollback

- **Serviço A (Container App):** `az containerapp revision list` para localizar a revision anterior, depois `az containerapp update --image <imagem-anterior>` ou `az containerapp revision activate/deactivate` para reverter tráfego sem novo build.
- **Serviço B (Function App):** reexecutar o workflow `deploy-azure.yml` apontando para o commit anterior (ou `git revert` + novo deploy), pois o Function App em Consumption plan não mantém histórico de "slots" por padrão.
- **Banco de dados:** migrations Flyway são incrementais e não têm rollback automático — qualquer migration destrutiva precisa de uma migration de correção manual; nunca editar uma migration já aplicada em produção.
- Sempre validar que a migration aplicada é compatível com a versão do código para a qual está revertendo antes de trocar a imagem/pacote de volta.

## Referências oficiais

- Azure Container Apps para Java: https://learn.microsoft.com/azure/container-apps/java-overview
- GitHub Actions com OIDC na Azure: https://docs.github.com/actions/security-for-github-actions/security-hardening-your-deployments/configuring-openid-connect-in-azure
- Quarkus em Azure Functions: https://quarkus.io/guides/azure-functions-http
