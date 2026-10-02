# Orça Aí

SaaS multiempresa para orçamentos e propostas, começando por empresas de obras e reformas.

- Decisões técnicas: [docs/architecture.md](docs/architecture.md)
- Regras e operação de segurança: [docs/security.md](docs/security.md)

## Estrutura

```
backend/         Spring Boot 4 (Java 25, Maven), monólito modular
frontend/        Next.js 16 (TypeScript)
docs/            Decisões de arquitetura e segurança
infra/postgres/  Script de inicialização do banco local (usuário da aplicação)
docker-compose.yml   PostgreSQL e Mailpit (e-mails locais)
```

## Requisitos

- JDK 25
- Node.js 24
- Docker

## Rodando localmente

```bash
cp .env.example .env          # defina POSTGRES_PASSWORD e APP_DB_PASSWORD (e opcionalmente DEV_BOOTSTRAP_*)
docker compose up -d

cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # http://localhost:8080

cd frontend
cp .env.example .env.local
npm install
npm run dev                                                # http://localhost:3000
```

Abra `http://localhost:3000/signup` para cadastrar uma empresa. O cadastro cria a organização e o usuário OWNER com o e-mail pendente de confirmação. Os e-mails não saem da máquina: abra o Mailpit em `http://localhost:8025`, use o link "Confirmar e-mail" e depois entre em `/login`. "Esqueci minha senha" funciona do mesmo jeito.

No painel, **Clientes** (`/app/customers`) permite cadastrar, buscar, editar, arquivar e restaurar clientes. Arquivar e restaurar são exclusivos de OWNER e ADMIN.

O usuário restrito da aplicação (`APP_DB_USER`, membro de `orcaai_runtime`) é criado apenas quando o volume do banco é criado. Volumes criados antes desta versão usam um modelo de privilégios antigo: recrie com `docker compose down -v` (isso apaga os dados locais).

Com `DEV_BOOTSTRAP_EMAIL` e `DEV_BOOTSTRAP_PASSWORD` preenchidos, o profile `dev` cria uma organização e um usuário `OWNER` na primeira inicialização.

Health check: `GET http://localhost:8080/actuator/health`.

## Testes e build

```bash
cd backend && ./mvnw clean package     # testes + jar; requer Docker (Testcontainers)
cd frontend && npm test && npm run lint && npm run typecheck && npm run build
```

## Variáveis de ambiente

| Variável | Onde | Descrição |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | `.env` | Banco local; `POSTGRES_USER` é o dono do schema e roda as migrations |
| `APP_DB_USER`, `APP_DB_PASSWORD` | `.env` | Usuário restrito usado pela aplicação em `dev` |
| `DEV_BOOTSTRAP_EMAIL`, `DEV_BOOTSTRAP_PASSWORD` | `.env` (opcional) | Usuário inicial em `dev` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | ambiente (produção) | Conexão da aplicação (usuário só com DML) |
| `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` | ambiente (produção) | Dono do schema, usado só pelas migrations |
| `SERVER_FORWARD_HEADERS_STRATEGY`, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | ambiente (produção) | Só atrás de proxy confiável; ver `docs/security.md` §6 |
| `APP_PUBLIC_URL` | `.env` / ambiente | URL pública do frontend, base dos links enviados por e-mail (obrigatória em produção; `https`) |
| `MAIL_FROM` | `.env` / ambiente | Remetente dos e-mails transacionais |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | ambiente (produção) | SMTP do provedor; em dev, o padrão é o Mailpit (`localhost:1025`) |
| `MAIL_STARTTLS`, `MAIL_SMTP_AUTH` | ambiente (produção, opcional) | Padrão `true`; ajuste conforme o provedor |
| `MAILPIT_SMTP_PORT`, `MAILPIT_UI_PORT` | `.env` (opcional) | Portas locais do Mailpit (1025 e 8025) |
| `BACKEND_URL` | `frontend/.env.local` | Destino do proxy `/api` |
| `HTTPS_ONLY` | ambiente do frontend | `true` apenas com HTTPS garantido (HSTS) |

Nunca versione `.env`, `.env.local` ou credenciais.
