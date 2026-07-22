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
3. Ler `../../docs/DECISIONS.md` (em especial ADR-005, sobre a redução do Serviço B a 2 funções sem framework de aplicação).
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
- **Serviço B** (`functions/`) — Azure Functions Java Worker puro (`azure-functions-java-library`, anotações `@FunctionName`), sem framework de aplicação. Exatamente 2 funções: `RelatorioAgendadoFunction` (Timer) e `FeedbackCriticoFunction` (Queue). Acesso ao Postgres via JDBC simples (`shared/JdbcRelatorioDao`).

Mensageria: Azure Storage Queue (`notificacoes-criticas`). E-mail: Azure Communication Services. Deploy: Serviço A em Azure Container Apps, Serviço B em Azure Functions.

Não adicionar uma 3ª função nem um endpoint HTTP no Serviço B sem nova ADR aprovada — a redução a 2 funções foi decisão explícita do usuário (ADR-005), especificamente para manter responsabilidade única sem ambiguidade em cada componente.

Mudanças de stack exigem nova ADR aprovada em `../../docs/DECISIONS.md`.

---

## Convenções de código

- Organizar por módulo de domínio (`auth`, `avaliacao`, `relatorio` no Serviço A; `timer`, `notificacao` no Serviço B), nunca por camada técnica pura.
- Controller (Serviço A): recebe HTTP, valida entrada, delega ao Service. Nunca lógica de negócio no controller.
- Classe `@FunctionName` (Serviço B): recebe o gatilho, delega a lógica para `shared/`. Nunca lógica de negócio direto na classe da função.
- Migrations Flyway são exclusivas do Serviço A; o Serviço B nunca cria/altera schema.
- Segredos somente em `.env`/variável de ambiente/Key Vault, nunca versionados.
- Testes proporcionais ao risco: MockMvc + Testcontainers para o Serviço A; testes unitários (AssertJ) para lógica pura e Testcontainers direto contra `JdbcRelatorioDao` para o que toca banco no Serviço B.
- Português do Brasil em documentação e textos de produto; nomes técnicos de código em inglês quando a convenção da stack favorecer.

---

## [EXTENSÍVEL] Regras específicas do projeto

> Adicione aqui regras que surgirem com o crescimento do projeto. Cada regra deve ter uma justificativa.

Nenhuma regra adicional além das descritas acima.

---

## [EXTENSÍVEL] Módulos com contexto especial

> Adicione uma subseção aqui para cada módulo que tiver gotchas, dependências não óbvias ou padrões específicos que o agente precisa conhecer antes de editar.

### Funções Timer e Queue (Serviço B)

Não há nenhum framework de aplicação nesse módulo (ver ADR-005) — nada de `@Inject`, CDI ou ORM. Use `shared/JdbcRelatorioDao` (ou crie um DAO JDBC simples equivalente) para acessar o Postgres, e `shared/EmailSender` para envio de e-mail.

---

## Fluxo de implementação

1. **Entender** — ler o código existente do módulo antes de propor qualquer mudança.
2. **Propor** — apresentar o que será criado/alterado e aguardar aprovação.
3. **Implementar** — executar o escopo aprovado, uma mudança por vez.
4. **Verificar** — confirmar que testes passam e que módulos adjacentes não foram afetados.
5. **Registrar** — atualizar `docs/MODULES.md` e `docs/DECISIONS.md` se necessário.
