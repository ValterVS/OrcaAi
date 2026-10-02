
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

### Cadastro (signup) e tenancy

O cadastro é a única operação que cria dados sem usuário autenticado. Ele **não** enfraquece o mecanismo de tenancy:
- grava apenas `organizations` e `users`, que não são filtradas por tenant;
- a organização do novo OWNER é a linha recém-criada, nunca um valor da requisição. `organizationId`, `role` e `id` enviados pelo cliente são ignorados;
- organização e OWNER são criados na mesma transação: se o usuário falha, a organização é desfeita;
- se no futuro o cadastro precisar criar dados de tenant (ex.: configurações iniciais), isso deve ocorrer num passo explícito, com o contexto da nova organização definido pelo backend, nunca por um endpoint genérico de seleção de tenant.

### E-mail e senha

- **E-mail:** normalizado só com trim + lowercase (sem regras de provedor, pontos e `+` preservados). Unicidade garantida pelo banco (`users_email_uk`) e formato normalizado garantido por `CHECK (email = lower(btrim(email)))`. No cadastro concorrente, a constraint é o árbitro final.
- **Senha:** 12 a 64 caracteres, sem regras de composição. Senhas longas e frases são incentivadas. O limite superior inclui no máximo 72 bytes em UTF-8, porque o BCrypt ignora o excedente; rejeitar é melhor que truncar em silêncio. O hash continua com o `DelegatingPasswordEncoder` (`{bcrypt}`), o que permite migrar para Argon2 sem invalidar senhas.
- A senha nunca é logada, retornada ou incluída em exceções (`SignupRequest.toString` a omite). O hash é calculado antes de checar o e-mail, para que o tempo de resposta não revele se o e-mail já existe.
- **Enumeração:** o cadastro responde sempre 202 com a mesma mensagem, exista ou não o e-mail (ver §3.1).

### 3.1 Verificação de e-mail e recuperação de senha

**Estado da conta.**
- `active`: a conta pode operar (decisão administrativa).
- `email_verified_at`: o endereço foi confirmado (`null` = pendente).

São conceitos independentes:
- **Cadastro normal:** cria `active = true` e `email_verified_at = null`.
- **`DevBootstrap` (só no profile `dev`):** cria o usuário já verificado.
- **Usuários anteriores a `V5`:** foram marcados como verificados, porque só existiam em desenvolvimento.

**Login.** Só contas ativas **e** verificadas entram. O status é checado **depois** da senha (`SecurityConfig`), então conta inexistente, senha errada, conta desativada e conta não verificada recebem o mesmo 401, com o mesmo corpo e o mesmo custo de BCrypt.

**Tokens** (`OneTimeTokens`):
- 32 bytes de `SecureRandom`, em Base64 URL-safe sem padding (43 caracteres).
- O banco guarda só o SHA-256 (32 bytes, `UNIQUE`). Isso basta para um valor aleatório de 256 bits; não há senha a proteger com hash lento.
- Tabelas separadas por finalidade, `email_verification_tokens` e `password_reset_tokens`, com `created_at`, `expires_at` e `consumed_at`.
- **Validade:** 24 h para a verificação e 30 min para a redefinição (configuráveis).
- **Uso único e concorrência:** o consumo é um `UPDATE ... WHERE consumed_at IS NULL AND expires_at > now() RETURNING user_id`. O PostgreSQL trava a linha, e a segunda requisição concorrente encontra 0 linhas. Testado com 4 threads.
- **Novo token aposenta os anteriores:** um token novo expira os anteriores da mesma finalidade (`expires_at = now()`), então `consumed_at` sempre significa "usado". A emissão trava a linha do usuário (`FOR UPDATE`) para que pedidos simultâneos não deixem dois tokens válidos.
- O token bruto existe só na memória e no e-mail: nunca em banco, log, exceção ou resposta (`toString` dos DTOs e eventos o omite).

**Links.**
- Montados a partir de `APP_PUBLIC_URL`, nunca do header `Host` (proteção contra host-header injection). A URL é validada na inicialização: absoluta, `https` (`http` só em localhost), sem query, fragmento ou credenciais.
- O token vai no **fragmento** (`/verify-email#token=...`), que o navegador não envia ao servidor: não aparece em logs de acesso nem no `Referer`. A página lê o fragmento, apaga-o da barra de endereço e envia o token no corpo de um `POST`.
- Nenhuma ação acontece num `GET`. A confirmação de e-mail exige um clique explícito, para que scanners de e-mail que abrem links não confirmem contas.

**Cadastro sem enumeração.** `POST /api/auth/signup` responde sempre `202` com "Se os dados puderem ser utilizados, enviaremos as instruções para continuar o cadastro.":
- **E-mail novo:** cria organização + OWNER pendente + token e envia o e-mail.
- **E-mail existente:** não cria nada e não altera nada (nome, empresa, senha). Se a conta está pendente e fora do cooldown, reenvia a confirmação.
- **Cadastro concorrente para o mesmo e-mail:** a constraint única decide; o perdedor tem rollback completo e recebe a mesma resposta.

