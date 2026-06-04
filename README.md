# Financeiro API

Backend de um SaaS financeiro multi-tenant construído com Spring Boot 4 e Java 21. Oferece gestão completa de extratos, categorias, contas bancárias, previsões de fluxo de caixa, DRE, conciliação e relatórios — com autenticação JWT, controle de acesso por papéis e audit log completo.

**Produção:** https://finance-api-ejib.onrender.com  
**Frontend:** https://github.com/phmacieldev/finance-api-web

![CI](https://github.com/phmacieldev/finance-api/actions/workflows/ci.yml/badge.svg)

---

## Stack

| Tecnologia | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| PostgreSQL | 16 |
| Flyway | — |
| JWT (JJWT) | 0.12.6 |
| Bucket4j (rate limiting) | 8.10.1 |
| Apache POI (XLSX) | 5.3.0 |
| OpenCSV | 5.9 |
| Testcontainers | 1.20.4 |

---

## Funcionalidades

- **Auth completa** — registro, login, verificação de email (token expira em 24h), reset de senha
- **Multi-tenant** — isolamento total de dados por empresa via `TenantContext`
- **Roles** — `PLATFORM_ADMIN`, `CEO`, `OWNER`, `USER`
- **Extratos** — import CSV/XLSX, paginação, filtros, categorização, conciliação
- **Financeiro** — dashboard, DRE, relatório mensal, previsões, saldo anterior, export
- **Segurança** — rate limiting, HSTS, CNPJ com dígito verificador, mascaramento de logs
- **Audit log** — tabela `audit_logs` com 22 ações rastreadas em todos os módulos
- **LGPD** — `DELETE /me` para auto-exclusão de conta

---

## Rodando localmente

### Pré-requisitos

- Java 21
- Docker e Docker Compose

### 1. Subir o banco

```bash
docker-compose up -d
```

Sobe PostgreSQL 16 em `localhost:5432`, banco `financeiro`, usuário `admin`, senha `secret`.

### 2. Configurar variáveis de ambiente

Crie um arquivo `.env` na raiz (ou configure no seu IDE):

```env
JWT_SECRET=sua-chave-secreta-com-pelo-menos-48-caracteres-aqui
DB_URL=jdbc:postgresql://localhost:5432/financeiro
DB_USER=admin
DB_PASS=secret
CORS_ORIGINS=http://localhost:3000
APP_URL=http://localhost:3000
```

> Para email: configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `MAIL_FROM`.  
> Sem SMTP configurado, os links de verificação são exibidos no log (modo desenvolvimento).

### 3. Rodar a aplicação

```bash
./mvnw spring-boot:run
```

O Flyway executa as migrations automaticamente (V1–V15). Um admin padrão é criado na primeira inicialização:

```
Email:  admin@plataforma.com
Senha:  admin@1234
```

### 4. Verificar

```bash
curl http://localhost:8080/health
```

```json
{ "timestamp": "...", "status": "UP", "database": "UP" }
```

---

## Variáveis de ambiente

| Variável | Descrição | Padrão |
|---|---|---|
| `JWT_SECRET` | Chave HMAC-SHA384 (mín. 48 chars) | valor dev (não usar em prod) |
| `JWT_EXPIRATION` | Expiração do token em ms | `86400000` (24h) |
| `DB_URL` | JDBC URL do PostgreSQL | `jdbc:postgresql://localhost:5432/financeiro` |
| `DB_USER` | Usuário do banco | `admin` |
| `DB_PASS` | Senha do banco | `secret` |
| `CORS_ORIGINS` | Origens permitidas | `http://localhost:3000` |
| `APP_URL` | URL base do frontend (links de email) | `http://localhost:3000` |
| `SMTP_HOST` | Host SMTP | — |
| `SMTP_PORT` | Porta SMTP | `587` |
| `SMTP_USER` | Usuário SMTP | — |
| `SMTP_PASS` | Senha SMTP | — |
| `MAIL_FROM` | Remetente dos emails | — |
| `PLATFORM_ADMIN_EMAIL` | Email do admin da plataforma | `admin@plataforma.com` |
| `PLATFORM_ADMIN_PASSWORD` | Senha do admin da plataforma | `admin@1234` |

---

## Endpoints

### Autenticação (`/api/v1/auth`) — público

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/register` | Cadastro de empresa + CEO |
| `POST` | `/login` | Login |
| `GET` | `/verificar-email?token=` | Confirma email |
| `POST` | `/reenviar-verificacao` | Reenvia email de verificação |
| `POST` | `/esqueci-senha` | Solicita reset de senha |
| `POST` | `/resetar-senha` | Redefine senha via token |

### Perfil (`/api/v1/me`) — autenticado

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/` | Busca perfil |
| `PATCH` | `/` | Atualiza perfil |
| `PATCH` | `/senha` | Altera senha |
| `PATCH` | `/empresa` | Atualiza dados da empresa |
| `DELETE` | `/` | Exclui própria conta (LGPD) |

### Usuários (`/api/v1/users`) — CEO / OWNER

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/` | Lista usuários da empresa |
| `POST` | `/` | Convida usuário |
| `PATCH` | `/{id}` | Edita usuário |
| `DELETE` | `/{id}` | Remove usuário |

### Extratos (`/api/v1/extratos`) — CEO / OWNER

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/importar` | Importa CSV ou XLSX |
| `GET` | `/` | Lista paginado (filtros: mes, ano, categoriaId, contaBancariaId, tipo) |
| `GET` | `/sem-categoria` | Lista sem categoria (mes, ano) |
| `PATCH` | `/{id}/categoria` | Atribui categoria |
| `PATCH` | `/{id}/conta-bancaria` | Atribui conta bancária |
| `DELETE` | `/{id}` | Remove lançamento |
| `DELETE` | `/batch/{batchId}` | Cancela lote de importação |

### Categorias (`/api/v1/categorias`) — CEO / OWNER

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/` | Lista (filtro opcional: `tipo`) |
| `POST` | `/` | Cria categoria |
| `DELETE` | `/{id}` | Remove categoria |

### Contas Bancárias (`/api/v1/contas-bancarias`) — CEO / OWNER

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/` | Lista contas ativas |
| `POST` | `/` | Cria conta |
| `DELETE` | `/{id}` | Desativa conta |

### Financeiro — CEO / OWNER / USER

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/v1/dashboard` | KPIs e fluxo de caixa (mes, ano) |
| `GET` | `/api/v1/dre` | DRE (mes, ano) |
| `GET` | `/api/v1/relatorio/mensal` | Relatório (meses=1-24) |
| `GET` | `/api/v1/conciliacao/mensal` | Conciliação mensal |
| `GET` | `/api/v1/conciliacao/periodo` | Conciliação por período |
| `GET` | `/api/v1/previsoes` | Lista previsões |
| `POST` | `/api/v1/previsoes` | Cria previsão |
| `DELETE` | `/api/v1/previsoes/{id}` | Desativa previsão |
| `GET` | `/api/v1/saldo-anterior` | Busca saldo anterior |
| `POST` | `/api/v1/saldo-anterior` | Registra saldo anterior |
| `GET` | `/api/v1/export/csv` | Exporta CSV |
| `GET` | `/api/v1/export/xlsx` | Exporta XLSX |

### Admin (`/api/v1/admin`) — PLATFORM_ADMIN

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/empresas` | Lista empresas (filtro: status) |
| `PATCH` | `/empresas/{id}/aprovar` | Aprova empresa |
| `PATCH` | `/empresas/{id}/rejeitar` | Rejeita empresa |
| `PATCH` | `/empresas/{id}/plano` | Altera plano |
| `GET` | `/empresas/{id}/usuarios` | Lista usuários da empresa |
| `POST` | `/empresas/{id}/usuarios` | Adiciona usuário |
| `PATCH` | `/empresas/{enterpriseId}/usuarios/{userId}` | Altera role do usuário |
| `DELETE` | `/empresas/{enterpriseId}/usuarios/{userId}` | Remove usuário |

### Health (público)

```
GET /health
```

---

## Banco de dados

Migrations gerenciadas pelo Flyway:

| Versão | O que cria / modifica |
|---|---|
| V1 | Tabela `enterprises` |
| V2 | Tabela `users` |
| V3 | Tabela `categorias` |
| V4 | Tabela `extratos` |
| V5 | Tabela `previsoes` |
| V6 | Tabela `saldo_anterior` |
| V7 | Coluna `import_batch_id` em extratos |
| V8 | Tabela `contas_bancarias` |
| V9 | Coluna `conta_bancaria_id` em extratos |
| V10 | Verificação de email + status de empresa |
| V11 | Migração de roles (ADMIN → CEO) |
| V12 | `enterprise_id` nullable para PLATFORM_ADMIN |
| V13 | Token de reset de senha |
| V14 | Expiração do token de verificação de email |
| V15 | Tabela `audit_logs` |

---

## Testes

```bash
./mvnw test
```

16 testes de integração com Testcontainers (PostgreSQL real). Nenhum mock de banco.

---

## CI/CD

- **CI** — roda em todo push/PR via GitHub Actions
- **CD** — deploy automático no Render ao mergear na `main`

---

## Segurança

- Senhas com BCrypt
- JWT com HMAC-SHA384
- Rate limiting por IP (Bucket4j)
- HSTS: `max-age=31536000; includeSubDomains; preload`
- Validação de CNPJ com dígitos verificadores
- Mascaramento de dados sensíveis (email, CNPJ, tokens) nos logs
- Audit log de todas as operações de escrita
