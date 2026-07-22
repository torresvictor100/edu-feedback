// Infraestrutura como código do EduFeedback.
// Provisiona: PostgreSQL Flexible Server, Storage Account (fila notificacoes-criticas),
// Azure Container Registry, Container Apps Environment + Container App (Serviço A),
// Function App Consumption (Serviço B: 2 funções — timer e queue), Application
// Insights + Log Analytics, Key Vault e Azure Communication Services (e-mail).
//
// Uso: az deployment group create --resource-group <rg> --template-file main.bicep
//      --parameters @main.parameters.example.json
//
// Nenhum valor real de credencial fica neste arquivo — segredos são gerados ou
// referenciados via Key Vault. Revise nomes, região e SKUs antes de executar.

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

@description('Segredo compartilhado para assinatura dos JWT emitidos pelo Serviço A e validados pelo Serviço B.')
@secure()
param jwtSecret string

var suffix = '${projectName}-${environmentName}'
var storageAccountName = toLower(replace('st${suffix}', '-', ''))
var acrName = toLower(replace('acr${suffix}', '-', ''))

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
        { name: 'postgres-password', value: postgresAdminPassword }
        { name: 'jwt-secret', value: jwtSecret }
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
      appSettings: [
        { name: 'FUNCTIONS_WORKER_RUNTIME', value: 'java' }
        { name: 'FUNCTIONS_EXTENSION_VERSION', value: '~4' }
        { name: 'AzureWebJobsStorage', value: 'DefaultEndpointsProtocol=https;AccountName=${storageAccount.name};EndpointSuffix=core.windows.net' }
        { name: 'AZURE_STORAGE_CONNECTION_STRING', value: 'DefaultEndpointsProtocol=https;AccountName=${storageAccount.name};EndpointSuffix=core.windows.net' }
        { name: 'POSTGRES_HOST', value: '${postgres.name}.postgres.database.azure.com' }
        { name: 'POSTGRES_PORT', value: '5432' }
        { name: 'POSTGRES_DB', value: 'edufeedback' }
        { name: 'POSTGRES_USER', value: postgresAdminLogin }
        { name: 'POSTGRES_PASSWORD', value: postgresAdminPassword }
        { name: 'RELATORIO_AGENDADO_CRON', value: '0 0 8 * * 1' }
        { name: 'APPINSIGHTS_INSTRUMENTATIONKEY', value: appInsights.properties.InstrumentationKey }
        { name: 'APPLICATIONINSIGHTS_CONNECTION_STRING', value: appInsights.properties.ConnectionString }
      ]
    }
  }
}

resource communicationServices 'Microsoft.Communication/communicationServices@2023-04-01' = {
  name: 'acs-${suffix}'
  location: 'global'
  properties: {
    dataLocation: 'Brazil'
  }
}

output containerAppFqdn string = containerApp.properties.configuration.ingress.fqdn
output functionAppName string = functionApp.name
output acrLoginServer string = acr.properties.loginServer
output postgresServerName string = postgres.name
output storageAccountName string = storageAccount.name
