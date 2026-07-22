# Visão do projeto — EduFeedback

## Objetivo

O EduFeedback é uma plataforma para coletar e analisar o feedback de estudantes sobre as aulas de um curso on-line. O objetivo central é dar aos administradores visibilidade rápida sobre a satisfação dos alunos, sem depender de análise manual.

O sistema roda hospedado em nuvem (Microsoft Azure), usando funções serverless para automatizar três tarefas: identificar feedbacks críticos e notificar os administradores, gerar relatórios periódicos de satisfação e processar sob demanda relatórios solicitados pelo administrador.

## Problema que resolve

Hoje, para garantir a qualidade de um curso on-line, alguém precisaria olhar manualmente cada avaliação recebida para perceber que um aluno está insatisfeito, e compilar à mão médias e contagens para entender a tendência geral. Isso não escala e atrasa a resposta a problemas críticos. O EduFeedback resolve isso automatizando a captura do feedback, o alerta imediato de casos críticos e a geração de relatórios agregados — o administrador só precisa agir quando avisado, e consultar o relatório quando quiser.

## Público-alvo

Estudantes (avaliam aulas) e administradores/coordenação (analisam relatórios e recebem alertas).

## Princípios do produto

### Uma responsabilidade por módulo

Cada módulo resolve um problema específico e possui fronteiras claras. O crescimento do projeto não deve transformar todos os domínios em uma única área acoplada. Isso vale com ainda mais força para as funções serverless: cada uma resolve exatamente um problema (notificar, agendar relatório, receber solicitação, processar solicitação).

### Aprovação antes da implementação

Nenhuma feature é implementada sem uma proposta revisada e aprovada. Isso evita retrabalho e mantém o escopo controlado.

### Evolução incremental

O primeiro módulo não precisa prever todas as possibilidades futuras. Abstrações compartilhadas surgem de necessidades reais e repetidas — nunca por antecipação.

### Segredos fora do código

Credenciais, tokens e chaves de API vivem apenas em variáveis de ambiente. Nunca em arquivos versionados.

Nenhum princípio adicional definido na criação.

## Roles de usuário

- **ESTUDANTE**: não tem conta no sistema. Envia avaliações de aula via `POST /avaliação`, endpoint público, sem autenticação — exatamente como o contrato do desafio define.
- **ADMIN**: possui conta e faz login via JWT. Consulta relatórios prontos e recebe notificação por e-mail quando chega um feedback crítico.

## Módulos planejados para o lançamento

1. **Autenticação admin** — login com e-mail/senha, emissão de JWT.
2. **Recebimento de feedback** — `POST /avaliação`, valida e persiste nota (0–10) e descrição.
3. **Notificação de feedback crítico** — função serverless (Queue Trigger) disparada quando a nota é ≤ 3, envia e-mail ao(s) administrador(es).
4. **Geração de relatório agendado** — função serverless (Timer Trigger) com periodicidade configurável, gera relatório com médias e contagens.
5. **Consulta de relatório** — `GET /relatorios/{id}`, retorna o relatório já processado.

Serviço B tem exatamente essas 2 funções serverless (3 e 4 acima) — decisão registrada na ADR-005 em `DECISIONS.md`, reduzindo de um desenho anterior com 4 funções para manter responsabilidade única sem ambiguidade.

## Fora do escopo inicial

- Frontend/painel visual (o desafio não exige interface, só contratos de API).
- Login social/OAuth externo.
- IA integrada.
- Mensageria com Kafka/RabbitMQ (usando Azure Storage Queue, suficiente para este volume).
- Bancos NoSQL (MongoDB/Cassandra) — apenas PostgreSQL.
- Multi-tenant / múltiplos cursos — o desafio trata de um único curso/plataforma.
- Solicitação de relatório sob demanda (existiu numa versão anterior; removida para manter o Serviço B com só 2 funções de responsabilidade única — ver ADR-005).
