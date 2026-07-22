# Prontidão para produção — EduFeedback

Atualizado em 2026-07-22.

## Escopo implementado

- **Serviço A (Spring Boot)** — `backend/`
  - Autenticação admin (`POST /auth/login`, JWT HS256), com admin seed via migration Flyway.
  - Recebimento de feedback (`POST /avaliação`), classificando urgência (nota ≤ 3 = CRITICA) e publicando na fila `notificacoes-criticas` (com descrição, urgência e data de envio) quando crítico.
  - Consulta de relatório (`GET /relatorios/{id}`), protegida por JWT.
  - Migrations Flyway: `admins`, `avaliacoes`, `relatorios`.
- **Serviço B (Azure Functions Java Worker puro, sem framework de aplicação)** — `functions/`
  - Função Timer `RelatorioAgendado` — única responsabilidade: gerar relatório periódico com médias, contagens por dia/urgência e a lista de avaliações (descrição, urgência, data de envio), e persistir.
  - Função Queue `FeedbackCritico` — única responsabilidade: processar a fila `notificacoes-criticas` e enviar e-mail aos admins com descrição, urgência e data de envio.
  - Exatamente 2 funções (reduzido de um desenho anterior com 4 — ver ADR-005 em `docs/DECISIONS.md`), sem nenhum endpoint HTTP nem dependência de Quarkus/Spring nesse módulo.
- **Infraestrutura (`infra/azure/main.bicep`)**
  - Governança de acesso real: Managed Identity do Container App e do Function App com roles RBAC concedidas via Bicep (`AcrPull`, `Key Vault Secrets User`, `Storage Queue Data Contributor`) — não apenas identidade criada sem permissão.
  - Segredos (`postgres-admin-password`, `jwt-secret`) armazenados no Key Vault e referenciados nativamente pelos dois serviços, nunca em texto puro nas variáveis de ambiente.
  - Monitoramento com 2 Metric Alerts reais (exceções no Application Insights, CPU do Postgres) + Action Group de e-mail.

## Evidências locais

Comandos efetivamente executados nesta máquina de desenvolvimento:

```bash
cd backend && ./mvnw -q test
# Resultado: BUILD SUCCESS — 13 testes (unitários + integração com Testcontainers/Postgres real)

cd functions && mvn -q clean test
# Resultado: BUILD SUCCESS — testes cobrem AgregadosJsonSerializer (unitário puro) e
# JdbcRelatorioDao (integração com Postgres real via Testcontainers, schema mínimo
# criado no próprio teste)
```

Ambiente usado para validar: Java 21 (Temurin/OpenJDK), Maven 3.8.7, Docker 29.6.1. O ambiente sandbox onde este projeto foi gerado tinha um Docker Engine muito recente (API 1.55); foi necessário fixar a versão do Testcontainers em 1.21.4 (nos dois módulos) para a negociação de versão da API funcionar — deixe essa nota caso o mesmo sintoma ("client version X.XX is too old") apareça em outro ambiente.

## Checklist de produção

- [x] Build local (`mvnw`/`mvn`) dos dois serviços sem erros.
- [x] Testes unitários e de integração passando localmente.
- [x] Migrations Flyway validadas contra banco limpo (Testcontainers recria o schema do zero em cada execução de teste).
- [x] Dockerfile do Serviço A com build multistage e usuário não-root.
- [x] `.env.example` sem valores sensíveis; `.env` no `.gitignore`.
- [x] Coleção Postman validada como JSON (`python3 -m json.tool`).
- [ ] Build/execução do container do Serviço A via `docker compose up` (não executado neste ambiente — pendente de validação em máquina com acesso de rede ao Docker Hub/Maven Central sem restrições de sandbox).
- [ ] Deploy real em Azure (Container Apps + Functions) — depende de assinatura, permissões e recursos reais (ver `docs/AZURE-DEPLOY.md`).
- [ ] Validação end-to-end das 2 funções serverless contra Azure Storage Queue e Azure Communication Services reais (dentro do host real do Azure Functions, não só a lógica isolada localmente).
- [ ] Revisão de custo dos recursos provisionados por `infra/azure/main.bicep` (agora inclui Action Group e 2 Metric Alerts, além dos recursos já previstos).
- [ ] Rotação do `JWT_SECRET` e da senha do admin seed antes de qualquer deploy real.
- [ ] Sintaxe do `infra/azure/main.bicep` nunca foi validada com `az bicep`/`az deployment group validate` de verdade — este ambiente sandbox não tem Azure CLI instalado. Revisar com `az deployment group validate` antes do primeiro `create` real (ação humana obrigatória, não só recomendada).
- [ ] Confirmar que o Container App e o Function App conseguem resolver as referências do Key Vault logo no primeiro start (pode exigir reiniciar a revision/o Function App se a role RBAC ainda não tiver se propagado — ver nota de bootstrapping em `main.bicep` e em `docs/AZURE-DEPLOY.md`).

## CI/CD

- `.github/workflows/ci.yml`: builda e testa os dois serviços (`backend` e `functions`) e builda a imagem Docker do Serviço A em cada push/PR para `main`/`develop`.
- `.github/workflows/deploy-azure.yml`: dispara manualmente ou em push para `main`; faz login via OIDC (sem credencial de longa duração), builda e publica a imagem no ACR, atualiza o Container App, e builda/faz deploy do pacote de funções. Depende de secrets do GitHub (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_ACR_NAME`, `AZURE_RESOURCE_GROUP`) e de um `environment` chamado `production` — nenhum desses foi configurado neste momento (ação humana, ver `docs/AZURE-DEPLOY.md`).

## Lacunas conhecidas

- O admin seed (`admin@edufeedback.local` / `admin123`) existe apenas para desenvolvimento local — trocar a senha (ou remover o seed) antes de qualquer deploy real é dependência humana.
- Não há endpoint de cadastro de novo admin nesta primeira versão — novos admins entram via migration adicional.
- As 2 funções do Serviço B nunca foram testadas dentro do host real do Azure Functions (Core Tools/Azure) — só a lógica de negócio isolada (`JdbcRelatorioDao`, `AgregadosJsonSerializer`) foi validada localmente com Testcontainers.
- Não há mais forma de o admin solicitar um relatório sob demanda — essa função foi removida (ADR-005) para manter o Serviço B com responsabilidade única e sem ambiguidade em cada componente. Se for reintroduzida, deve ser um endpoint comum (síncrono) do Serviço A, não uma nova função serverless que misture geração e notificação.
- Nenhum recurso Azure foi criado; `infra/azure/main.bicep` não foi executado (`az deployment group create` não foi rodado).
- Sem teste de carga ou de resiliência da fila além do comportamento padrão do Azure Storage Queue.
