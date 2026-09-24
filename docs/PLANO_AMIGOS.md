# Plano — Amigos no app (adiado)

> **Status:** planejado, **não implementado**. Pedido do usuário em 24/09/2026; decidido
> deixar pra depois. Este documento descreve o que vai ser feito e como, pra retomar sem
> ter que redescobrir o contexto.
>
> **Regra de ouro:** nada disso pode afetar player/rádio. Tudo de amigos é rede opcional —
> se o Worker cair ou o aparelho estiver offline, o app segue igual, só a tela de amigos
> mostra "sem conexão".

## O que o usuário pediu

1. Um **ícone de amigos no header, ao lado do sino** (`HeaderNewsButton` em
   `ui/LocalTuneApp.kt`).
2. Ao tocar: **lista de amigos**, cada um num card com:
   - foto em miniatura (círculo);
   - nome;
   - **online / offline** (bolinha verde / cinza);
   - **o que está ouvindo agora** ("🎵 Música — Artista" ou "📻 Estação").
3. Botão **+ Adicionar amigo** → campo de busca **pelo nome** → aparecem as pessoas
   registradas no Cloudflare → botão **Enviar pedido**.
4. Quem recebe o pedido vê um card bonito com **a foto e o nome de quem pediu** e três
   ações: **Aceitar**, **Recusar**, **Ignorar**.

## O que já existe (base aproveitada)

| Peça | Onde | Serve pra |
|---|---|---|
| Heartbeat a cada 2 min (play/pause, app aberto) | `data/ListenerHeartbeat.kt` | online/offline + "ouvindo agora" |
| Tabela `listeners` no D1 (`pailer-fm-ouvintes`) | `_broadcast-boletins-local/distribuicao-app/migrations/0001_ouvintes.sql` | cadastro de todo mundo: `name`, `is_playing`, `station`, `track_title`, `track_artist`, `last_seen` |
| Worker `pailer-fm-boletins` | `_broadcast-boletins-local/distribuicao-app/src/index.js` | onde entram os endpoints novos |
| `install_id` (UUID por instalação) | `ListenerHeartbeat.installId()` | identidade do usuário (sem login) |
| Foto de perfil local | prefs `user_profile` → `profile_photo_path` | fonte da miniatura |

**Lacunas:**
- a foto **não sobe** pro servidor hoje (só vai `has_photo`);
- `install_id` **não é secreto o bastante** pra autorizar ações (ver Segurança);
- não existe tabela de amizades.

## Arquitetura

```
App (Compose)                         Worker Cloudflare                 Armazenamento
─────────────                         ─────────────────                 ─────────────
FriendsRepository ──HTTPS+token──▶  /api/friends/*          ──▶  D1: listeners, friendships,
  (polling)                           valida token                     listener_secrets
ListenerHeartbeat (já existe) ───▶  /api/heartbeat          ──▶  D1: listeners (last_seen, tocando)
Upload de avatar ────────────────▶  /api/avatar (PUT)       ──▶  R2: avatars/<install_id>.jpg
                                    /api/avatar/<id> (GET)  ◀──  (servido com cache)
```

### Fase 1 — Identidade segura (pré-requisito)

Problema: qualquer um que descubra o `install_id` de outra pessoa conseguiria aceitar
pedidos ou ver amigos no lugar dela.

- No primeiro heartbeat após a atualização, o app gera um **segredo aleatório** (32 bytes,
  base64) e guarda nas prefs `listener_heartbeat` (`KEY_SECRET`).
- O Worker guarda **só o hash SHA-256** em `listener_secrets(install_id, secret_hash)`.
  Registro é "primeiro a chegar": se o `install_id` já tem hash, um segredo diferente é
  recusado.
- Todo endpoint de amigos exige `Authorization: Bearer <install_id>:<segredo>`.
- Instalações antigas (sem segredo) registram na primeira chamada — sem login, sem tela.

### Fase 2 — Banco (migration `0003_amigos.sql`)

```sql
CREATE TABLE IF NOT EXISTS listener_secrets (
  install_id TEXT PRIMARY KEY,
  secret_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

-- Um pedido por par (from -> to). Amizade = linha com status 'accepted'.
CREATE TABLE IF NOT EXISTS friendships (
  from_id TEXT NOT NULL,          -- quem pediu
  to_id TEXT NOT NULL,            -- quem recebeu
  status TEXT NOT NULL,           -- 'pending' | 'accepted' | 'declined' | 'ignored'
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (from_id, to_id)
);
CREATE INDEX IF NOT EXISTS idx_friendships_to ON friendships(to_id, status);

-- Privacidade: aparecer ou não na busca (padrão: aparece).
ALTER TABLE listeners ADD COLUMN discoverable INTEGER NOT NULL DEFAULT 1;
ALTER TABLE listeners ADD COLUMN avatar_version INTEGER NOT NULL DEFAULT 0;
```

Regras:
- **Recusar** → `declined`; quem pediu **não é avisado** (vê "pedido enviado" até
  expirar) e só pode pedir de novo depois de 30 dias.
- **Ignorar** → `ignored`; some da lista de pedidos, sem aviso, sem cooldown pra quem
  pediu reenviar (mas não gera alerta novo).
- Se A pede pra B e B já tinha pedido pra A → vira `accepted` direto.
- **Desfazer amizade** → apaga a linha.

### Fase 3 — Endpoints no Worker

