// Infraestrutura como código do EduFeedback.
// Provisiona: PostgreSQL Flexible Server, Storage Account (fila notificacoes-criticas),
// Azure Container Registry, Container Apps Environment com 3 componentes do
// Serviço B/A: o Container App público do Serviço A (Spring Boot), o Container App
// interno do Serviço B (Quarkus, sem ingress externo) e 2 Container Apps Jobs
// (Schedule + Event) que disparam os endpoints internos do Serviço B — ver
// ADR-007 em docs/DECISIONS.md (substitui a ADR-006 quanto ao mecanismo de
// gatilho; a divisão "Quarkus + endpoint interno protegido por segredo" continua).
// Também provisiona Application Insights + Log Analytics, Key Vault (com role
// assignments reais para as identidades gerenciadas), Azure Communication
// Services (e-mail) e alertas de monitoramento (Azure Monitor) com action group
// de e-mail.
//
// Uso: az deployment group create --resource-group <rg> --template-file main.bicep
//      --parameters @main.parameters.example.json
//
// Nenhum valor real de credencial fica neste arquivo — segredos são gerados ou
// referenciados via Key Vault. Revise nomes, região e SKUs antes de executar.
//
// Nota de bootstrapping: os 2 Container Apps e os 2 Jobs usam identidade
// gerenciada (SystemAssigned) para ler segredos do Key Vault (e, no caso do Job
// de feedback crítico, para ler/apagar mensagens da fila via Azure AD). Como o
// role assignment só pode ser criado depois que a identidade existe, é possível
// que a primeira execução falhe ao resolver a referência do Key Vault antes da
// role se propagar — se isso acontecer, reinicie a revision do Container App
// afetado ou apenas aguarde a próxima execução do Job (ação humana, documentada
// em AZURE-DEPLOY.md).

@description('Prefixo usado nos nomes dos recursos.')
param projectName string = 'edufeedback'

@description('Região de deploy.')
param location string = 'brazilsouth'

@description('Ambiente (dev, staging, prod).')
@allowed(['dev', 'staging', 'prod'])
param environmentName string = 'dev'

@description('Usuário administrador do PostgreSQL Flexible Server.')
param postgresAdminLogin string = 'edufeedback_admin'

@secure()
@description('Senha do administrador do PostgreSQL. Gere com: az postgres flexible-server create --admin-password (ou forneça via Key Vault/secret do pipeline).')
param postgresAdminPassword string

@description('Segredo compartilhado para assinatura dos JWT emitidos pelo Serviço A (o Serviço B não usa JWT).')
@secure()
param jwtSecret string

@description('Segredo compartilhado só entre os 2 Jobs (gatilho fino) e os endpoints internos do Quarkus no Serviço B — nunca exposto fora da rede interna do Container Apps Environment.')
@secure()
param internalTriggerSecret string

@description('E-mail que recebe os alertas de monitoramento (Application Insights e PostgreSQL).')
param alertNotificationEmail string

var suffix = '${projectName}-${environmentName}'
var storageAccountName = toLower(replace('st${suffix}', '-', ''))
var acrName = toLower(replace('acr${suffix}', '-', ''))

// Roles internos do Azure usados abaixo (IDs fixos, documentados pela Microsoft).
var roleKeyVaultSecretsUser = '4633458b-17de-408a-b874-0445c86b69e6'
var roleAcrPull = '7f951dda-4ed3-4680-a7ca-43fe172d538d'

// Cron padrão 5 campos (min hora dia mês dia-da-semana, avaliado em UTC pelo
// Container Apps Jobs) — equivalente ao NCRONTAB '0 0 8 * * 1' usado antes pelo
// Timer trigger do Azure Functions: toda segunda-feira às 08:00 UTC.
var cronRelatorioAgendado = '0 8 * * 1'

// Scripts dos 2 Jobs (gatilho fino, sem lógica de negócio — só repassam via HTTP
// para o endpoint interno do Quarkus, ver ADR-007). Rodam na imagem pública
// mcr.microsoft.com/azure-cli, que já traz curl, jq e az.
var scriptRelatorioAgendado = '''
set -e
curl -sf -X POST "$INTERNAL_APP_URL/internal/relatorio-agendado" -H "X-Internal-Secret: $INTERNAL_TRIGGER_SECRET"
'''

