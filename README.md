# EduFeedback

Plataforma para estudantes avaliarem aulas e administradores acompanharem a satisfação, com notificação automática de feedbacks críticos e relatórios periódicos de avaliações. Projeto desenvolvido como Tech Challenge de Fase 4 (Cloud Computing, Serverless e Deploy).

## Arquitetura em duas partes

- **`backend/`** — API principal (Java 21 + Spring Boot 3): login de administrador (JWT), recebimento de feedback (`POST /avaliação`) e consulta de relatório (`GET /relatorios/{id}`). Deploy: Azure Container Apps.
- **`functions/`** — 2 funções serverless (Java 21 + Quarkus 3): geração agendada de relatório (timer) e notificação de feedback crítico (queue). Cada função combina um gatilho nativo fino (Timer/Queue) com um endpoint Quarkus interno, protegido por segredo compartilhado, que concentra a lógica de negócio (CDI, Panache). Deploy: Azure Functions.

Os dois serviços compartilham o mesmo banco PostgreSQL. Detalhes completos da arquitetura em [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (specs completas só existem na branch `develop`).

## Rodando localmente

Pré-requisitos: Docker, Docker Compose, Java 21, Maven.

```bash
cp .env.example .env
# edite .env com valores locais (a connection string de Storage/ACS pode ficar vazia em dev)

docker compose up -d db
cd backend && ./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. Endpoint de feedback:

```bash
curl -X POST http://localhost:8080/avaliação \
  -H "Content-Type: application/json" \
  -d '{"descricao": "Aula muito boa", "nota": 9}'
```

Para rodar as funções localmente (Azure Functions Core Tools instalado):

```bash
cd functions && mvn clean package azure-functions:run
```

## Testes

```bash
cd backend && ./mvnw test
cd functions && mvn test
```

## Coleção Postman

Importe `postman/edu-feedback.postman_collection.json` e `postman/local.postman_environment.json` no Postman para testar os fluxos manualmente (health check, login, envio de feedback e consulta de relatório). O Serviço B não tem endpoint HTTP **público** — os endpoints Quarkus em `/internal/*` só existem para os gatilhos nativos (Timer/Queue) chamarem entre si, protegidos por segredo compartilhado; acompanhe o funcionamento das 2 funções pelos logs.

## Documentação

Este README fica público. A documentação completa do projeto (especificação técnica, arquitetura, decisões, guia de novos módulos) vive em `docs/` e `AGENTS.md`, disponíveis apenas na branch `develop` deste repositório.
