# Registro de decisões — EduFeedback

Estados permitidos: `proposta`, `aceita`, `substituída` ou `rejeitada`.

---

## ADR-001 — Stack principal

- Data: 2026-07-22
- Estado: aceita

### Contexto

O projeto precisará atender ao requisito obrigatório do Tech Challenge Fase 4: rodar em ambiente de nuvem e implementar no mínimo duas funções serverless com responsabilidade única, além de uma API para login e recebimento de feedback.

### Decisão

Arquitetura de dois serviços: Java 21 + Spring Boot 3 + Maven + Spring Data JPA + Flyway + PostgreSQL para a API principal (login, feedback, consulta de relatório); Java 21 + Maven para as 2 funções serverless (timer de relatório agendado, queue de notificação crítica), compartilhando o mesmo banco PostgreSQL. O Serviço B combina gatilhos nativos finos com Quarkus para a lógica de negócio — ver ADR-006 (substitui a ADR-005).

### Consequências

Mudanças de stack exigem nova ADR aprovada. Introduzir tecnologias fora desta lista sem aprovação viola a regra de trabalho definida em `AGENTS.md`.

---

## ADR-002 — Organização por módulos de domínio

- Data: 2026-07-22
- Estado: aceita

### Contexto

O projeto terá múltiplas funcionalidades que precisam crescer de forma independente.

### Decisão

Organizar cada serviço por módulos de domínio (`auth`, `avaliacao`, `relatorio`). Cada módulo tem sua própria pasta, rota e contratos. Código compartilhado surge apenas de necessidades reais, não por antecipação.

### Consequências

Adicionar um novo módulo não exige reestruturar os existentes. O acoplamento entre módulos é explicitamente proibido a não ser via interfaces formais.

---

## ADR-003 — Segredos fora do versionamento

- Data: 2026-07-22
- Estado: aceita

### Contexto

Credenciais e chaves de API precisam ser configuráveis por ambiente sem risco de exposição em repositórios públicos ou privados.

### Decisão

Todos os segredos (chaves de API, tokens, senhas) ficam exclusivamente em variáveis de ambiente ou em gerenciadores de segredos (Azure Key Vault em produção). O `.env.example` documenta os nomes sem os valores. O `.env` real está no `.gitignore`.

### Consequências

Nenhum segredo em código, logs ou arquivos versionados. Deployments precisam configurar variáveis de ambiente antes da execução.

---

## ADR-004 — Empacotamento híbrido do Serviço B (Quarkus + Azure Functions Java Worker)

- Data: 2026-07-22
- Estado: substituída (ver ADR-005)

### Contexto

Versão inicial do desenho tinha 4 funções: timer (relatório agendado), HTTP (solicitação sob demanda), queue (processamento do relatório) e queue (notificação de feedback crítico). A extensão oficial `quarkus-azure-functions-http` só suporta função com HTTP trigger, o que exigia misturar Quarkus CDI (na função HTTP) com Azure Functions Java Worker puro (nas demais) no mesmo módulo.

### Decisão (substituída)

Esta ADR foi substituída pela ADR-005 após a decisão de reduzir o Serviço B a exatamente 2 funções, eliminando a função HTTP e, com ela, toda a necessidade de Quarkus no módulo `functions/`.

---

## ADR-005 — Redução a 2 funções serverless, sem framework de aplicação no Serviço B

- Data: 2026-07-22
- Estado: aceita

### Contexto

O enunciado exige no mínimo 2 funções serverless com responsabilidade única, e pede como avaliação "a correta separação dos serviços e responsabilidades". O desenho anterior (ADR-004, 4 funções) tinha um ponto fraco de responsabilidade única: a função de processamento de relatório sob demanda misturava "gerar relatório" com "notificar que ficou pronto por e-mail". A decisão do usuário foi simplificar para as 2 funções que mapeiam direto nas duas automações que o próprio enunciado pede ("o envio de notificações e a geração de relatórios"), eliminando o fluxo de solicitação sob demanda por completo.

### Decisão

Serviço B passa a ter exatamente 2 funções, cada uma com um único trigger e uma única responsabilidade:

1. **`FeedbackCriticoFunction`** — Queue Trigger na fila `notificacoes-criticas`. Única responsabilidade: enviar e-mail de alerta ao(s) administrador(es) quando chega uma avaliação crítica (nota ≤ limite). Não calcula nada, não persiste nada.
2. **`RelatorioAgendadoFunction`** — Timer Trigger (periodicidade configurável via `RELATORIO_AGENDADO_CRON`). Única responsabilidade: calcular médias/contagens das avaliações e persistir um novo relatório. Não envia e-mail, não recebe requisição.

Como nenhuma das duas funções precisa de HTTP, CDI ou ORM reativo, o módulo `functions/` deixa de depender do Quarkus inteiramente — vira um projeto Azure Functions Java puro (`azure-functions-java-library` + `azure-functions-maven-plugin`), usando `JdbcRelatorioDao` (JDBC simples) para acessar o Postgres compartilhado com o Serviço A.

### Consequências

- A fila `solicitacoes-relatorio` e o endpoint de solicitação sob demanda deixam de existir — não há mais forma de o admin pedir um relatório "avulso"; ele só recebe relatórios pela geração agendada.
- `infra/azure/main.bicep` não provisiona mais essa fila.
- O módulo `functions/` fica mais simples de explicar na avaliação: 2 componentes, 2 responsabilidades, sem mistura de estilos de código.
- Se o admin precisar de relatórios sob demanda no futuro, a forma mais simples de reintroduzir isso é um endpoint comum (síncrono) no Serviço A — não como função serverless — para não reabrir a mistura de responsabilidades identificada aqui.

**Nota:** esta ADR foi parcialmente substituída pela ADR-006 — o Serviço B voltou a usar Quarkus, mas a decisão de ter exatamente 2 funções (timer + queue), cada uma com responsabilidade única, continua valendo. A ADR-006, por sua vez, foi parcialmente substituída pela ADR-007 (o gatilho fino deixou de ser Azure Functions e passou a ser Azure Container Apps Jobs).

---

## ADR-006 — Quarkus de volta no Serviço B, via gatilho nativo fino + endpoint interno

- Data: 2026-07-24
- Estado: aceita

### Contexto

Objetivo de aprendizado do usuário: praticar Quarkus (conteúdo visto no curso) nas funções serverless, com deploy real na Azure. A restrição técnica que motivou a ADR-005 continua verdadeira: a extensão oficial `quarkus-azure-functions-http` só sabe fazer **HTTP trigger** — não existe suporte oficial do Quarkus para Timer trigger ou Queue trigger com CDI. Não dá para simplesmente "voltar a usar Quarkus" nas 2 funções como elas eram antes (Timer/Queue nativos) sem violar essa limitação.

A alternativa de tirar essas 2 responsabilidades do Azure Functions e rodar como serviço Quarkus sempre ativo (Container App) foi descartada: isso deixaria de ser serverless, violando a regra obrigatória do enunciado ("Deve, obrigatoriamente, implementar serverless").

### Decisão

Cada uma das 2 funções passa a ter duas partes:

1. **Gatilho nativo fino** — classe `@FunctionName` do modelo padrão do Azure Functions Java Worker (sem CDI), com uma única responsabilidade: disparar no trigger certo (Timer ou Queue) e repassar a chamada, via HTTP, para um endpoint interno.
   - `RelatorioAgendadoTrigger` (Timer) → `POST /internal/relatorio-agendado`
   - `FeedbackCriticoTrigger` (Queue) → `POST /internal/feedback-critico`
2. **Endpoint interno Quarkus** — recurso RESTEasy Reactive (`@Path("/internal/...")`), exposto via `quarkus-azure-functions-http` (função HTTP única que já existia na versão original do projeto), com toda a lógica de negócio de verdade: CDI (`@ApplicationScoped`), Panache (`AvaliacaoEntity`, `AdminEntity`, `RelatorioEntity`), injeção de dependência.
   - `RelatorioAgendadoResource` → delega para `RelatorioService` (calcula agregados, persiste o relatório).
   - `FeedbackCriticoResource` → busca e-mails dos admins via Panache e envia via `EmailService`.