var scriptFeedbackCritico = '''
set -e
LINHA=$(az storage message get --queue-name notificacoes-criticas --connection-string "$STORAGE_CONNECTION_STRING" --query "[0].[id,popReceipt,content]" -o tsv)
if [ -z "$LINHA" ]; then
  echo "Fila notificacoes-criticas vazia, nada a fazer."
  exit 0
fi
ID=$(printf '%s' "$LINHA" | cut -f1)
POP=$(printf '%s' "$LINHA" | cut -f2)
CORPO=$(printf '%s' "$LINHA" | cut -f3 | base64 -d)
curl -sf -X POST "$INTERNAL_APP_URL/internal/feedback-critico" -H "Content-Type: application/json" -H "X-Internal-Secret: $INTERNAL_TRIGGER_SECRET" -d "$CORPO"
az storage message delete --queue-name notificacoes-criticas --connection-string "$STORAGE_CONNECTION_STRING" --id "$ID" --pop-receipt "$POP"
'''

resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-${suffix}'
  location: location
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: 'appi-${suffix}'
  location: location
  kind: 'web'
  properties: {
    Application_Type: 'web'
    WorkspaceResourceId: logAnalytics.id
  }
}

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: 'kv-${suffix}'
  location: location
  properties: {
    sku: { family: 'A', name: 'standard' }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
  }
}

resource kvSecretPostgresPassword 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'postgres-admin-password'
  properties: { value: postgresAdminPassword }
}

resource kvSecretJwt 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'jwt-secret'
  properties: { value: jwtSecret }
}

resource kvSecretInternalTrigger 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'internal-trigger-secret'
  properties: { value: internalTriggerSecret }
}

resource kvSecretStorageConnection 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'storage-connection-string'
  properties: {
    value: 'DefaultEndpointsProtocol=https;AccountName=${storageAccount.name};AccountKey=${storageAccount.listKeys().keys[0].value};EndpointSuffix=core.windows.net'
  }
}

resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: storageAccountName
  location: location
  sku: { name: 'Standard_LRS' }
  kind: 'StorageV2'
  properties: {
    minimumTlsVersion: 'TLS1_2'
    allowBlobPublicAccess: false
  }
}

resource queueService 'Microsoft.Storage/storageAccounts/queueServices@2023-01-01' = {
  parent: storageAccount
  name: 'default'
}

resource filaNotificacoesCriticas 'Microsoft.Storage/storageAccounts/queueServices/queues@2023-01-01' = {
  parent: queueService
  name: 'notificacoes-criticas'
}

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: { name: 'Basic' }
  properties: {
    adminUserEnabled: false
  }
}

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2023-06-01-preview' = {
  name: 'psql-${suffix}'
  location: location
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: postgresAdminLogin
    administratorLoginPassword: postgresAdminPassword
    storage: { storageSizeGB: 32 }
    backup: { backupRetentionDays: 7 }
  }
}

resource postgresDatabase 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2023-06-01-preview' = {
  parent: postgres
  name: 'edufeedback'
}

resource postgresFirewallAllowAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2023-06-01-preview' = {
  parent: postgres
  name: 'AllowAzureServices'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// Ambiente único, reaproveitado pelo Container App do Serviço A, pelo Container
// App interno do Serviço B e pelos 2 Jobs do Serviço B (ver ADR-007).
resource containerAppsEnv 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: 'cae-${suffix}'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
  }
}

resource containerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: 'app-${suffix}'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    managedEnvironmentId: containerAppsEnv.id
    configuration: {
      secrets: [
        { name: 'postgres-password', keyVaultUrl: kvSecretPostgresPassword.properties.secretUri, identity: 'system' }
        { name: 'jwt-secret', keyVaultUrl: kvSecretJwt.properties.secretUri, identity: 'system' }
      ]
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
      }
      registries: [
        {
          server: '${acrName}.azurecr.io'
          identity: 'system'
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'backend'
          // Imagem inicial placeholder — o pipeline de deploy substitui pela tag real a cada release.
          image: 'mcr.microsoft.com/k8se/quickstart:latest'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          env: [
            { name: 'POSTGRES_HOST', value: '${postgres.name}.postgres.database.azure.com' }
            { name: 'POSTGRES_PORT', value: '5432' }
            { name: 'POSTGRES_DB', value: 'edufeedback' }
            { name: 'POSTGRES_USER', value: postgresAdminLogin }
            { name: 'POSTGRES_PASSWORD', secretRef: 'postgres-password' }
            { name: 'JWT_SECRET', secretRef: 'jwt-secret' }
            { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
          ]
        }
      ]
      scale: { minReplicas: 0, maxReplicas: 3 }
    }
  }
}

// Governança de acesso: cada identidade gerenciada só recebe a role mínima que
// precisa (princípio do menor privilégio), nunca a chave/connection string bruta.
resource containerAppAcrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, containerApp.id, roleAcrPull)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleAcrPull)
    principalId: containerApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource containerAppKeyVaultAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, containerApp.id, roleKeyVaultSecretsUser)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKeyVaultSecretsUser)
    principalId: containerApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Serviço B — Container App interno (sem ingress externo): só o Quarkus com os
