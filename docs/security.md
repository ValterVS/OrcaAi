# Segurança: regras e operação

Complementa [architecture.md](architecture.md). Regras marcadas como **Regra** são obrigatórias em code review.

## 1. IDs não são controle de acesso

UUIDs (v7) evitam enumeração sequencial, mas **não são segredo**: aparecem em URLs, logs e e-mails. Toda autorização e todo isolamento de tenant precisam funcionar mesmo que alguém conheça o UUID exato de um registro de outra organização. Os testes de isolamento partem exatamente dessa premissa.

## 2. Multi-tenancy: onde o isolamento acontece

| Camada | Mecanismo | Cobre |
|---|---|---|
| 1 | Organização vinda apenas da sessão (`TenantContext`) | Nunca de parâmetro, header ou corpo |
| 2 | Hibernate `@TenantId` em `TenantOwnedEntity` | JPQL/Criteria, `findById`, `findAll`, update/delete em lote via JPQL, insert, merge |
| 3 | PostgreSQL RLS (seção 7) | Qualquer SQL, inclusive nativo, quando ativado na tabela |

`TenantIsolationIntegrationTest` prova, com dois tenants e o UUID real do outro: busca por ID, listagem, insert, update (lote e merge de cópia destacada), delete (`deleteById`, `delete`, lote) e ausência de tenant.

**Regra:** consultas SQL nativas envolvendo dados multi-tenant exigem filtro explícito por organização (`TenantContext.requireOrganizationId()`) e teste de isolamento. O `@TenantId` não as protege.

**Regra:** acesso a dados de tenant sempre dentro de transação (`@Transactional` em serviço). Fora de transação, o RLS não recebe a organização.

**Regra:** jobs assíncronos e agendados não herdam a sessão. Devem processar uma organização por vez, cada uma em sua própria transação com o contexto da organização definido explicitamente.

## 3. Usuários

`users` **não** usa `@TenantId`: o login busca o usuário por e-mail antes de existir organização na sessão. Um filtro automático tornaria o login impossível (ou exigiria um modo "sem filtro" que falharia aberto).

O isolamento de usuários fica em `UserRepository`, que propositalmente **não** é um `JpaRepository`:
- não existe `findById`, `findAll` nem `deleteById` sem organização;
- busca por ID somente via `findByIdAndOrganizationId(id, TenantContext.requireOrganizationId())`;
- `findByEmail` existe apenas para autenticação.

Riscos a tratar ao criar a gestão de usuários:
- **Escalada de privilégio:** ADMIN não pode promover ninguém a OWNER nem alterar OWNER. Impedir a remoção do último OWNER.
- **Sessões:** mudanças de papel, status, senha ou e-mail devem revogar sessões (seção 4).
- **Enumeração:** o e-mail é único globalmente, então convites e signup não podem revelar que um e-mail já existe em outra organização.
- **Auditoria:** registrar quem alterou papel ou status de quem.
- **Listagens:** sempre filtradas por organização, com teste de isolamento como nas entidades de negócio.

## 4. Sessões e permissões desatualizadas

A sessão guarda um retrato do usuário (papel e status) feito no login. **Esperar a expiração não é aceitável.**

Estratégia: **revogação explícita** via `UserSessions.revokeAll(email)`, que remove todas as sessões do usuário no Spring Session JDBC (índice por principal). Toda operação que altere algum destes dados deve chamá-la na mesma operação, antes do commit:

| Evento | Ação |
|---|---|
| Usuário desativado ou removido | `revokeAll` |
| Papel alterado | `revokeAll` (o usuário entra de novo com o papel novo) |
| Senha alterada ou redefinida | `revokeAll`; o fluxo pode autenticar de novo a sessão atual |
| E-mail alterado | `revokeAll` com o e-mail **antigo** (o índice usa o nome do principal) |

Se a revogação acontecer e o commit falhar, o efeito é apenas um novo login (falha segura).

Pendência explícita: quando a gestão de usuários existir, cada operação acima precisa de um teste de integração provando que a sessão anterior recebe 401. O mecanismo base já está coberto por `UserSessionsIntegrationTest`.

Alternativa descartada por enquanto: revalidar o usuário no banco a cada requisição. É mais robusta contra esquecimento, mas custa uma consulta por requisição. Reavaliar se o número de pontos que alteram usuários crescer.