As rotas `/internal/*` ficam protegidas por `InternalSecretValidator`, injetado em cada resource e checado no início do método via `@HeaderParam("X-Internal-Secret")` (comparado com `INTERNAL_TRIGGER_SECRET`) — sem isso, qualquer cliente externo poderia chamá-las diretamente, já que o `quarkus-azure-functions-http` expõe todas as rotas Quarkus atrás do mesmo endpoint HTTP público do Function App. (Uma primeira tentativa usando um `ContainerRequestFilter` JAX-RS global não bloqueava as requisições de forma confiável nos testes — a checagem explícita por endpoint é mais simples e verificadamente funciona.)

A chamada HTTP do gatilho nativo para o endpoint interno usa o hostname do próprio Function App (`WEBSITE_HOSTNAME`, injetado automaticamente pela Azure; `localhost:7071` como padrão local via Core Tools) — é uma chamada de function-to-function dentro do mesmo app, padrão comum quando um Azure Function precisa acionar lógica exposta via HTTP trigger.

### Consequências

- O módulo `functions/` volta a depender do Quarkus (CDI, Panache, RESTEasy Reactive, `quarkus-azure-functions-http`) — igual à primeira versão do projeto, mas agora sem a função de solicitação sob demanda (que segue removida, ADR-005).
- Continuam sendo exatamente 2 funções serverless no sentido do enunciado (2 gatilhos Azure Functions distintos — Timer e Queue), cada uma com responsabilidade única. A diferença é que agora a maior parte do código de cada uma é Quarkus real, não JDBC bruto.
- Adiciona uma chamada HTTP interna por execução (latência extra pequena, aceitável para o volume deste projeto) e uma dependência nova: `INTERNAL_TRIGGER_SECRET`, gerenciado como qualquer outro segredo (Key Vault em produção).
- `JdbcRelatorioDao` e o `EmailSender` sem CDI foram removidos; a lógica equivalente vive em `RelatorioService`/`EmailService` (CDI) e nas entidades Panache.
- Explicar essa arquitetura (gatilho fino + endpoint interno protegido) é parte do que deve constar no vídeo de demonstração e na documentação da avaliação — é a peça mais não óbvia do projeto.

**Nota:** esta ADR foi parcialmente substituída pela ADR-007 — o gatilho fino deixou de ser Azure Functions (`@FunctionName`, `quarkus-azure-functions-http`) e passou a ser Azure Container Apps Jobs, mas a decisão central (Quarkus + endpoint interno `/internal/*` protegido por `INTERNAL_TRIGGER_SECRET`) continua valendo.

---

## ADR-007 — Gatilho fino via Azure Container Apps Jobs, sem Azure Functions

- Data: 2026-07-26
- Estado: aceita

### Contexto

O Serviço B (ADR-006) dependia do Azure Functions Java Worker só para o papel de "gatilho fino": disparar no Timer/Queue certo e repassar via HTTP para o endpoint interno Quarkus. Isso obrigava o módulo `functions/` a carregar duas peças de empacotamento diferentes no mesmo jar (`quarkus-maven-plugin` + `azure-functions-maven-plugin`) e a rodar em Azure Functions (Consumption, Linux, Java 21) — uma plataforma de deploy própria, separada do Container Apps Environment já usado pelo Serviço A.

Azure Container Apps Jobs oferece os mesmos 2 modelos de gatilho que o projeto precisa — `Schedule` (cron) e `Event` (KEDA, escalado pela profundidade da fila) — como um recurso "serverless por definição" do próprio Container Apps: cobrado por execução, com `minExecutions: 0` (não fica nada rodando entre disparos), sem servidor dedicado. Isso elimina a necessidade de uma segunda plataforma de deploy (Azure Functions) só para hospedar 2 gatilhos finos, mantendo o requisito obrigatório do enunciado ("implementar serverless").

### Decisão

O Serviço B passa a ter 3 componentes de infraestrutura, todos no mesmo Container Apps Environment já usado pelo Serviço A (`infra/azure/main.bicep`):