**Reenvio de confirmação.** `POST /api/auth/resend-verification` responde sempre `202`. Só envia para conta ativa, pendente e fora do cooldown.

**Esqueci minha senha.**
- `POST /api/auth/forgot-password` responde sempre `202`.
- Contas inativas nunca recebem token.
- **Decisão:** contas ainda não verificadas **podem** redefinir a senha. Seguir o link prova o controle da caixa de e-mail, então a redefinição também marca o e-mail como verificado.

**Redefinição.** `POST /api/auth/reset-password` (token + nova senha, mesma política do cadastro), numa única transação:
1. consome o token;
2. troca o hash da senha;
3. expira os demais tokens de redefinição e de verificação;
4. revoga todas as sessões (`UserSessions`).

Não há login automático.

**Pendência de manutenção: limpeza de tokens.** Tokens expirados ou usados continuam nas tabelas, mas são inutilizáveis: o consumo exige `consumed_at IS NULL AND expires_at > now()`. Não há job de limpeza, e o runtime não tem `DELETE` nessas tabelas. Definir a retenção (ex.: apagar após 30 dias) com um papel ou job próprio antes de escalar, ou quando o volume justificar.

**Envio de e-mail** (`AccountEmails`):
- Disparado por evento publicado dentro da transação e entregue **somente após o commit** (`@TransactionalEventListener`). Um rollback nunca gera e-mail com link inútil.
- O envio é **assíncrono**, para que o tempo de resposta não revele se houve envio.
- **Executor:** um pool próprio e limitado (`accountEmailExecutor`: 2 threads, fila de 500).
  - O executor padrão do Boot tem fila ilimitada e, com virtual threads, cria uma thread por tarefa; nenhum dos dois é aceitável sob flood.
  - Com a fila cheia, o e-mail é descartado com log e o usuário pode pedir de novo; a requisição nunca falha por isso.
  - No desligamento, aguarda até 20 s pelos envios em andamento.
- **Timeouts SMTP:** 5 s para conexão, 10 s para leitura e 10 s para escrita.
- **Notificação de senha alterada:** enviada após cada redefinição bem-sucedida, também só depois do commit. É texto simples, sem link, token ou senha.
- Uma falha de SMTP é registrada só com o tipo do erro (sem endereço, token ou mensagem do servidor). O cliente recebe a resposta normal e pode pedir reenvio.
- Não há outbox nem fila: um e-mail perdido (falha de SMTP ou restart antes do envio) é recuperado pelo reenvio ou por um novo pedido de redefinição.
- E-mails em texto + HTML simples, sem imagens externas, rastreadores ou marketing.

**Logs.** Só ID do usuário, tipo do evento e resultado. Nunca e-mail, senha, hash, token ou URL com token. Há teste (`AccountEmailsIntegrationTest`) e verificação no E2E.

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
| Senha alterada ou redefinida | `revokeAll` (a redefinição por e-mail já faz isso; não há login automático) |
| E-mail alterado | `revokeAll` com o e-mail **antigo** (o índice usa o nome do principal) |

`revokeAll` apaga as sessões **na transação de quem chama** (se a alteração falhar, as sessões ficam) e de novo **logo após o commit**. A segunda passada fecha a janela em que um login com a senha antiga, feito enquanto a alteração ainda não estava confirmada, ficaria com sessão válida. Se a repetição pós-commit falhar, isso é registrado em log de erro.

Pendência explícita: quando a gestão de usuários existir, cada operação acima precisa de um teste de integração provando que a sessão anterior recebe 401. O mecanismo base já está coberto por `UserSessionsIntegrationTest`.

Alternativa descartada por enquanto: revalidar o usuário no banco a cada requisição. É mais robusta contra esquecimento, mas custa uma consulta por requisição. Reavaliar se o número de pontos que alteram usuários crescer.

## 5. Rate limiting das rotas públicas de conta

Em memória (Caffeine), por instância, janela fixa (`AuthRateLimitFilter`):

| Endpoint | Chave | O que conta | Padrão |
|---|---|---|---|
| Login | IP | toda tentativa | 30 / 15 min |
| Login | IP + e-mail normalizado | só falhas (401) | 5 / 15 min |
| Cadastro | IP | toda tentativa | 5 / 1 h |
| Reenvio de confirmação + esqueci a senha | IP (limite compartilhado) | toda tentativa | 10 / 1 h |
| Confirmação de e-mail + redefinição de senha | IP | toda tentativa | 20 / 15 min |

