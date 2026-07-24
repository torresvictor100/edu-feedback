# Instruções permanentes — EduFeedback

## Propósito

Plataforma para estudantes avaliarem aulas e administradores acompanharem satisfação, com alertas automáticos para feedbacks críticos e relatórios periódicos.

Estudantes enviam avaliações de aula (nota de 0 a 10 e descrição) através de um endpoint público, sem necessidade de conta. Administradores fazem login (JWT) e usam o sistema para acompanhar a satisfação dos alunos, recebendo notificação automática quando chega um feedback crítico e podendo consultar relatórios periódicos com médias e contagens. O projeto é desenvolvido sozinho por um único desenvolvedor, como Tech Challenge de Fase 4 (Cloud Computing, Serverless e Deploy).

## Leitura obrigatória

Antes de trabalhar neste repositório:

1. Leia `docs/TECH-SPEC.md` — **primeira leitura obrigatória**. Define todas as tecnologias aprovadas, justificativas e regras do projeto, incluindo a arquitetura de dois serviços (Serviço A Spring Boot / Serviço B Quarkus + gatilho nativo fino).
2. Leia `skills/trabalhar-em-edu-feedback/SKILL.md`.
3. Leia `docs/PROJECT_VISION.md`.
4. Leia `docs/ARCHITECTURE.md`.
5. Para um novo módulo, leia `docs/NEW_MODULE_GUIDE.md`.
6. Consulte `docs/DECISIONS.md` antes de tomar decisões estruturais.
7. Consulte `docs/MODULES.md` para verificar módulos existentes.

**Antes de qualquer deploy ou configuração de infraestrutura:**
- Leia `/home/joao/dev/skils/rubro-spec/ESPECIFICACAO-PRODUCAO.md` — checklist completo de segurança, monitoramento, testes e infraestrutura.

**Regra de escopo de tecnologia:** use apenas tecnologias listadas em `docs/TECH-SPEC.md`. Qualquer adição ou substituição exige atualizar esse documento com justificativa e obter aprovação explícita do usuário antes de implementar.

## Materiais de referência por tecnologia

Antes de implementar qualquer das tecnologias abaixo, leia o material de curso correspondente ao ID.
Os IDs abaixo referenciam a legenda completa (caminho de arquivo e fase) em `/home/joao/dev/skils/rubro-spec/KNOWLEDGE-BASE.md`.

| Estou prestes a... | ID |
|---|---|
| **Deploy no Azure** (Container Apps, Functions) | AZURE |
| **Configurar Azure Container Registry, imagens no Azure** | AZURE |
| **Configurar Azure Application Insights, alertas, logs** | AZURE |
| **Criar ou alterar uma das 2 Azure Functions (serverless, event-driven)** | SERVERLESS |
| **Decidir modelo de cloud (IaaS/PaaS/SaaS/FaaS)** | CLOUD |
| **Segurança no Azure (IAM, RBAC, Key Vault, Managed Identity)** | CLOUD |
| Criar Dockerfile, configurar imagem, docker-compose.yml | DOCKER |
| Criar API REST com Spring MVC, Controller, ResponseEntity | REST |
| Criar resource, CDI ou entidade Panache no Serviço B | QUARKUS |
| Criar entidades JPA, Repository, migrations Postgres | JPA-NOSQL |
| Modelar banco de dados, criar schema, índices | MODELAGEM-BD |
| Configurar Spring Security, JWT | SEGURANCA |
| Escrever testes JUnit, Mockito, Testcontainers | TESTES |

