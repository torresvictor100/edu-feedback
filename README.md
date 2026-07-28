# EduFeedback

Repositório da **Atividade 4 (Tech Challenge — Fase 4)** da minha pós-graduação, cujo foco é Cloud Computing, Serverless e Deploy de Aplicações em ambiente de nuvem.

Plataforma para estudantes avaliarem aulas e administradores acompanharem a satisfação, com notificação automática de feedbacks críticos e relatórios periódicos de avaliações.

## Visão geral

O projeto é dividido em dois serviços, que compartilham o mesmo banco PostgreSQL:

- **`backend/`** — API principal: login de administrador (JWT), recebimento de feedback (`POST /avaliação`) e consulta de relatório (`GET /relatorios/{id}`). Roda como Container App público na Azure.
- **`functions/`** — os 2 componentes serverless exigidos pelo desafio: geração agendada de relatório semanal e notificação de feedback crítico por e-mail. Cada um combina um gatilho fino (Azure Container Apps Job — Schedule/Event) com um endpoint interno protegido por segredo compartilhado.

## Stack

- **Java 21**, Maven
- **Spring Boot 3** (Serviço A — API principal)
- **Quarkus 3** (Serviço B — lógica dos componentes serverless)
- **PostgreSQL 16** (banco compartilhado)
- **Azure Container Apps** + **Azure Container Apps Jobs** (serverless)
- **Azure Storage Queue** (desacoplamento entre avaliação crítica e notificação)
- **Azure Communication Services** (envio de e-mail)
- **Azure Key Vault** + Managed Identity (segredos e governança de acesso)
- **Application Insights** + **Azure Monitor** (observabilidade e alertas)
- **GitHub Actions** com login OIDC (deploy automatizado, sem credenciais estáticas)
- **Docker** (build multistage, usuário não-root)

## Documentação completa

A descrição completa do projeto — arquitetura da solução, instruções de deploy, configuração do monitoramento e documentação das funções serverless criadas — está em [`RELATORIO-PROJETO.md`](RELATORIO-PROJETO.md).

Repositório: https://github.com/torresvictor100/edu-feedback