Além disso, há um **cooldown de envio por conta** (2 min, no banco): enquanto ele dura, novos pedidos recebem a mesma resposta, mas nenhum e-mail novo é enviado. O cooldown limita só o envio de e-mails, nunca o login ou o uso de um link já recebido.

- **Não existe limite pelo e-mail sozinho.** Ele permitiria a qualquer pessoa bloquear o login de uma vítima errando a senha de propósito. Falhas vindas de um IP não afetam a vítima em outro IP (coberto por `AuthRateLimitIntegrationTest`).
- As chaves não dependem de a conta existir: conta existente e inexistente produzem exatamente a mesma sequência de respostas (401 e depois 429, com corpo idêntico).
- **Risco aceito:** um ataque distribuído contra uma única conta (muitos IPs) só é contido pelo limite de cada IP. Evoluções: MFA, CAPTCHA após falhas, monitoramento de falhas por conta (alerta, não bloqueio) e store compartilhado (Redis) com mais de uma instância.

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
| `users`, `organizations`, `spring_session*` | Sem RLS (exceção documentada). `users` é lida por e-mail no login e escrita no cadastro, antes de existir tenant; `organizations` é a própria raiz do tenant, criada no cadastro; `spring_session*` não são dados de tenant. Colocar RLS nelas exigiria um modo "sem tenant" que falharia aberto. O isolamento de `users` fica em `UserRepository` (§3) |
| `email_verification_tokens`, `password_reset_tokens` | Sem RLS (exceção documentada): são lidas e consumidas por fluxos públicos, sem tenant, e não pertencem a uma organização (`user_id`, não `organization_id`). A proteção é o hash de 256 bits, o uso único e o runtime sem `DELETE`. |

### Decisão

- **Ativo em produção na tabela `customers`** (`V6__create_customers.sql`): `ENABLE` + `FORCE` + `USING` + `WITH CHECK`, com o runtime só com `SELECT, INSERT, UPDATE`.
  - `CustomerIsolationIntegrationTest` prova, com SQL direto e o usuário restrito, que B não lê, não altera, não arquiva, não insere em nome de A e não move linhas para A.
  - O E2E repetiu a verificação no PostgreSQL real.
- **Regra para toda nova tabela de negócio multi-tenant:** a mesma migration cria a tabela, `organization_id`, `ENABLE`/`FORCE ROW LEVEL SECURITY`, a policy com `USING` e `WITH CHECK`, o índice começando por `organization_id` e o `GRANT` mínimo a `orcaai_runtime`. Ela também precisa de um teste de isolamento com SQL direto.

## 8. Usuários do banco em produção

Três papéis, nenhum superusuário:

| Papel | Login | Função |
|---|---|---|
| `orcaai_owner` | sim | Dono do schema; executa o Flyway. Usado só em deploy |
| `orcaai_runtime` | não | Grupo que recebe os privilégios de tabela. **Nome fixo, referenciado pelas migrations** |
| `orcaai_app` (qualquer nome) | sim | Usuário da aplicação, membro de `orcaai_runtime` |

```sql
-- Provisionamento, uma vez, por um administrador. Senhas vêm do secret manager, nunca do repositório.
CREATE ROLE orcaai_owner   LOGIN PASSWORD '<secret>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE orcaai_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE orcaai_app     LOGIN PASSWORD '<secret>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS
    IN ROLE orcaai_runtime;

ALTER SCHEMA public OWNER TO orcaai_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO orcaai_runtime;
```

- **Sem `ALTER DEFAULT PRIVILEGES`:** nenhuma tabela nova fica acessível implicitamente, e `flyway_schema_history` nunca é concedida ao runtime.
- **Regra:** toda migration que cria tabela concede explicitamente a `orcaai_runtime` só as operações necessárias (ex.: `V3__grant_runtime_privileges.sql`). Hoje `users` e `organizations` não têm `DELETE`.
- O runtime não é dono de nada, não tem `CREATE` no schema, não altera tabelas, não cria políticas e não toca o histórico do Flyway (`DatabasePrivilegesIntegrationTest`).
- **Os testes reproduzem exatamente esse modelo:** o container cria os três papéis (`db/test-database-roles.sql`), o Flyway roda como `orcaai_owner` e a aplicação como `orcaai_app`. Uma migration que esqueça um `GRANT` quebra os testes.

| Variável | Usuário |
|---|---|
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | `orcaai_owner` (migrations) |
| `DB_USERNAME` / `DB_PASSWORD` | `orcaai_app` (runtime) |

**Ambiente local:** `infra/postgres/init/01-app-user.sh` cria `orcaai_runtime` e `APP_DB_USER` quando o volume é criado. O Flyway roda como `POSTGRES_USER` (superusuário da imagem Docker, aceitável só em dev). Volumes criados antes desta versão usam o modelo antigo (default privileges) e devem ser recriados com `docker compose down -v`.

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
