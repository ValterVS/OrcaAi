# Decisões de arquitetura

Registro curto das decisões que moldam o sistema. Ao mudar uma delas, atualize este arquivo. Regras e operação de segurança: [security.md](security.md).

## 1. Monólito modular

Um único backend Spring Boot, um único banco PostgreSQL. Módulos são pacotes de primeiro nível em `com.orcaai`:

| Pacote          | Responsabilidade                                                       |
|-----------------|------------------------------------------------------------------------|
| `shared`        | Transversal: erros, segurança, tenancy, base de persistência           |
| `identity`      | Cadastro da empresa, login, sessão, conta atual, revogação de sessões  |
| `organizations` | A organização (tenant)                                                 |
| `users`         | Usuários de uma organização                                            |
| `customers`     | Clientes da organização (primeiro módulo de negócio)                   |
| `team`          | Equipe: convites, papéis, desativação e reativação de usuários         |

Módulos futuros (`leads`, `estimates`, `proposals`, `followups`, `projects`, `billing`, `notifications`, `audit`) entram como pacotes irmãos.

Regras:
- Um módulo referencia outro por ID (`UUID`), não por associação JPA. Isso mantém tabelas e módulos desacoplados.
- `shared` não depende de nenhum módulo de negócio.
- Classes internas de um módulo são package-private sempre que possível.
- Verticalizações (outros tipos de prestador) devem ser tratadas por configuração/dados dentro dos mesmos módulos, não por cópias de módulos.

## 2. Multi-tenancy

Banco e schema compartilhados, com coluna `organization_id` em toda tabela de negócio.