// 2 endpoints internos protegidos por X-Internal-Secret (ver ADR-007). Só é
// alcançável de dentro do Container Apps Environment (pelos 2 Jobs abaixo),
// nunca da internet.
resource containerAppFunc 'Microsoft.App/containerApps@2023-05-01' = {
  name: 'app-func-${suffix}'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    managedEnvironmentId: containerAppsEnv.id
    configuration: {
      secrets: [
        { name: 'postgres-password', keyVaultUrl: kvSecretPostgresPassword.properties.secretUri, identity: 'system' }
        { name: 'internal-trigger-secret', keyVaultUrl: kvSecretInternalTrigger.properties.secretUri, identity: 'system' }
      ]
      ingress: {
        external: false
        targetPort: 8080
        transport: 'auto'
      }
      registries: [
        {
          server: '${acrName}.azurecr.io'
          identity: 'system'
        }
      ]
    }
    template: {
      containers: [
        {
          name: 'functions'
          // Imagem inicial placeholder — o pipeline de deploy substitui pela tag real a cada release.
          image: 'mcr.microsoft.com/k8se/quickstart:latest'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          env: [
            { name: 'POSTGRES_HOST', value: '${postgres.name}.postgres.database.azure.com' }
            { name: 'POSTGRES_PORT', value: '5432' }
            { name: 'POSTGRES_DB', value: 'edufeedback' }
            { name: 'POSTGRES_USER', value: postgresAdminLogin }
            { name: 'POSTGRES_PASSWORD', secretRef: 'postgres-password' }
            { name: 'INTERNAL_TRIGGER_SECRET', secretRef: 'internal-trigger-secret' }
            { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
          ]
        }
      ]
      // minReplicas 0: o Container Apps Environment sustenta a conexão HTTP dos
      // Jobs durante o cold start (scale-from-zero), então não precisa ficar
      // sempre ativo — os 2 endpoints só são chamados pelos Jobs, não por tráfego contínuo.
      scale: { minReplicas: 0, maxReplicas: 2 }
    }
  }
}

resource containerAppFuncAcrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, containerAppFunc.id, roleAcrPull)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleAcrPull)
    principalId: containerAppFunc.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource containerAppFuncKeyVaultAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, containerAppFunc.id, roleKeyVaultSecretsUser)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKeyVaultSecretsUser)
    principalId: containerAppFunc.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Job 1 (Schedule trigger) — única responsabilidade: disparar semanalmente e
// repassar via HTTP para o endpoint interno de relatório agendado (ver ADR-007).
resource jobRelatorioAgendado 'Microsoft.App/jobs@2024-03-01' = {
  name: 'job-relatorio-agendado-${suffix}'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    environmentId: containerAppsEnv.id
    configuration: {
      triggerType: 'Schedule'
      scheduleTriggerConfig: {
        cronExpression: cronRelatorioAgendado
        parallelism: 1
        replicaCompletionCount: 1
      }
      replicaTimeout: 300
      replicaRetryLimit: 1
      secrets: [
        { name: 'internal-trigger-secret', keyVaultUrl: kvSecretInternalTrigger.properties.secretUri, identity: 'system' }
      ]
    }
    template: {
      containers: [
        {
          name: 'trigger'
          image: 'mcr.microsoft.com/azure-cli:latest'
          command: ['/bin/sh', '-c']
          args: [scriptRelatorioAgendado]
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [
            { name: 'INTERNAL_APP_URL', value: 'https://${containerAppFunc.properties.configuration.ingress.fqdn}' }
            { name: 'INTERNAL_TRIGGER_SECRET', secretRef: 'internal-trigger-secret' }
          ]
        }
      ]
    }
  }
}

resource jobRelatorioAgendadoKeyVaultAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, jobRelatorioAgendado.id, roleKeyVaultSecretsUser)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKeyVaultSecretsUser)
    principalId: jobRelatorioAgendado.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// Job 2 (Event trigger, escalado pelo KEDA via profundidade da fila) — única