**Regras de infraestrutura:**
- Toda decisão de infraestrutura usa Microsoft Azure como plataforma (referência primária: ID `AZURE`).
- Serviço A (Spring Boot) é deployado em Azure Container Apps; Serviço B (Quarkus + gatilho nativo fino) em Azure Functions. Nunca inverter essa divisão sem nova ADR aprovada.
- Serviço B tem exatamente 2 funções (timer + queue), cada uma com responsabilidade única — ver ADR-005 e ADR-006 em `docs/DECISIONS.md`. Cada função = gatilho nativo fino (`@FunctionName`, sem CDI) + endpoint interno Quarkus (`/internal/...`, CDI/Panache) que faz o trabalho de verdade. Nunca expor um endpoint interno como API pública, nunca reintroduzir a solicitação de relatório sob demanda como função serverless sem nova ADR aprovada.
- Endpoints `/internal/*` do Serviço B sempre validam `X-Internal-Secret` via `InternalSecretValidator` antes de qualquer lógica de negócio — são alcançáveis publicamente pelo proxy HTTP do `quarkus-azure-functions-http`, mesmo não sendo pensados para isso.
- Migrations Flyway são de propriedade exclusiva do Serviço A. O Serviço B nunca cria ou altera schema (`quarkus.hibernate-orm.database.generation=none` em dev/prod).
- Segredos (JWT_SECRET, INTERNAL_TRIGGER_SECRET, connection strings) só em Key Vault/variável de ambiente, nunca versionados. `JWT_SECRET` é usado somente pelo Serviço A — o Serviço B não valida JWT, usa `INTERNAL_TRIGGER_SECRET` para proteger seus próprios endpoints internos.

**Regra geral:** não implemente de memória. Consulte o material correspondente à tecnologia aprovada e aplique somente convenções compatíveis com a stack registrada em `docs/TECH-SPEC.md`.

## Regra de aprovação antes da execução

Antes de criar ou alterar código, estrutura ou módulo:

1. Explique objetivamente o que entendeu da demanda.
2. Mostre o que pretende criar ou alterar.
3. Liste componentes, rotas, riscos e dependências conhecidos.
4. Delimite o que ficará fora da demanda.
5. Aguarde aprovação explícita do usuário.

Uma autorização como "crie", "pode fazer" ou equivalente aprova apenas o escopo apresentado imediatamente antes. Não amplie esse escopo durante a execução.

## Regras de trabalho

- Execute uma ideia ou demanda por vez.
- Preserve a stack registrada em `docs/DECISIONS.md`; mudanças de tecnologia exigem nova decisão aprovada.
- Organize o código por módulo de domínio (`auth`, `avaliacao`, `relatorio`), não apenas por tipo técnico de arquivo.
- Reutilize componentes compartilhados somente quando houver uso real em mais de um módulo.
- Preserve independência entre módulos. Um módulo não deve acessar diretamente detalhes internos de outro.
- Configure URLs e parâmetros de APIs no módulo responsável. Segredos somente em `.env` ou gerenciador de segredos.
- Registre todo módulo aprovado em `docs/MODULES.md`.
- Use português do Brasil em textos do produto e documentação, salvo exigência externa.
- Mantenha nomes técnicos de código em inglês quando a tecnologia adotada favorecer essa convenção.
- Nenhum kit de identidade visual aplicado neste projeto (sem frontend).

- ADMIN é o único perfil autenticado; toda rota de consulta de relatório no Serviço A exige JWT válido com esse papel. ESTUDANTE não tem conta — o envio de feedback é público, sem diferenciação artificial de permissão além do contrato do enunciado.

## Requisitos mínimos de um módulo

Um módulo só está completo quando possuir:

- propósito e público definidos;
- rota ou contrato de API único e estável;
- lógica de negócio documentada;
- estados de carregamento, ausência de dados e erro tratados;
- testes proporcionais ao risco;
- registro atualizado em `docs/MODULES.md`.

Nenhuma regra adicional definida na criação.

## Estado atual

Projeto criado em 2026-07-22, com o Serviço B revisado em 2026-07-24 para usar Quarkus (ADR-006). Escopo implementado: autenticação admin, recebimento de feedback, 2 funções serverless (timer de relatório agendado, queue de notificação crítica — cada uma com gatilho nativo fino + endpoint interno Quarkus), consulta de relatório. Ver `docs/PRODUCTION-READINESS.md` para o estado exato de build/testes e pendências humanas de deploy.