## 5. Rate limiting de login

Em memória (Caffeine), por instância, janela fixa:
- **por endereço:** toda tentativa conta (padrão: 30 / 15 min);
- **por conta (e-mail normalizado):** só falhas contam (padrão: 5 / 15 min).

A chave por conta não depende de o e-mail existir: a resposta 429 é idêntica para contas existentes e inexistentes.

Trade-off aceito: um atacante pode bloquear temporariamente o login de uma vítima. Evoluções: CAPTCHA após falhas, MFA e um store compartilhado (Redis) quando houver mais de uma instância.

## 6. Proxy e cabeçalhos encaminhados (produção)

Por padrão (`server.forward-headers-strategy: none`), o backend **ignora** `X-Forwarded-*`: o IP do cliente é o da conexão TCP. Isso foi verificado manualmente: `X-Forwarded-For` falsificado não contorna o limite por endereço.

Topologia recomendada:

```
Internet ──TLS──> Reverse proxy / load balancer
                    ├── /api/*  ──> backend:8080 (rede privada, sem acesso público)
                    └── /*      ──> frontend:3000
```

Configuração do backend atrás do proxy:

```
SERVER_FORWARD_HEADERS_STRATEGY=native
SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=10\.0\.1\.10     # regex com o(s) IP(s) exato(s) do proxy
```

- Não use o padrão do Tomcat para `internal-proxies`: ele confia em todas as faixas privadas.
- O proxy deve enviar `X-Forwarded-For` e `X-Forwarded-Proto`. O Tomcat percorre a lista da direita para a esquerda e para no primeiro IP não confiável.
- Com `X-Forwarded-Proto: https`, o Spring Security trata a requisição como segura e envia HSTS.
- O backend nunca deve ser acessível diretamente da internet. Caso contrário, qualquer cliente que alcance o IP confiável poderia forjar cabeçalhos.
- Evite rotear `/api` através do Next em produção. Se isso for inevitável, o servidor Next também precisa estar em `internal-proxies`.

Frontend: `HTTPS_ONLY=true` somente onde todo o tráfego é HTTPS (ativa HSTS e `upgrade-insecure-requests`).

## 7. PostgreSQL Row Level Security

### Como a combinação atual funciona

- **Hibernate `@TenantId`:** filtra na aplicação; não cobre SQL nativo.
- **`TenantTransactionManager`:** no início de **toda** transação JPA executa `set_config('app.current_organization_id', <org ou ''>, true)`. O terceiro parâmetro (`true`) equivale a `SET LOCAL`: o valor morre no commit/rollback e nunca sobrevive em uma conexão devolvida ao pool. Isso é provado por teste (mesmo `pg_backend_pid`, valor vazio na transação seguinte). Usa-se `set_config` em vez de `SET LOCAL` porque aceita parâmetro (`?`), sem concatenar SQL.
- **Spring Session JDBC:** usa o próprio `JdbcTransactionManager` (`SessionConfig`), não passa pelo hook e não toca tabelas de tenant. Isso evita a recursão sessão → tenant → sessão (coberta por `SessionTenancyRegressionTest`).
- **Flyway:** roda com o usuário dono do schema, fora do hook.
- **Pool (Hikari):** como o valor é local à transação, não há vazamento entre usuários.

### Política de referência

Já aplicada e testada na tabela de teste `tenancy_probes` (`V9001__enable_rls_on_tenancy_probes.sql`, `RowLevelSecurityIntegrationTest`):

```sql
ALTER TABLE <tabela> ENABLE ROW LEVEL SECURITY;
ALTER TABLE <tabela> FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON <tabela>
    USING (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid)
    WITH CHECK (organization_id = NULLIF(current_setting('app.current_organization_id', true), '')::uuid);
```

- Sem organização, `NULLIF(...)` dá `NULL`, nenhuma linha casa e o comportamento é **falha fechada**.
- `WITH CHECK` impede inserir ou mover linhas para outra organização.
- `ENABLE` não se aplica ao **dono** da tabela; `FORCE` faz a política valer também para ele.
- Superusuários e papéis com `BYPASSRLS` **sempre** ignoram RLS, mesmo com `FORCE`. Por isso a aplicação precisa de um usuário próprio (seção 8).
- Exige índice em `organization_id` (predicado de toda consulta).

