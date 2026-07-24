// Infraestrutura como código do EduFeedback.
// Provisiona: PostgreSQL Flexible Server, Storage Account (fila notificacoes-criticas),
// Azure Container Registry, Container Apps Environment + Container App (Serviço A),
// Function App Consumption (Serviço B: 2 gatilhos nativos — Timer e Queue — que
// chamam endpoints internos Quarkus no mesmo Function App, ver ADR-006), Application
// Insights + Log Analytics, Key Vault (com role assignments reais para as duas
// identidades gerenciadas), Azure Communication Services (e-mail) e alertas de
// monitoramento (Azure Monitor) com action group de e-mail.
//
// Uso: az deployment group create --resource-group <rg> --template-file main.bicep
//      --parameters @main.parameters.example.json
//
// Nenhum valor real de credencial fica neste arquivo — segredos são gerados ou
// referenciados via Key Vault. Revise nomes, região e SKUs antes de executar.
//
// Nota de bootstrapping: o Container App e o Function App usam identidade
// gerenciada (SystemAssigned) para ler segredos do Key Vault. Como o role
// assignment só pode ser criado depois que a identidade existe, é possível que
// a primeira inicialização falhe ao resolver a referência do Key Vault antes da
// role se propagar — se isso acontecer, reinicie a revision/o Function App
// depois do primeiro provisionamento (ação humana, documentada em AZURE-DEPLOY.md).

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

@description('Segredo compartilhado só entre os gatilhos nativos (Timer/Queue) e os endpoints internos do Quarkus no Serviço B — nunca exposto fora do próprio Function App.')
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
var roleStorageQueueDataContributor = '974c5e8b-45b9-4653-ba55-5f855dd0fb88'

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
      scale: { minReplicas: 1, maxReplicas: 3 }
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

resource functionAppPlan 'Microsoft.Web/serverfarms@2023-01-01' = {
  name: 'asp-${suffix}'
  location: location
  sku: { name: 'Y1', tier: 'Dynamic' }
}

resource functionApp 'Microsoft.Web/sites@2023-01-01' = {
  name: 'func-${suffix}'
  location: location
  kind: 'functionapp,linux'
  identity: { type: 'SystemAssigned' }
  properties: {
    serverFarmId: functionAppPlan.id
    siteConfig: {
      linuxFxVersion: 'JAVA|21'
      // WEBSITE_HOSTNAME não precisa ser definido aqui — a Azure injeta essa
      // variável automaticamente em todo App Service/Function App com o
      // hostname público do próprio app. Os gatilhos nativos (Timer/Queue)
      // usam ela para chamar os endpoints internos do Quarkus na mesma app.
      appSettings: [
        { name: 'FUNCTIONS_WORKER_RUNTIME', value: 'java' }
        { name: 'FUNCTIONS_EXTENSION_VERSION', value: '~4' }
        { name: 'AzureWebJobsStorage', value: 'DefaultEndpointsProtocol=https;AccountName=${storageAccount.name};EndpointSuffix=core.windows.net' }
        { name: 'AZURE_STORAGE_CONNECTION_STRING', value: 'DefaultEndpointsProtocol=https;AccountName=${storageAccount.name};EndpointSuffix=core.windows.net' }
        { name: 'POSTGRES_HOST', value: '${postgres.name}.postgres.database.azure.com' }
        { name: 'POSTGRES_PORT', value: '5432' }
        { name: 'POSTGRES_DB', value: 'edufeedback' }
        { name: 'POSTGRES_USER', value: postgresAdminLogin }
        { name: 'POSTGRES_PASSWORD', value: '@Microsoft.KeyVault(SecretUri=${kvSecretPostgresPassword.properties.secretUri})' }
        { name: 'INTERNAL_TRIGGER_SECRET', value: '@Microsoft.KeyVault(SecretUri=${kvSecretInternalTrigger.properties.secretUri})' }
        { name: 'RELATORIO_AGENDADO_CRON', value: '0 0 8 * * 1' }
        { name: 'APPINSIGHTS_INSTRUMENTATIONKEY', value: appInsights.properties.InstrumentationKey }
        { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
      ]
    }
  }
}

resource functionAppKeyVaultAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(keyVault.id, functionApp.id, roleKeyVaultSecretsUser)
  scope: keyVault
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleKeyVaultSecretsUser)
    principalId: functionApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource functionAppStorageQueueAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(storageAccount.id, functionApp.id, roleStorageQueueDataContributor)
  scope: storageAccount
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', roleStorageQueueDataContributor)
    principalId: functionApp.identity.principalId
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
    description: 'Dispara quando qualquer um dos 2 serviços (API ou funções) registra uma exceção no Application Insights — cobre falha de execução de função e erros da API.'
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
output functionAppName string = functionApp.name
output acrLoginServer string = acr.properties.loginServer
output postgresServerName string = postgres.name
output storageAccountName string = storageAccount.name
output keyVaultName string = keyVault.name
