# Financeiro API

> Backend de um SaaS financeiro multi-tenant construído do zero — não um CRUD de tutorial, mas algo onde multi-tenancy, segurança, integridade de dados e observabilidade precisam coexistir.

**Produção:** https://finance-api-ejib.onrender.com  
**Frontend:** https://github.com/phmacieldev/finance-api-web

![CI](https://github.com/phmacieldev/finance-api/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.6-6db33f?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791?logo=postgresql)
![Coverage](https://img.shields.io/badge/coverage-JaCoCo-brightgreen?logo=jacoco)

---

## Por que eu construí isso

Queria um projeto que me forçasse a tomar decisões de arquitetura reais. Um SaaS financeiro é naturalmente rico em regras de negócio: isolamento total de dados entre clientes, auditoria de operações, fluxo de aprovação de conta, DRE, conciliação — cada módulo trouxe um desafio diferente.

Escolhi Java 21 com Spring Boot porque queria dominar o ecossistema enterprise moderno, e esse projeto foi o lugar onde fiz isso acontecer.

---

## Stack

| Tecnologia | Versão | Por que |
|---|---|---|
| Java | 21 | Records, pattern matching, melhor inferência de tipos |
| Spring Boot | 4.0.6 | Ecossistema maduro, autoconfiguração sólida |
| PostgreSQL | 16 | ACID, suporte nativo a UUID, bom com Flyway |
| Flyway | — | Schema versionado no git, reproducível do zero |
| JWT (JJWT) | 0.12.6 | Auth stateless + refresh token |
| Bucket4j | 8.10.1 | Rate limiting por IP sem precisar de Redis |
| Apache POI | 5.3.0 | Import de extratos bancários em XLSX |
| OpenCSV | 5.9 | Import de extratos em CSV |
| Testcontainers | 1.20.4 | Testes de integração com PostgreSQL real |

---

## Decisões de arquitetura

### Multi-tenancy via TenantContext

Optei por thread-local em vez de row-level security no banco. O `TenantContext` é populado pelo filtro JWT antes de qualquer chamada de repositório, e cada query filtra por `enterpriseId` explicitamente. Mais verboso que RLS, mas completamente visível no código — qualquer dev consegue entender o isolamento só de ler um repositório.

```java
// JwtAuthenticationFilter extrai o tenant do JWT e popula o contexto
UUID enterpriseId = UUID.fromString(claims.get("enterpriseId").toString());
TenantContext.set(enterpriseId);
// ... limpeza garantida em finally
```

### Transações somente leitura

Métodos de consulta são anotados com `@Transactional(readOnly = true)`. O Spring não abre locks desnecessários e o PostgreSQL usa isso como dica para otimizar snapshots. Num SaaS com múltiplos tenants lendo ao mesmo tempo, esses detalhes importam.

### Audit log explícito

Em vez de AOP, o `AuditLogService` é chamado explicitamente em cada operação de escrita. Preferi a verbosidade: fica óbvio no code review quando uma ação não está sendo auditada. Hoje são 22 ações cobertas em todos os módulos.

### Flyway com migrations atômicas

Cada mudança de schema tem sua própria migration numerada. Migrations grandes são difíceis de fazer rollback e impossíveis de revisar em PRs. Com essa abordagem, qualquer dev reconstrói o banco do zero com `./mvnw spring-boot:run` — o Flyway cuida do resto.

### Erros de validação por campo

O `ApiExceptionHandler` retorna erros 422 com um mapa `campo → mensagem`, não uma lista genérica. O frontend consegue mostrar o erro diretamente no input correto sem precisar parsear strings.

```json
{
  "status": 422,
  "message": "Dados inválidos",
  "campos": {
    "cnpj": "CNPJ inválido",
    "email": "já está em uso"
  }
}
```

---

## Funcionalidades

- **Auth completa** — registro, login, verificação de email (token expira em 24h), refresh token, reset de senha
- **Multi-tenant** — isolamento total por empresa via `TenantContext` thread-local
- **Roles** — `PLATFORM_ADMIN`, `CEO`, `OWNER`, `USER` com controle por anotação
- **Extratos** — import CSV/XLSX com deduplicação por hash, criação manual, edição, paginação com filtros
- **Dashboard rico** — KPIs do mês, saldo acumulado total, fluxo diário, top categorias, últimos 5 lançamentos
- **DRE** — Demonstração de Resultado com categorias configuráveis por empresa
- **Previsões** — fluxo projetado com 8 frequências (semanal até anual)
- **Conciliação** — previsto × realizado por dia e por período
- **Relatório** — série histórica mensal configurável (até 24 meses)
- **Export** — CSV e XLSX com filtros aplicados
- **Segurança** — rate limiting por IP, HSTS, validação de CNPJ/CPF com dígito verificador
- **Audit log** — 22 ações rastreadas com timestamp, usuário e empresa
- **LGPD** — `DELETE /me` para auto-exclusão de conta

---

## Rodando localmente

**Pré-requisitos:** Java 21 + Docker

```bash
# 1. Banco de dados
docker-compose up -d

# 2. Variáveis de ambiente (.env na raiz)
JWT_SECRET=uma-chave-secreta-com-pelo-menos-48-caracteres
DB_URL=jdbc:postgresql://localhost:5432/financeiro
DB_USER=admin
DB_PASS=secret
CORS_ORIGINS=http://localhost:3000
APP_URL=http://localhost:3000

# 3. Rodar (Flyway executa V1-V17 automaticamente)
./mvnw spring-boot:run
```

Admin padrão criado na primeira inicialização:

```
Email:  admin@plataforma.com
Senha:  admin@1234
```

Para email (opcional em dev): configure `MAIL_USERNAME` (Gmail dedicado à API) e `MAIL_PASSWORD` (senha de app do Google). Sem essas variáveis, os links aparecem no log.

```bash
curl http://localhost:8080/health
# {"timestamp":"...","status":"UP","database":"UP"}
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
| `MAIL_USERNAME` | Gmail dedicado à API (ex: `app@gmail.com`) | — |
| `MAIL_PASSWORD` | Senha de app Gmail (Conta Google → Segurança → Senhas de app) | — |
| `PLATFORM_ADMIN_EMAIL` | Email do admin da plataforma | `admin@plataforma.com` |
| `PLATFORM_ADMIN_PASSWORD` | Senha do admin da plataforma | `admin@1234` |

---

## Endpoints

### Autenticação (`/api/v1/auth`) — público

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/register` | Cadastro de empresa + CEO |
| `POST` | `/login` | Login (JWT + refresh token) |
| `POST` | `/refresh` | Renova JWT com refresh token |
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
| `PATCH` | `/empresa` | Atualiza dados da empresa (suporte a PF e PJ) |
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
| `POST` | `/importar` | Importa CSV ou XLSX (deduplicação por hash) |
| `POST` | `/` | Cria lançamento manual |
| `PATCH` | `/{id}` | Edita lançamento |
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
| `GET` | `/api/v1/dashboard` | KPIs, saldo total acumulado, fluxo diário, top categorias, últimos lançamentos |
| `GET` | `/api/v1/dre` | DRE (mes, ano) |
| `GET` | `/api/v1/relatorio/mensal` | Série histórica (meses=1-24) |
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
| `POST` | `/empresas` | Cria empresa manualmente (PF ou PJ) |
| `PATCH` | `/empresas/{id}/aprovar` | Aprova empresa |
| `PATCH` | `/empresas/{id}/rejeitar` | Rejeita empresa |
| `PATCH` | `/empresas/{id}/plano` | Altera plano |
| `GET` | `/empresas/{id}/usuarios` | Lista usuários da empresa |
| `POST` | `/empresas/{id}/usuarios` | Adiciona usuário |
| `PATCH` | `/empresas/{enterpriseId}/usuarios/{userId}` | Altera role do usuário |
| `DELETE` | `/empresas/{enterpriseId}/usuarios/{userId}` | Remove usuário |

### Health (público)

```
GET /health  →  {"timestamp":"...","status":"UP","database":"UP"}
```

---

## Banco de dados

Migrations gerenciadas pelo Flyway — o schema inteiro é reproduzível do zero:

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
| V11 | Migração de roles (`ADMIN` → `CEO`) |
| V12 | `enterprise_id` nullable para `PLATFORM_ADMIN` |
| V13 | Token de reset de senha |
| V14 | Expiração do token de verificação de email |
| V15 | Tabela `audit_logs` |
| V16 | Tabela `refresh_tokens` |
| V17 | Suporte a pessoa física: `tipo_pessoa`, `cpf`; `cnpj` passa a nullable |

---

## Testes

```bash
./mvnw test
```

Testes de integração com Testcontainers: sobe um PostgreSQL real em container, aplica todas as migrations e executa os cenários. Nenhum H2, nenhum mock de banco — se passa aqui, passa em produção.

---

## CI/CD

- **CI** — roda em todo push e PR via GitHub Actions (build + testes completos)
- **CD** — deploy automático no Render ao mergear na `main`

---

## Segurança

- Senhas com BCrypt
- JWT com HMAC-SHA384 + refresh token persistido com TTL
- Rate limiting por IP via Bucket4j (sem Redis, funciona no free tier do Render)
- HSTS: `max-age=31536000; includeSubDomains; preload`
- Validação de CNPJ e CPF com dígitos verificadores
- Mascaramento de dados sensíveis (email, CPF/CNPJ, tokens) nos logs
- Audit log de todas as operações de escrita (22 ações)