| Método | Rota | Faz |
|---|---|---|
| `GET` | `/api/friends` | amigos aceitos + `online` (`last_seen` < 3 min), `is_playing`, música/artista/estação, `avatar_url` |
| `GET` | `/api/friends/requests` | pedidos recebidos `pending` (nome + foto de quem pediu) e contagem pro badge |
| `GET` | `/api/friends/search?q=` | até 20 `listeners` com `name LIKE q%`, `discoverable = 1`, excluindo eu e quem já é amigo; retorna só id, nome, foto e status do pedido comigo |
| `POST` | `/api/friends/request` | `{ to_id }` → cria `pending` (valida cooldown / auto-aceite) |
| `POST` | `/api/friends/respond` | `{ from_id, action: accept\|decline\|ignore }` |
| `POST` | `/api/friends/remove` | `{ friend_id }` |
| `PUT` | `/api/avatar` | corpo JPEG ≤ 30 KB → R2 `avatars/<install_id>.jpg`, incrementa `avatar_version` |
| `GET` | `/api/avatar/<install_id>` | serve do R2 com `Cache-Control: public, max-age=86400` (URL leva `?v=avatar_version` pra furar cache quando muda) |

- Busca: mínimo 2 caracteres, limite de ~30 buscas/min por `install_id` pra ninguém
  varrer a base.
- "Ouvindo agora" só é devolvido **pra amigos aceitos** — a busca nunca mostra isso.
- `wrangler.jsonc`: adicionar binding R2 (`AVATARS`, bucket `pailer-fm-avatars`).
- O painel `/painel` pode ganhar depois um contador de amizades (opcional).

### Fase 4 — App: dados

- Novo `data/FriendsRepository.kt` (mesmo estilo do `ListenerHeartbeat`:
  `HttpURLConnection` + `org.json`, `Dispatchers.IO`, falhas só logam).
- `ListenerHeartbeat` passa a mandar o header de autenticação e o segredo.
- **Avatar:** ao salvar a foto no perfil (`LocalTuneViewModel`, onde grava
  `KEY_PROFILE_PHOTO_PATH`), gerar JPEG 128×128 quadrado (qualidade ~80) e fazer `PUT`.
  Também enviar uma vez após a atualização se `has_photo` e nunca enviado.
- **Polling:**
  - lista de amigos: a cada 30 s **só com a tela de amigos aberta**;
  - pedidos pendentes (badge): junto com o heartbeat de "app aberto" da `MainActivity`
    (a cada 2 min) — sem timer novo.
- Estado no `LocalTuneViewModel`: `friends`, `friendRequests`, `pendingRequestCount`,
  `friendSearchResults`.
- Cache local da última lista (prefs) pra abrir a tela instantânea mesmo offline.
- Miniaturas carregadas com cache em disco (mesma abordagem usada pras capas).

### Fase 5 — App: telas (Compose, em `ui/LocalTuneApp.kt` ou `ui/FriendsScreen.kt`)

1. **`HeaderFriendsButton`** ao lado do `HeaderNewsButton`, mesmo tamanho (30 dp,
   ícone 21 dp, `Icons.Filled.People`), com badge numérico `PailerRed` quando há pedidos.
2. **Painel de amigos** (mesmo padrão de painel do sino de notícias):
   - seção **Pedidos** no topo (se houver);
   - lista de amigos ordenada: online tocando → online → offline (por `last_seen`);
   - card: avatar 44 dp com bolinha de status no canto; nome; linha 2 =
     "🎵 Título — Artista" / "📻 Estação" / "Online" / "Visto há 2 h";
   - toque longo no card → "Desfazer amizade".
3. **+ Adicionar amigo**: campo de busca com debounce de 400 ms; resultado com avatar,
   nome e botão **Enviar pedido** → vira "Pedido enviado ✓" (ou "Amigos" / "Aceitar" se
   já houver relação).
4. **Card de pedido recebido**: avatar grande (72 dp) centralizado, nome em destaque,
   "quer ser seu amigo no Pailer FM", botões **Aceitar** (preenchido), **Recusar**
   (contorno) e **Ignorar** (texto).
5. **Perfil/Configurações:** switch "Aparecer na busca de amigos" (manda `discoverable`
   no heartbeat).

### Fase 6 (opcional, futuro) — aviso com o app fechado

O polling só avisa de pedido novo com o app aberto ou tocando. Aviso instantâneo com o app
fechado exige **Firebase Cloud Messaging** (Worker chama a API do FCM ao criar o pedido).
Fica fora do escopo inicial; o badge resolve o caso comum.

## Privacidade

- Busca mostra **só nome e foto**, e só de quem está `discoverable`.
- O que está tocando só aparece pra **amigos aceitos**.
- Foto sobe em miniatura de 128 px; a original nunca sai do aparelho.
- Apagar o app = `install_id` novo = perfil novo (amizades antigas ficam órfãs; limpar
  linhas de quem não aparece há 180 dias).

## Ordem de execução e testes

1. Migration + endpoints no Worker → `npx wrangler d1 migrations apply pailer-fm-ouvintes --remote`
   e `npx wrangler deploy`; testar com `curl` usando dois `install_id` falsos.
2. Segredo + upload de avatar no app → conferir no R2 e no `/painel`.
3. Telas → testar com **dois aparelhos** (ou aparelho + emulador): buscar, pedir,
   aceitar/recusar/ignorar, ver online/offline e música mudando em até ~2 min.
4. Conferir que com o Worker fora do ar (ou modo avião) nada trava e o player segue.

## Estimativa

- Worker + banco + R2: ~1 sessão.
- App (repositório, avatar, telas): ~2 sessões.