1. **`app-func-<ambiente>`** — Container App **sem ingress externo** (`ingress.external: false`), rodando o mesmo código Quarkus de antes (CDI, Panache, RESTEasy Reactive), agora como aplicação HTTP comum (sem `quarkus-azure-functions-http`). Só é alcançável de dentro do Container Apps Environment.
2. **`job-relatorio-agendado-<ambiente>`** — Container Apps Job com `triggerType: Schedule` (cron `'0 8 * * 1'`, toda segunda-feira 08:00 UTC). Única responsabilidade: `curl -X POST .../internal/relatorio-agendado` com o header `X-Internal-Secret`.
3. **`job-feedback-critico-<ambiente>`** — Container Apps Job com `triggerType: Event`, escalado por um scale rule KEDA `azure-queue` sobre a fila `notificacoes-criticas`. Única responsabilidade: consumir 1 mensagem da fila (`az storage message get`/`delete`, autenticado via connection string guardado no Key Vault) e repassar o corpo via `curl -X POST .../internal/feedback-critico`.

Os 2 Jobs rodam a imagem pública `mcr.microsoft.com/azure-cli` (já traz `curl`, `jq`, `az`) — não há Dockerfile próprio para eles, só o script inline no Bicep. As classes `@FunctionName` (`FeedbackCriticoTrigger`, `RelatorioAgendadoTrigger`) e a classe auxiliar `InternalHttpCaller` foram removidas do módulo `functions/`, junto com `host.json`, `local.settings.json.example` e as dependências/plugin do Azure Functions Java Worker (`azure-functions-java-library`, `azure-functions-maven-plugin`) — nada nesse módulo depende mais do runtime do Azure Functions.

O contrato dos 2 endpoints internos (`/internal/relatorio-agendado`, `/internal/feedback-critico`) não muda: continuam recebendo/validando `X-Internal-Secret` via `InternalSecretValidator` e delegando para `RelatorioService`/`EmailService` (CDI). O que muda é só quem os chama e como.

### Consequências

- `infra/azure/main.bicep` não provisiona mais Function App nem App Service Plan (`Microsoft.Web/sites`, `Microsoft.Web/serverfarms`). Um único Container Apps Environment (`cae-<ambiente>`) hospeda agora o Container App do Serviço A, o Container App interno do Serviço B e os 2 Jobs.
- `functions/Dockerfile` (novo) empacota o Quarkus do Serviço B como fast-jar padrão (mesmo formato multistage do `backend/Dockerfile`) — o Serviço B passa a ter imagem de container publicada no ACR, igual ao Serviço A.
- O scale rule KEDA `azure-queue` do Job de evento só aceita autenticação por secret referenciado (não por identidade gerenciada, nesta versão de API do Container Apps Jobs) — por isso existe um novo segredo `storage-connection-string` no Key Vault, usado só por esse Job (para o scale rule e para os comandos `az storage message`). Os demais segredos (`postgres-admin-password`, `jwt-secret`, `internal-trigger-secret`) continuam iguais.
- Cada Job tem sua própria identidade gerenciada (`SystemAssigned`) e só recebe a role `Key Vault Secrets User` — segue o princípio do menor privilégio já usado no resto do template.
- `.github/workflows/deploy-azure.yml`: o job `deploy-functions` deixa de rodar `azure-functions:deploy` e passa a fazer `az acr build` (a partir de `functions/Dockerfile`) + `az containerapp update` no Container App interno — mesmo padrão já usado pelo `deploy-backend`.
- Continuam sendo exatamente 2 componentes serverless com responsabilidade única no sentido do enunciado — a diferença é que agora são 2 Azure Container Apps Jobs (Schedule + Event) em vez de 2 Azure Functions (Timer + Queue). A ADR-006 (Quarkus + endpoint interno protegido) continua valendo sem alteração de lógica de negócio.
- Explicar essa migração (por que Container Apps Jobs em vez de Azure Functions, e como cada Job ainda é "gatilho fino, sem lógica de negócio") é parte do que deve constar no vídeo de demonstração — junto com a explicação do gatilho fino + endpoint interno da ADR-006.

---

## ADR-008 — Camadas de Clean Architecture dentro de cada módulo de domínio

- Data: 2026-07-27
- Estado: aceita

### Contexto

Pedido do usuário: refatorar os dois serviços para uma arquitetura mais limpa, com fronteira formal entre regra de negócio e framework. Até aqui, cada módulo (`auth`, `avaliacao`, `relatorio` no Serviço A; `notificacao`, `relatorio`, `avaliacao`, `admin` no Serviço B) era uma pasta única e "achatada": controller/resource, service, entidade JPA/Panache e repositório no mesmo pacote, sem inversão de dependência — a regra de negócio dependia diretamente de Spring Data, JPA, Panache ou do SDK do Azure.