### Usuários e casos especiais

| Situação | Tratamento |
|---|---|
| Aplicação | Usuário `NOSUPERUSER NOBYPASSRLS`, não dono das tabelas |
| Migrations de schema | Dono do schema. DDL não é afetado por RLS |
| Migrations de dados em tabelas com RLS | Com `FORCE`, o dono também é filtrado: definir `set_config` por organização no script ou usar um papel de manutenção com `BYPASSRLS`, explícito e revisado |
| Jobs assíncronos | Contexto de organização explícito antes da transação; sem ele, não veem nada |
| Consultas administrativas legítimas (suporte, relatórios globais) | Papel separado com `BYPASSRLS`, fora da aplicação, com acesso auditado. Nunca relaxar a política da aplicação |
| `users`, `organizations`, `spring_session*` | Sem RLS: lidos antes de existir tenant (login) ou não são dados de tenant |

### Decisão

- A infraestrutura está **ativa**: o hook roda em toda transação e o desenho está provado em tabela de teste.
- A política RLS deve ser criada **na mesma migration que cria cada tabela de negócio multi-tenant**, começando por `customers`. Isso é obrigatório antes de armazenar qualquer dado comercial real.
- Cada tabela nova também precisa de um teste de isolamento com SQL direto, como `RowLevelSecurityIntegrationTest`.

## 8. Usuários do banco em produção

Dois usuários, ambos sem superusuário:

```sql
-- Executado uma vez por um administrador. Senhas vêm do secret manager, nunca do repositório.
CREATE ROLE orcaai_owner LOGIN PASSWORD '<secret>' NOSUPERUSER NOCREATEROLE NOBYPASSRLS;
CREATE ROLE orcaai_app   LOGIN PASSWORD '<secret>' NOSUPERUSER NOCREATEROLE NOBYPASSRLS;

ALTER SCHEMA public OWNER TO orcaai_owner;
GRANT USAGE ON SCHEMA public TO orcaai_app;
ALTER DEFAULT PRIVILEGES FOR ROLE orcaai_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO orcaai_app;
ALTER DEFAULT PRIVILEGES FOR ROLE orcaai_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO orcaai_app;
```

| Variável | Usuário |
|---|---|
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | `orcaai_owner` (migrations) |
| `DB_USERNAME` / `DB_PASSWORD` | `orcaai_app` (runtime) |

O ambiente local reproduz essa separação: `infra/postgres/init/01-app-user.sh` cria `APP_DB_USER` no primeiro `docker compose up`. Foi verificado que esse usuário não cria tabelas, não faz `DROP` e não desativa RLS.

Pendência menor: os privilégios padrão também dão DML em `flyway_schema_history` ao usuário da aplicação. Revogar isso em produção após a primeira migration.

## 9. Content Security Policy (frontend)

- **Nonce por requisição:** gerado em `src/proxy.ts` (128 bits). O Next aplica o nonce aos próprios scripts; por isso todas as páginas são renderizadas dinamicamente (`connection()` no layout raiz).
- **Produção:** `script-src 'self' 'nonce-…' 'strict-dynamic'`, `style-src 'self' 'nonce-…'`, `object-src 'none'`, `base-uri 'self'`, `form-action 'self'`, `frame-ancestors 'none'`, sem `unsafe-eval` e sem `unsafe-inline`.
- **Desenvolvimento:** adiciona `unsafe-eval` e `style-src 'unsafe-inline'`, exigidos pelas ferramentas de dev do React/Next.
- **Demais cabeçalhos** (em todas as respostas, incluindo assets): `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`.
- **HSTS:** apenas com `HTTPS_ONLY=true`.
- **Backend:** a API tem CSP própria `default-src 'none'; frame-ancestors 'none'`.
- **Scripts de terceiros:** qualquer script externo futuro exige revisão da política.

## 10. Dependências

- **Frontend:** `npm audit` a cada atualização. O lockfile é versionado.
- **Backend:** versões gerenciadas pelo Spring Boot parent. Ainda não há varredura automática; recomenda-se ativar Dependabot (ou equivalente) ao publicar o repositório.
