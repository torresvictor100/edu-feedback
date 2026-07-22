# Prontidão para produção — EduFeedback

Atualizado em 2026-07-22.

## Escopo implementado

- **Serviço A (Spring Boot)** — `backend/`
  - Autenticação admin (`POST /auth/login`, JWT HS256), com admin seed via migration Flyway.
  - Recebimento de feedback (`POST /avaliação`), classificando urgência (nota ≤ 3 = CRITICA) e publicando na fila `notificacoes-criticas` quando crítico.
  - Consulta de relatório (`GET /relatorios/{id}`), protegida por JWT.
  - Migrations Flyway: `admins`, `avaliacoes`, `relatorios`.
- **Serviço B (Quarkus + Azure Functions Java Worker)** — `functions/`
  - Função HTTP `POST /relatorios/solicitacoes` (Quarkus real, RESTEasy Reactive + Panache), valida JWT com papel ADMIN, cria o registro do relatório e enfileira o processamento.
  - Função Timer `RelatorioAgendado` — gera relatório periódico com médias e contagens.
  - Função Queue `ProcessarRelatorio` — processa a fila `solicitacoes-relatorio`, gera o relatório e avisa os admins por e-mail.
  - Função Queue `FeedbackCritico` — processa a fila `notificacoes-criticas` e envia e-mail aos admins.

## Evidências locais

Comandos efetivamente executados nesta máquina de desenvolvimento:

```bash
cd backend && ./mvnw -q test
# Resultado: BUILD SUCCESS — 13 testes (unitários + integração com Testcontainers/Postgres real)

cd functions && mvn -q test
# Resultado: ver docs/PRODUCTION-READINESS.md atualizado após a última execução local;
# testes cobrem AgregadosJsonSerializer (unitário puro) e SolicitarRelatorioResource
# (@QuarkusTest, Dev Services com Postgres via Testcontainers)
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
- [ ] Validação end-to-end das 4 funções serverless contra Azure Storage Queue e Azure Communication Services reais.
- [ ] Revisão de custo dos recursos provisionados por `infra/azure/main.bicep`.
- [ ] Rotação do `JWT_SECRET` e da senha do admin seed antes de qualquer deploy real.

## CI/CD

- `.github/workflows/ci.yml`: builda e testa os dois serviços (`backend` e `functions`) e builda a imagem Docker do Serviço A em cada push/PR para `main`/`develop`.
- `.github/workflows/deploy-azure.yml`: dispara manualmente ou em push para `main`; faz login via OIDC (sem credencial de longa duração), builda e publica a imagem no ACR, atualiza o Container App, e builda/faz deploy do pacote de funções. Depende de secrets do GitHub (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `AZURE_ACR_NAME`, `AZURE_RESOURCE_GROUP`) e de um `environment` chamado `production` — nenhum desses foi configurado neste momento (ação humana, ver `docs/AZURE-DEPLOY.md`).

## Lacunas conhecidas

- O admin seed (`admin@edufeedback.local` / `admin123`) existe apenas para desenvolvimento local — trocar a senha (ou remover o seed) antes de qualquer deploy real é dependência humana.
- Não há endpoint de cadastro de novo admin nesta primeira versão — novos admins entram via migration adicional.
- A extensão `quarkus-azure-functions-http` empacota só a função HTTP; as funções Timer/Queue usam o modelo padrão do Azure Functions Java Worker no mesmo módulo (ADR-004) — isso nunca foi testado dentro do host real do Azure Functions (Core Tools/Azure), só a lógica de negócio isolada foi validada localmente.
- Nenhum recurso Azure foi criado; `infra/azure/main.bicep` não foi executado (`az deployment group create` não foi rodado).
- Sem teste de carga ou de resiliência das filas além do comportamento padrão do Azure Storage Queue.