A ADR-002 já definia organização por módulo de domínio, não por tipo técnico de arquivo. A decisão aqui precisava escolher entre reestruturar por camada no topo de cada serviço (contradizendo a ADR-002) ou aplicar camadas **dentro** de cada módulo (preservando-a). O usuário confirmou a segunda opção.

### Decisão

Cada módulo de domínio (nos dois serviços) passa a ter até três subpacotes:

1. **`domain/`** — entidade pura (POJO/record, sem anotação de framework) + enums/value objects + portas (interfaces) que a camada de aplicação precisa (repositório, publicador de fila, gerador de token, remetente de e-mail). Não importa Spring, JPA, Panache, Quarkus CDI ou SDK do Azure.
2. **`application/`** — caso de uso (`*UseCase`) que orquestra `domain/` através das portas. Só existe em módulos com regra de negócio própria; módulos só de leitura/apoio (`avaliacao` e `admin` no Serviço B) ficam só com `domain/` + `infrastructure/persistence/`.
3. **`infrastructure/`** — única camada que conhece framework: `web/` (controller/resource + DTOs de request/response), `persistence/` (entidade mapeada + repositório de framework + adapter que implementa a porta do domínio), e conforme o módulo, `messaging/`, `security/` ou `email/`.

Exceções pragmáticas registradas (para não inflar o projeto com abstração sem uso real, seguindo o espírito da ADR-002):

- `PasswordEncoder` (Spring Security) é usado diretamente na camada de aplicação do módulo `auth` do Serviço A — já é uma abstração estável do stack aprovado; embrulhar de novo em outra porta seria só cerimônia.
- `JwtTokenGerador` (Serviço A) implementa a porta de domínio `TokenGerador` (geração de token) e também expõe extração/validação de token, usadas só pelo `JwtAuthenticationFilter` — uma peça 100% de infraestrutura (filtro de segurança), por isso essas duas operações não viram porta de domínio.
- No Serviço B, entidades Panache continuam estendendo `PanacheEntityBase` (mapeamento), mas a camada de aplicação nunca as usa diretamente nem chama seus métodos estáticos de active record — todo acesso passa por uma classe `*PanacheRepository` (`implements PanacheRepository<Entidade>`, padrão repositório oficial do Panache) injetada num adapter que implementa a porta de domínio.
- `config/` e `shared/exception/` no Serviço A, e `shared/InternalSecretValidator` no Serviço B, continuam fora de qualquer módulo — são peças cross-cutting de infraestrutura sem regra de negócio, e a ADR-002 só permite compartilhar código com uso real em mais de um módulo.

Nenhum contrato de API muda (rotas, request/response, status HTTP, comportamento de autenticação/erros idênticos ao anterior).

### Consequências

- Mais classes por módulo (POJO de domínio + porta + entidade de framework + adapter, em vez de uma classe "tudo em um") em troca de regra de negócio testável sem subir Spring, Quarkus, JPA ou Panache — os testes unitários dos casos de uso mockam só as portas.
- `docs/ARCHITECTURE.md` foi atualizado com as árvores de pastas por módulo refletindo `domain/application/infrastructure`.
- Renomeações relevantes: `AuthService` → `AutenticarAdminUseCase`, `AvaliacaoService` → `RegistrarAvaliacaoUseCase`, `RelatorioService` (Serviço B) → `GerarRelatorioAgendadoUseCase`, `JwtService` → `JwtTokenGerador`, `NotificacaoCriticaPublisher` (implementação) → `AzureQueueNotificacaoCriticaPublisher`, `EmailService` → `AzureEmailSender`. As entidades JPA/Panache ganharam sufixo `JpaEntity`/`PanacheEntity` para diferenciar do POJO de domínio de mesmo conceito (ex.: `Avaliacao` domínio vs. `AvaliacaoJpaEntity` infraestrutura).
- `Agregados`/`AvaliacaoResumo` (Serviço B) migraram de `shared/` para `relatorio/domain/`, já que só são usados por esse módulo — `AgregadosJsonSerializer` migrou para `relatorio/infrastructure/persistence/`, já que serializar para JSON é detalhe de persistência (coluna `conteudo` jsonb), não regra de negócio.