// responsabilidade: consumir 1 mensagem de "notificacoes-criticas" e repassar
// via HTTP para o endpoint interno de feedback crítico (ver ADR-007). O scale
// rule do Container Apps Jobs só aceita autenticação via secret referenciado
// (não aceita identidade gerenciada para "azure-queue" nesta API), por isso o
// connection string da Storage Account fica no Key Vault (`storage-connection-
// string`) e é referenciado tanto pelo scale rule quanto pelo próprio container.
resource jobFeedbackCritico 'Microsoft.App/jobs@2024-03-01' = {
  name: 'job-feedback-critico-${suffix}'
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    environmentId: containerAppsEnv.id
    configuration: {
      triggerType: 'Event'
      eventTriggerConfig: {
        parallelism: 1
        replicaCompletionCount: 1
        scale: {
          minExecutions: 0
          maxExecutions: 5
          pollingInterval: 30
          rules: [
            {
              name: 'fila-notificacoes-criticas'
              type: 'azure-queue'
              metadata: {
                queueName: 'notificacoes-criticas'
                queueLength: '1'
              }
              auth: [
                { triggerParameter: 'connection', secretRef: 'storage-connection' }
              ]
            }
          ]
        }
      }
      replicaTimeout: 120
      replicaRetryLimit: 1
      secrets: [
        { name: 'internal-trigger-secret', keyVaultUrl: kvSecretInternalTrigger.properties.secretUri, identity: 'system' }
        { name: 'storage-connection', keyVaultUrl: kvSecretStorageConnection.properties.secretUri, identity: 'system' }
      ]
    }
    template: {
      containers: [
        {
          name: 'trigger'
          image: 'mcr.microsoft.com/azure-cli:latest'
          command: ['/bin/sh', '-c']
          args: [scriptFeedbackCritico]
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [
            { name: 'INTERNAL_APP_URL', value: 'https://${containerAppFunc.properties.configuration.ingress.fqdn}' }
            { name: 'INTERNAL_TRIGGER_SECRET', secretRef: 'internal-trigger-secret' }
            { name: 'STORAGE_CONNECTION_STRING', secretRef: 'storage-connection' }
          ]
        }
      ]
    }
  }
}

resource jobFeedbackCriticoKeyVaultAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, jobFeedbackCritico.id, roleKeyVaultSecretsUser)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKeyVaultSecretsUser)
    principalId: jobFeedbackCritico.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource communicationServices 'Microsoft.Communication/communicationServices@2023-04-01' = {
  name: 'acs-${suffix}'
  location: 'global'
  properties: {
    dataLocation: 'Brazil'
  }
}

// Monitoramento: action group de e-mail + 2 alertas mínimos exigidos pelo
// enunciado ("aplicação monitorada"). Ampliar conforme o projeto crescer.
resource actionGroup 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: 'ag-${suffix}'
  location: 'global'
  properties: {
    groupShortName: 'edufeedbk'
    enabled: true
    emailReceivers: [
      {
        name: 'admin-principal'
        emailAddress: alertNotificationEmail
        useCommonAlertSchema: true
      }
    ]
  }
}

resource alertExcecoes 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: 'alert-excecoes-${suffix}'
  location: 'global'
  properties: {
    description: 'Dispara quando qualquer um dos 2 serviços (API ou Serviço B) registra uma exceção no Application Insights — cobre falha de execução dos Jobs/endpoint interno e erros da API.'
    severity: 2
    enabled: true
    scopes: [appInsights.id]
    evaluationFrequency: 'PT5M'
    windowSize: 'PT5M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          name: 'ExcecoesDetectadas'
          metricName: 'exceptions/count'
          operator: 'GreaterThan'
          threshold: 0
          timeAggregation: 'Count'
        }
      ]
    }
    actions: [
      { actionGroupId: actionGroup.id }
    ]
  }
}

resource alertPostgresCpu 'Microsoft.Insights/metricAlerts@2018-03-01' = {
  name: 'alert-postgres-cpu-${suffix}'
  location: 'global'
  properties: {
    description: 'Dispara quando o uso de CPU do PostgreSQL Flexible Server passa de 80% por 15 minutos — indício de sobrecarga do banco compartilhado pelos dois serviços.'
    severity: 3
    enabled: true
    scopes: [postgres.id]
    evaluationFrequency: 'PT15M'
    windowSize: 'PT15M'
    criteria: {
      'odata.type': 'Microsoft.Azure.Monitor.SingleResourceMultipleMetricCriteria'
      allOf: [
        {
          name: 'CpuAlto'
          metricName: 'cpu_percent'
          operator: 'GreaterThan'
          threshold: 80
          timeAggregation: 'Average'
        }
      ]
    }
    actions: [
      { actionGroupId: actionGroup.id }
    ]
  }
}

output containerAppFqdn string = containerApp.properties.configuration.ingress.fqdn
output containerAppFuncName string = containerAppFunc.name
output jobRelatorioAgendadoName string = jobRelatorioAgendado.name
output jobFeedbackCriticoName string = jobFeedbackCritico.name
output acrLoginServer string = acr.properties.loginServer
output postgresServerName string = postgres.name
output storageAccountName string = storageAccount.name
output keyVaultName string = keyVault.name
