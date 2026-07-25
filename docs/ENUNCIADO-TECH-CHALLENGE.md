# Enunciado do Tech Challenge — Fase 4

Este documento guarda o enunciado original do desafio (fonte primária, para consulta futura) e, logo abaixo, um resumo em texto livre de como cada parte foi atendida no projeto e o que ainda falta.

---

## 1. Texto original do enunciado

> Boas-vindas ao Tech Challenge da fase 4! Este desafio é fundamental para consolidar os conhecimentos obtidos ao longo da fase. Nosso desafio focará em Cloud Computing, Serverless e Deploy de Aplicações em ambiente de nuvem. O projeto proposto envolve a criação de uma plataforma de feedback, onde os estudantes podem avaliar as aulas e os administradores podem ter acesso a relatórios e análises desses feedbacks. Esta atividade será desenvolvida em grupo, com prazo de entrega determinado e com impacto direto na nota final.
>
> **Problema**
>
> Para garantir a qualidade dos cursos on-line, é essencial que os estudantes possam fornecer feedbacks e que os administradores possam acompanhar rapidamente a satisfação dos alunos. O sistema deverá ser capaz de receber feedbacks, enviar notificações para itens críticos e gerar relatórios periódicos para auxiliar na análise dos dados.
>
> **Objetivo**
>
> O objetivo é desenvolver uma aplicação hospedada em um ambiente de nuvem, com funções serverless para automatizar o recebimento de feedbacks, o envio de notificações e a geração de relatórios. Como estamos em um ambiente com créditos de cloud computing limitados, vocês deverão gravar um vídeo demonstrando o sistema em funcionamento.
>
> **Requisitos**
> - Ambiente de nuvem configurado e funcionando, com configurações de segurança relacionadas aos dados de clientes e com governança de acesso.
> - Configuração dos componentes de suporte (bancos de dados etc.).
> - Deploy automatizado dos componentes atualizáveis (ex.: funções).
> - Aplicação monitorada.
> - Notificações automáticas aos administradores para problemas críticos.
> - Relatório semanal dos feedbacks, com média de avaliações.
>
> **Regras para a aplicação**
> - Deve, obrigatoriamente, implementar serverless.
> - Deve, obrigatoriamente, rodar em ambiente cloud.
> - O mínimo de funções serverless a implementar são dois, mas é preciso observar a regra de Responsabilidade Única para cada um dos componentes. A correta separação dos serviços e responsabilidades é parte da avaliação.
>
> **Artefatos de entrega**
> - Repositório aberto com o código-fonte do projeto.
> - Vídeo de demonstração da aplicação em funcionamento, as funções serverless ativas e as configurações do projeto.
>
> **Avaliação**
>
> A avaliação será baseada nos seguintes critérios:
> - Explicação do modelo de cloud escolhido e dos componentes envolvidos na solução.
> - Funcionamento correto da aplicação.
> - Qualidade do código, com documentação.
> - Descrição do projeto com: arquitetura da solução; instruções de deploy; configuração do monitoramento; documentação das funções criadas.
> - Configuração do ambiente de nuvem e funções serverless, com explicações sobre o modelo escolhido e configurações de segurança.
>
> **Referências**
>
> Endpoints de entrada:
> ```
> POST /avaliação
> {
>   "descricao": string,
>   "nota": int (0 a 10),
> }
> ```
>
> Dados para o e-mail de aviso de urgência:
> - Descrição;
> - Urgência;
> - Data de envio;
>
> Dados para o relatório semanal:
> - Descrição;
> - Urgência;
> - Data de envio;
> - Quantidade de avaliações por dia;
> - Quantidade de avaliações por urgência.
>
> Lembramos que as datas de todas as lives, grupos de estudos e entrega do Tech Challenge estão na plataforma. Bom trabalho.

---

## 2. Como o projeto atende a isso (resumo em texto livre)

O EduFeedback ficou desenhado como dois serviços que dividem o mesmo banco PostgreSQL:

- **Serviço A (Spring Boot)** é a API principal: login de admin via JWT, o endpoint público `POST /avaliação` que recebe nota e descrição do estudante, e a consulta de relatório (`GET /relatorios/{id}`).
- **Serviço B (Azure Functions + Quarkus)** cobre as duas automações serverless que o enunciado pede. Cada função tem duas camadas: um gatilho nativo bem fino (Timer ou Queue, sem CDI, só repassando a chamada) e um endpoint interno Quarkus protegido por um segredo compartilhado, que concentra a lógica de negócio de verdade (Panache, injeção de dependência). Essa divisão existe porque a extensão oficial do Quarkus para Azure Functions só sabe lidar com gatilho HTTP — não há suporte oficial dele para Timer/Queue trigger. O caminho encontrado foi manter o Quarkus (era um objetivo de aprendizado explícito) sem abrir mão do serverless de verdade nem quebrar a regra de responsabilidade única. Essa decisão está registrada com todo o histórico em `docs/DECISIONS.md` (ADR-005 e ADR-006).

As duas funções são exatamente as que o enunciado pede, uma para cada automação:

1. **Notificação de feedback crítico** — disparada por uma fila (Queue Trigger) quando chega uma avaliação com nota abaixo do limite crítico. Responsabilidade única: buscar os e-mails dos admins e enviar o alerta.
2. **Relatório agendado** — disparada por um Timer com periodicidade semanal configurável. Responsabilidade única: calcular médias e contagens das avaliações e persistir um novo relatório.

Segurança e governança de acesso foram desenhadas via Managed Identity + roles RBAC do Azure (sem credenciais soltas) e segredos guardados no Key Vault, tudo declarado como código em `infra/azure/main.bicep` (Container App, Function App, PostgreSQL Flexible Server, Storage Queue, Application Insights, Key Vault, 2 alertas de monitoramento + Action Group). O pipeline de deploy automatizado já existe em `.github/workflows/deploy-azure.yml`.

**O que já está pronto e validado localmente:** as duas funções serverless (testadas com Testcontainers), a API do Serviço A, a documentação de arquitetura e decisões (`ARCHITECTURE.md`, `TECH-SPEC.md`, `DECISIONS.md`), e toda a infraestrutura como código.

**O que ainda falta (depende de ação humana, fora do que um agente de IA consegue fazer):**
- Provisionar a infraestrutura de verdade na Azure (o Bicep nunca foi rodado contra uma assinatura real).
- Criar o repositório no GitHub e dar push (o enunciado pede repositório aberto).
- Configurar a federação OIDC entre GitHub Actions e Azure e rodar o deploy real.
- Gravar o vídeo de demonstração com o sistema rodando de verdade na nuvem.

O passo a passo detalhado dessas pendências, na ordem certa, está em `docs/CHECKLIST-ENTREGA.md` — este documento aqui é só o enunciado original + o resumo de como ele foi endereçado; o checklist é o rastreador vivo do que falta fazer.