- A organização atual vem **somente** do principal autenticado na sessão (`TenantContext`). Nenhum endpoint aceita `organizationId` do cliente para decidir o escopo.
- Entidades de negócio estendem `TenantOwnedEntity`, que usa o `@TenantId` do Hibernate: `organization_id` é preenchido no insert, não tem setter, e consultas JPA (incluindo `findById` e update/delete em lote via JPQL) são filtradas.
- Sem organização autenticada, o resultado é **falha fechada**: nada é lido e inserts falham na FK.
- `TenantTransactionManager` publica a organização para o PostgreSQL em cada transação (`app.current_organization_id`, local à transação), base para Row Level Security.
- **SQL nativo não é filtrado pelo Hibernate:** consultas SQL nativas envolvendo dados multi-tenant exigem filtro explícito por organização e teste de isolamento.
- `users` não usa `@TenantId` (login antes do tenant). O isolamento fica em `UserRepository`, que não expõe acesso por ID sem organização.
- Recurso de outra organização responde 404 (não 403).
- **RLS:** ativo em `customers`, a primeira tabela de negócio, e obrigatório na mesma migration de cada tabela de negócio seguinte. Desenho e decisão em [security.md §7](security.md#7-postgresql-row-level-security).

## 3. Autenticação e autorização

- **Sessão no servidor**, não JWT. A sessão fica no PostgreSQL (Spring Session JDBC), então sobrevive a deploys, funciona com múltiplas instâncias e pode ser revogada.
- Cookie `SESSION`: `HttpOnly`, `Secure` (desligado só no profile `dev`), `SameSite=Lax`, timeout de 8h de inatividade.
- **CSRF**: double-submit. `GET /api/auth/csrf` emite o cookie `XSRF-TOKEN` (legível por JS); requisições que alteram estado enviam o valor no header `X-XSRF-TOKEN`. O token é rotacionado no login.
- Endpoints:

  | Endpoint | Autenticação | Resposta |
  |---|---|---|
  | `GET /api/auth/csrf` | pública | 204 + cookie `XSRF-TOKEN` |
  | `POST /api/auth/signup` (JSON `companyName`, `ownerName`, `email`, `password`) | pública, CSRF | 202 + mensagem genérica, exista ou não o e-mail; conta nova fica pendente de verificação |
  | `POST /api/auth/resend-verification` (JSON `email`) | pública, CSRF | 202 + mensagem genérica |
  | `POST /api/auth/verify-email` (JSON `token`) | pública, CSRF | 204; 422 com motivo (inválido, expirado, já usado) |
  | `POST /api/auth/forgot-password` (JSON `email`) | pública, CSRF | 202 + mensagem genérica |
  | `POST /api/auth/reset-password` (JSON `token`, `password`) | pública, CSRF | 204; revoga sessões, sem login automático |
  | `POST /api/auth/login` (form `email`, `password`) | pública, CSRF | 204 + cookie `SESSION`; token CSRF rotacionado |
  | `GET /api/auth/me` | sessão | `userId`, `userName`, `role`, `organizationId`, `organizationName` |
  | `POST /api/auth/logout` | CSRF | 204; sessão invalidada, cookies `SESSION` e `XSRF-TOKEN` expirados |

- **Bootstrap de CSRF:** antes de toda requisição que altera estado (cadastro, verificação, reenvio, recuperação, redefinição, login, logout), o frontend chama `GET /api/auth/csrf` (`src/lib/api/auth.ts`) e reenvia o cookie no header. Os tokens de e-mail não substituem o CSRF, que nunca é desativado.
- `/me` relê usuário e organização do banco a cada chamada; usuário inativo, não verificado ou removido recebe 401 mesmo com sessão ainda existente.
- Login exige conta ativa e e-mail verificado; a falha é indistinguível das demais ([security.md §3.1](security.md#31-verificação-de-e-mail-e-recuperação-de-senha)).
- Toda falha de login retorna a mesma resposta (e-mail inexistente, senha errada, conta desativada).
- Senhas: `DelegatingPasswordEncoder` (BCrypt por padrão, com prefixo de algoritmo para permitir migração futura).
- Autorização: papéis `OWNER`, `ADMIN`, `MEMBER` como `ROLE_*`; `@EnableMethodSecurity` ativo (`@PreAuthorize`). Tudo é autenticado por padrão; exceções públicas são explícitas em `SecurityConfig`.
- **Mudanças de papel, status, senha ou e-mail** exigem `UserSessions.revokeAll` na mesma operação ([security.md §4](security.md#4-sessões-e-permissões-desatualizadas)).
- Rate limit de login por endereço e por conta ([security.md §5](security.md#5-rate-limiting-de-login)).
- O Spring Session usa um gerenciador de transação JDBC próprio (`SessionConfig`). Usar o de JPA causa recursão: abrir sessão JPA resolve o tenant, que lê o contexto de segurança, que é carregado da sessão HTTP. Isso tem teste de regressão.

## 4. Comunicação frontend ↔ backend

- O navegador fala apenas com a origem do frontend. `/api/*` chega ao backend pelo `rewrites` do Next.js em dev; em produção, um reverse proxy roteia `/api/*` direto ao backend.
- Sem CORS, cookies first-party.
- JSON; erros no formato RFC 9457 (`application/problem+json`) com `status`, `title`, `detail` e, em validação, `errors[{field, message}]`.
- **Navegador:** `src/lib/api/client.ts` chama `/api/*` na mesma origem. O cookie de sessão é `HttpOnly` e o JavaScript nunca o lê. Nada de autenticação fica em `localStorage`.
- **Servidor (Server Components):** `src/lib/api/server.ts` chama `BACKEND_URL` diretamente, repassando apenas o cookie `SESSION`. `BACKEND_URL` não tem prefixo `NEXT_PUBLIC_` e não chega ao navegador. Atenção: o `rewrites` do Next lê `BACKEND_URL` **no build**; o fetch do servidor lê em runtime.
- **Rotas:**
  - `/app/*`: o layout consulta `/api/auth/me` e redireciona para `/login` sem sessão;
  - `/login`, `/signup`, `/forgot-password` e `/check-email` (grupo `(auth)`): redirecionam para `/app` se já houver sessão. Se o backend estiver fora do ar, mostram o formulário, sem loop de redirect;
  - `/verify-email` e `/reset-password` (grupo `(account)`): abertas por links de e-mail, funcionam com ou sem sessão. O token vem do fragmento da URL e é apagado da barra de endereço;
  - `/`: redireciona para `/app`.

  Isso é navegação, não autorização: o backend valida a sessão em toda chamada.
- Após o cadastro, o frontend vai para `/check-email` (não entra no painel). O primeiro login acontece depois da confirmação do e-mail.
- CSP com nonce por requisição; todas as páginas são renderizadas dinamicamente ([security.md §9](security.md#9-content-security-policy-frontend)).

## 4.1 Clientes (`customers`)

Primeiro módulo de negócio, e modelo para os próximos.

**Fluxo da requisição:**

```
Sessão → organizationId → Controller (DTO) → Service → Repository (JPA)
       → Hibernate @TenantId → SQL → PostgreSQL RLS
```

**Campos:**
- `name`: obrigatório, até 150 caracteres, com a grafia preservada.
- `phone`: até 40 caracteres, texto livre.
- `email`: trim + lowercase, não precisa ser único.
- `notes`: até 4000 caracteres, sempre exibido como texto.
- `archived_at`.

Valores opcionais em branco viram `null`. Ficam de fora de propósito: endereço (pertence a orçamento/proposta/obra), CPF/CNPJ e documentos.

**Endpoints:**

| Endpoint | Papéis | Resposta |
|---|---|---|
| `GET /api/customers?status=ACTIVE\|ARCHIVED\|ALL&q=&page=&size=` | todos | página |
| `GET /api/customers/{id}` | todos | cliente + `ETag: "<version>"` |
| `POST /api/customers` | todos | 201 + `ETag` |
| `PUT /api/customers/{id}` + `If-Match` | todos | 200 + nova `ETag` |
| `POST /api/customers/{id}/archive` + `If-Match` | OWNER, ADMIN | 200 + nova `ETag` |
| `POST /api/customers/{id}/restore` + `If-Match` | OWNER, ADMIN | 200 + nova `ETag` |

Não há `DELETE`: clientes são arquivados (`archived_at`) e restaurados, porque serão referenciados por propostas e obras.

**Paginação e busca:**
- Página padrão de 20 itens, com máximo de 100; `page` vai até 10.000 e `q` até 100 caracteres. Fora disso, a resposta é 400.
- Ordenação fixa: alterados mais recentemente primeiro, com `id` como desempate. O cliente não escolhe a ordenação, então não há `ORDER BY` montado a partir da entrada.
- A busca é um "contém" sem diferenciar maiúsculas em nome, e-mail e telefone, via Specification (JPA Criteria), com parâmetro vinculado e curingas de `LIKE` escapados.
- Não há índice de texto. Com volume, avaliar `pg_trgm`.

**Concorrência (padrão para recursos editáveis, `shared/web/EntityTags`):**
- A versão viaja no header `ETag` (não no corpo). Edição, arquivamento e restauração exigem `If-Match` com esse valor.
- Sem `If-Match`: 428. Valor desatualizado: **412 Precondition Failed**, sem sobrescrever a alteração de outra pessoa.
- Duas gravações simultâneas na mesma versão são resolvidas pelo `@Version` do JPA, que também gera 412.

**Entrada:**
- Propriedades desconhecidas no JSON (`organizationId`, `archived`, `id`...) são rejeitadas com 400 em toda a API (`spring.jackson.deserialization.fail-on-unknown-properties`). Isso impede mass assignment.

**Frontend:**
- `/app/customers` (lista, busca, filtro, paginação), `/app/customers/new` e `/app/customers/[id]` (detalhe, edição, arquivar/restaurar).
- As listas e o detalhe são renderizados no servidor a partir da URL (`?status=&q=&page=`). As escritas saem do navegador com CSRF e depois atualizam a página com `router.refresh()`.
- Os botões de arquivar e restaurar aparecem só para OWNER e ADMIN. Isso é só UX: o backend aplica `@PreAuthorize`.

## 4.2 Equipe (`team`)

| Endpoint | Papéis | Resposta |
|---|---|---|
| `GET /api/team/members` | todos | membros da organização (com `version`) |
| `PUT /api/team/members/{id}/role` + `If-Match` | OWNER | 200 + `ETag`; sessões do alvo revogadas |
| `POST /api/team/members/{id}/deactivate` + `If-Match` | OWNER, ADMIN (só MEMBER) | 200 + `ETag`; sessões revogadas |
| `POST /api/team/members/{id}/reactivate` + `If-Match` | OWNER, ADMIN (só MEMBER) | 200 + `ETag` |
| `GET /api/team/invitations` | OWNER, ADMIN | convites pendentes |
| `POST /api/team/invitations` (`email`, `role`: ADMIN \| MEMBER) | OWNER, ADMIN | 201 |
| `POST /api/team/invitations/{id}/resend` | OWNER, ADMIN | 200 |
| `POST /api/team/invitations/{id}/revoke` | OWNER, ADMIN | 204 |
| `POST /api/invitations/accept` (`token`, `name`, `password`) | pública, CSRF | 201; sem sessão |

- Não há `DELETE` de usuário.
- Regras de papel, convites e isolamento estão em [security.md §3.2](security.md#32-equipe-convites-papéis-e-status).
- **Frontend:** `/app/team` (membros, convite, convites pendentes; as ações mostradas dependem do papel) e `/accept-invite` (token no fragmento, nome, senha e confirmação).

## 5. Persistência

- Flyway é a única fonte do schema (`ddl-auto=validate`). Migrations aplicadas nunca são editadas; correções viram nova migration.
- IDs: UUID v7 gerado pela aplicação, com boa localidade de índice; embute o instante de criação. **IDs não são segredo nem mecanismo de autorização.**
- `BaseEntity` traz `version` (lock otimista), `created_at` e `updated_at` (`timestamptz`, UTC).
- `open-in-view` desligado: acesso a dados acontece em serviços transacionais.
- Banco: dono do schema (Flyway), grupo `orcaai_runtime` com privilégios concedidos tabela a tabela pelas migrations, e usuário da aplicação membro desse grupo, sem superusuário e sem `BYPASSRLS` ([security.md §8](security.md#8-usuários-do-banco-em-produção)). Dev e testes reproduzem essa separação.

## 6. Erros e validação

- `GlobalExceptionHandler` é o único ponto que monta respostas de erro, inclusive para filtros de segurança (via `SecurityProblemHandler`).
- Erros inesperados: 500 com mensagem genérica; detalhes apenas no log do servidor.
- `server.error.include-*` desligados; whitelabel desligado.
- Validação com Bean Validation (`@Valid`) nos DTOs de entrada.

## 7. Configuração e segredos

- `application.yml` tem padrões seguros (estilo produção) e lê credenciais só de variáveis de ambiente.
- Cabeçalhos `X-Forwarded-*` são ignorados por padrão; em produção, só são aceitos de um proxy explicitamente confiável ([security.md §6](security.md#6-proxy-e-cabeçalhos-encaminhados-produção)).
- Profile `dev`: lê o `.env` da raiz (ignorado pelo Git), cookie sem `Secure`, bootstrap opcional de usuário, Flyway com o dono e a aplicação com usuário restrito.
- Profile `test`: banco via Testcontainers; migrations extras de teste em `db/testmigration` (tabela de prova de isolamento e RLS).
- Actuator expõe apenas `health`, sem detalhes; probes `liveness`/`readiness` habilitados.

## 8. Backups e operação (a definir no deploy)

- PostgreSQL gerenciado com backup automático e PITR (point-in-time recovery), com restauração testada periodicamente.
- Sessões ficam no banco: restaurar backup também restaura sessões antigas. Após um incidente, limpar `spring_session`.
