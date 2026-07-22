# Guia para criar um módulo — EduFeedback

## Regra principal

Não iniciar implementação antes de apresentar a proposta do módulo e receber aprovação explícita. A aprovação vale somente para o escopo apresentado.

---

## Etapa 1 — Formular o propósito

Transformar a ideia em uma declaração verificável:

```
Ideia ampla:   painel de relatórios
Declaração:    um administrador autenticado pode ver a lista de relatórios
               já gerados e abrir o conteúdo de cada um
```

Registrar:
- propósito;
- público;
- o que o módulo entrega (e o que não entrega);
- rota ou endpoint principal;
- se o módulo vive no Serviço A (Spring Boot) ou no Serviço B (Quarkus/Functions) — e, se for uma nova função serverless, qual trigger usa (HTTP, Timer ou Queue) e por que ela tem responsabilidade única.

---

## Etapa 2 — Apresentar a proposta

Antes de alterar qualquer arquivo, apresentar:

```markdown
## Proposta do módulo

- Nome:
- ID (slug):
- Serviço: A (Spring Boot) ou B (Quarkus/Functions)
- Propósito em uma frase:
- Categoria:
- Rotas / endpoints / trigger:
- Componentes ou classes previstas:
- Dependências de outros módulos:
- Comportamento esperado (happy path):
- Casos de erro tratados:
- O que ficará fora do escopo:
- Testes planejados:
```

Aguardar aprovação explícita antes de avançar.

---

## Etapa 3 — Implementar uma fatia vertical

Criar somente o necessário para entregar o comportamento aprovado:

**Serviço A (Spring Boot):** Controller (contrato HTTP + validação Bean Validation) → Service (regra de negócio) → Repository/Entity JPA (persistência) → migration Flyway quando houver nova tabela/coluna → tratamento de erro no `GlobalExceptionHandler` → testes (MockMvc para o controller, Testcontainers quando tocar o banco).

**Serviço B (Quarkus/Functions):**
- Se for função **HTTP**: Resource Quarkus (RESTEasy Reactive) + entidade Panache + testes com `@QuarkusTest`.
- Se for função **Timer** ou **Queue**: classe `@FunctionName` no modelo Azure Functions Java Worker, usando `shared/JdbcRelatorioDao` (ou um novo DAO JDBC simples, se o dado for diferente) + testes unitários da lógica de negócio isolada da função.

Não criar abstrações antecipadas. Se um padrão aparecer em dois módulos, criar o compartilhado só então.

---

## Etapa 4 — Registrar o módulo

Após implementação:

1. Atualizar `docs/MODULES.md` com estado `disponível`.
2. Atualizar `docs/DECISIONS.md` se alguma decisão estrutural foi tomada.
3. Garantir que o módulo tem testes proporcionais ao risco.

---

## O que faz um módulo ser independente

- Tem sua própria pasta no backend (`backend/` e/ou `functions/`).
- Não acessa diretamente o interior de outro módulo.
- Pode ser pausado ou removido sem quebrar outros módulos.
- Tem contratos de entrada e saída explícitos (API, schema, fila).
