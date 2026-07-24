---
name: trabalhar-em-edu-feedback
description: Implementar funcionalidades, corrigir bugs e evoluir módulos do projeto EduFeedback. Usar quando receber qualquer demanda de código, nova feature, correção ou refatoração neste repositório.
---

# Trabalhar em EduFeedback

## Objetivo

Implementar demandas de código com escopo controlado, aprovação prévia e preservação da arquitetura definida.

---

## Carregar contexto antes de agir

0. Checar a branch atual (`git branch --show-current`). Se existir uma branch `develop` e o HEAD não estiver nela, avisar o usuário e pedir confirmação para trocar antes de continuar — os documentos abaixo (`AGENTS.md`, `docs/`, `skills/`) só existem em `develop`; a branch `main` é a versão pública, sem eles.
1. Ler `../../AGENTS.md`.
2. Ler `../../docs/PROJECT_VISION.md` e `../../docs/ARCHITECTURE.md`.
3. Ler `../../docs/DECISIONS.md` (em especial ADR-005 — redução do Serviço B a 2 funções — e ADR-006 — Quarkus via gatilho nativo fino + endpoint interno).
4. Para nova feature ou módulo, ler `../../docs/NEW_MODULE_GUIDE.md`.
5. Consultar `../../docs/MODULES.md` para evitar duplicidade.
6. Se a demanda envolver deploy ou infraestrutura, ler também `../../docs/AZURE-DEPLOY.md` e `../../docs/PRODUCTION-READINESS.md`.

---

## Portão de aprovação

Antes de criar ou alterar qualquer arquivo:

1. Resumir a demanda em uma frase.
2. Listar arquivos que serão criados ou alterados.
3. Listar o que ficará fora do escopo.
4. Aguardar aprovação explícita.

Se a conversa já contiver uma proposta aceita, executar somente o escopo aceito. Pedir nova aprovação quando uma descoberta exigir mudança material de escopo.

---

## Stack obrigatória

Dois serviços Java 21 / Maven:
- **Serviço A** (`backend/`) — Spring Boot 3.3.x, Spring Data JPA, Flyway, PostgreSQL 16, Spring Security + `jjwt` para JWT.
- **Serviço B** (`functions/`) — Quarkus 3.x + Azure Functions Java Worker puro combinados (ver ADR-006). Exatamente 2 funções, cada uma com duas partes:
  - **Gatilho nativo fino** (`azure-functions-java-library`, anotações `@FunctionName`, sem CDI): `RelatorioAgendadoTrigger` (Timer) e `FeedbackCriticoTrigger` (Queue). Só repassam a chamada via HTTP (`InternalHttpCaller`) para o endpoint interno.
  - **Endpoint interno Quarkus** (`/internal/...`, CDI, Panache): `RelatorioAgendadoResource`+`RelatorioService` e `FeedbackCriticoResource`+`EmailService`. Concentram toda a lógica de negócio. Protegidos por `InternalSecretValidator` (`X-Internal-Secret`).

Mensageria: Azure Storage Queue (`notificacoes-criticas`). E-mail: Azure Communication Services. Deploy: Serviço A em Azure Container Apps, Serviço B em Azure Functions.

Não adicionar uma 3ª função, nem expor um endpoint `/internal/*` como API pública, nem reintroduzir a solicitação de relatório sob demanda como função serverless sem nova ADR aprovada — a redução a 2 funções foi decisão explícita do usuário (ADR-005), mantida na ADR-006, especificamente para manter responsabilidade única sem ambiguidade em cada componente.

Mudanças de stack exigem nova ADR aprovada em `../../docs/DECISIONS.md`.

---

## Convenções de código

- Organizar por módulo de domínio (`auth`, `avaliacao`, `relatorio` no Serviço A; `timer`, `notificacao`, `relatorio`, `avaliacao`, `admin` no Serviço B), nunca por camada técnica pura.
- Controller (Serviço A): recebe HTTP, valida entrada, delega ao Service. Nunca lógica de negócio no controller.
- Classe `@FunctionName` (Serviço B): recebe o gatilho, só repassa via HTTP para o endpoint interno — nunca lógica de negócio no gatilho nativo.
- Resource Quarkus `/internal/*` (Serviço B): sempre valida `InternalSecretValidator` antes de qualquer coisa, depois delega ao Service (CDI). Nunca lógica de negócio direto no resource.
- Migrations Flyway são exclusivas do Serviço A; o Serviço B nunca cria/altera schema (Panache com `database.generation=none`).
- Segredos somente em `.env`/variável de ambiente/Key Vault, nunca versionados.
- Testes proporcionais ao risco: MockMvc + Testcontainers para o Serviço A; `@QuarkusTest` + RestAssured + Dev Services (Postgres) para os endpoints internos do Serviço B; testes unitários (AssertJ) para lógica pura.
- Português do Brasil em documentação e textos de produto; nomes técnicos de código em inglês quando a convenção da stack favorecer.

---

## [EXTENSÍVEL] Regras específicas do projeto

> Adicione aqui regras que surgirem com o crescimento do projeto. Cada regra deve ter uma justificativa.

Nenhuma regra adicional além das descritas acima.

---

## [EXTENSÍVEL] Módulos com contexto especial

> Adicione uma subseção aqui para cada módulo que tiver gotchas, dependências não óbvias ou padrões específicos que o agente precisa conhecer antes de editar.

### Gatilhos nativos (RelatorioAgendadoTrigger, FeedbackCriticoTrigger)

Essas classes **não têm CDI** — não tente `@Inject` nelas. Elas só existem para disparar no trigger certo e chamar `InternalHttpCaller.post(...)` com o segredo interno. Toda lógica de negócio nova vai no endpoint Quarkus correspondente, nunca aqui.

### Endpoints internos (RelatorioAgendadoResource, FeedbackCriticoResource)

São Quarkus de verdade (CDI, Panache) — mas ficam expostos publicamente pelo `quarkus-azure-functions-http` do mesmo jeito que qualquer rota Quarkus. **Todo novo endpoint em `/internal/*` precisa injetar `InternalSecretValidator` e checar `@HeaderParam("X-Internal-Secret")` antes de qualquer lógica** — não existe outro mecanismo de proteção nessas rotas.

---

## Fluxo de implementação

1. **Entender** — ler o código existente do módulo antes de propor qualquer mudança.
2. **Propor** — apresentar o que será criado/alterado e aguardar aprovação.
3. **Implementar** — executar o escopo aprovado, uma mudança por vez.
4. **Verificar** — confirmar que testes passam e que módulos adjacentes não foram afetados.
5. **Registrar** — atualizar `docs/MODULES.md` e `docs/DECISIONS.md` se necessário.
