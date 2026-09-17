# Broadcast local: nomes, metadados e plano de evolucao

> Regra desta fase: preservar o pipeline atual de boletins antes de reorganizar.
> O fluxo Android existente ja escreve, sintetiza e toca boletins; a arquitetura de
> broadcast deve nascer ao redor dele, sem duplicar motores validados.

Documento iniciado em 2026-09-16 para acompanhar a evolucao do sistema de boletins do
Pailer FM rumo a uma central local de producao.

> **Status 16/09/2026 (mesmo dia, mais tarde):** o passo 9 do "Plano cauteloso" abaixo
> ("conectar gradualmente a central local ao app, sem remover o buffer Android") foi
> concluido e ULTRAPASSADO no mesmo dia - o app ja consome o feed publicado
> (`BroadcastFeedRepository`, ver `distribuicao-app/public/manifest.json` mais abaixo) E
> o pipeline embarcado antigo (RSS, redator local/Gemini, sintese de voz local/Gemini/TTS
> Android) foi removido por completo do app, nao so suplementado. A secao "Diagnostico do
> estado atual" logo abaixo descreve esse pipeline embarcado **que ja nao existe mais** -
> mantida como registro historico do ponto de partida. Ver ADR-034/ADR-035 em
> [DECISIONS.md](DECISIONS.md) e o estado atual em [RADIO_PIPELINE.md](RADIO_PIPELINE.md).
> O restante deste documento (taxonomia, contrato de metadata, manifest.json) descreve a
> central de broadcast externa (`_broadcast-boletins-local/`, fora deste app Android) e
> continua valendo para esse lado.

## Diagnostico do estado atual (histórico — pipeline embarcado, removido 16/09/2026)

O sistema atual de boletins esta implementado principalmente dentro do app Android.
Ele funciona como um buffer embarcado, nao ainda como uma central local externa.
Tambem existe uma bancada local importante em `_broadcast-boletins-local/`, usada para
operacao diaria, pesquisa de voz, pesquisa de redator, renders e comparativos. O mapa
oficial dessa bancada esta em [BROADCAST_LOCAL_WORKBENCH.md](BROADCAST_LOCAL_WORKBENCH.md).

Fluxo atual observado:

```text
feeds RSS/Atom
  -> NewsBulletinRepository.loadStories()
  -> RadioBulletinRepository.loadScripts()
  -> OptionalLocalLlmRadioScriptWriter ou RemoteGeminiRadioScriptWriter, com fallback
  -> RadioScript.withPhilosophicalCloser()
  -> LocalTuneViewModel.refillBulletinBuffer()
  -> RadioVoiceSynthesisService / LocalRadioVoiceEngine ou GeminiFlashTtsEngine
  -> filesDir/radio_bulletins_ready/radio_core_*.wav
  -> bulletin_buffer_manifest.json
  -> speakNextNewsBreak()
```

Arquivos/componentes relevantes:

- `data/NewsBulletinRepository.kt`: carrega feeds de noticia, resumo e fonte.
- `data/RadioBulletin.kt`: modelos de roteiro, locutores, prompts, redator local/Gemini e fallback.
- `data/RadioBulletinTts.kt`: configuracao experimental de TTS Gemini.
- `player/LocalTuneViewModel.kt`: buffer, priorizacao pratica atual, persistencia do manifesto e reproducao.
- `player/RadioVoiceSynthesisService.kt`: servico isolado para sintese de voz.
- `player/LocalRadioVoiceEngine.kt`: motor local Supertonic/sherpa, concatena falas e escreve WAV.
- `docs/RADIO_PIPELINE.md`: descricao fiel do pipeline atual.
- `docs/TTS.md`: detalhes da voz local.

O que ja esta validado e deve ser preservado:

- personalidades de Fran e Nico;
- estrutura de falas `RadioScriptLine`;
- caminhos de redacao local/Gemini/fallback;
- sintese local Supertonic;
- sintese Gemini experimental com fallback;
- buffer persistente de WAVs prontos;
- logs e protecoes contra perda imediata de trabalho.

## Limites do modelo atual

O pipeline atual ja persiste um manifesto, mas ele ainda e um manifesto interno do app.
Ele guarda roteiro, fonte, resumo, duracao, arquivo de audio e origem Gemini/local, mas
nao e suficiente para uma central de broadcast.

Lacunas para broadcast:

- nao ha `id` estavel por boletim;
- nao ha classificacao `evergreen` vs `temporal`;
- nao ha categoria editorial normalizada;
- nao ha `priority`, `publish_after` ou `expires_at`;
- nomes de WAV ainda sao tecnicos (`radio_core_*.wav`);
- `ready` ainda significa "tocavel pelo app", nao "boletim completo aprovado para distribuicao";
- nao ha separacao persistente entre roteiro bruto, validado, fila, sintese, erro e pronto;
- validacao ainda esta embutida nos caminhos de fallback, nao modularizada.

## Taxonomia inicial

`content_type`:

- `evergreen`: conteudo vitalicio ou reaproveitavel sem urgencia temporal.
- `temporal`: noticia, lancamento, evento ou informacao com janela de utilidade.

`priority`:

- `100`: urgente.
- `70`: temporal importante.
- `50`: temporal normal.
- `20`: evergreen.

`category` sugerida para evergreen:

- `science`
- `space`
- `technology`
- `history`
- `geopolitics`
- `music`
- `cinema`
- `games`
- `culture`
- `health`
- `human`
- `curiosities`

Categorias podem ser ampliadas por configuracao/metadata, sem exigir alteracao do
pipeline inteiro.

## Nome de arquivo proposto

O nome do arquivo deve ajudar humanos e sistemas, mas a fonte da verdade sera sempre o
JSON de metadata.

Formato sugerido:

```text
pailerfm_bulletin_{content_type}_p{priority}_{category}_{created_at}_{slug}_{short_id}_v{version}.wav
```

Exemplo:

```text
pailerfm_bulletin_temporal_p070_geopolitics_20260916T183015Z_eleicoes-no-chile_blt9f3a2_v001.wav
```

Regras:

- tudo em minusculo;
- ASCII seguro para Windows, Android e storage futuro;
- `created_at` em UTC no formato `yyyyMMdd'T'HHmmss'Z'`;
- `slug` curto, sem acentos, apenas `a-z`, `0-9` e `-`;
- `short_id` e redundante de proposito: evita colisao quando dois boletins tem titulo parecido;
- versao com tres digitos (`v001`) para permitir reprocessamento do mesmo boletim.

Para roteiros:

```text
pailerfm_script_{content_type}_p{priority}_{category}_{created_at}_{slug}_{short_id}_v{version}.json
```

## Metadata JSON v1

Cada boletim/redacao deve ter um JSON proprio. O manifesto geral podera apontar para
esses JSONs, em vez de conter tudo embutido.

Exemplo:

```json
{
  "schema_version": 1,
  "id": "blt_20260916_183015_9f3a2",
  "internal_title": "Eleicoes no Chile entram na reta final",
  "slug": "eleicoes-no-chile",
  "content_type": "temporal",
  "category": "geopolitics",
  "priority": 70,
  "created_at": "2026-09-16T18:30:15Z",
  "publish_after": "2026-09-16T18:30:15Z",
  "expires_at": "2026-09-18T23:59:59Z",
  "sources": [
    {
      "name": "Veja Mundo",
      "url": null,
      "retrieved_at": "2026-09-16T18:29:50Z"
    }
  ],
  "status": "ready",
  "version": 1,
  "speakers": [
    { "id": "fran", "slot": "Female" },
    { "id": "nico", "slot": "Male" }
  ],
  "script": {
    "path": "scripts/validated/temporal/geopolitics/pailerfm_script_temporal_p070_geopolitics_20260916T183015Z_eleicoes-no-chile_blt9f3a2_v001.json",
    "source": "LocalLlm",
    "duration": "Normal",
    "line_count": 6
  },
  "audio": {
    "path": "ready/temporal/geopolitics/pailerfm_bulletin_temporal_p070_geopolitics_20260916T183015Z_eleicoes-no-chile_blt9f3a2_v001.wav",
    "format": "wav",
    "sample_rate_hz": 44100,
    "bytes": 6883524,
    "tts_provider": "current",
    "voice_engine": "supertonic3"
  },
  "validation": {
    "script": {
      "passed": true,
      "checks": ["structure", "speaker_count", "text_not_empty"]
    },
    "audio": {
      "passed": true,
      "checks": ["file_exists", "wav_header", "min_size"]
    }
  },
  "processing": {
    "attempts": 1,
    "last_attempt_at": "2026-09-16T18:45:00Z",
    "last_error": null
  }
}
```

## Status propostos

Estados de roteiro:

- `draft`: redacao gerada, ainda nao validada.
- `awaiting_validation`: aguardando validacao automatica/manual.
- `validated`: roteiro aprovado.
- `rejected`: roteiro recusado.
- `queued`: pronto para sintese.

Estados de audio:

- `synthesizing`: sintese em andamento.
- `synthesized`: audio gerado, ainda nao validado.
- `ready`: audio aprovado para broadcast.
- `failed`: falha, preservando roteiro e metadata.
- `expired`: temporal venceu e nao deve ser usado.

## Estrutura local sugerida

Existe uma bancada local em `_broadcast-boletins-local/`. Portanto, a proposta abaixo nao
deve ser criada como uma arvore paralela do zero; ela deve ser incorporada gradualmente
nessa bancada, aproveitando `01-fontes/`, `02-roteiros/`, `03-audios/`, `logs/` e as
pesquisas ja existentes.

```text
_broadcast-boletins-local/
  metadata/
    bulletins/
  manifests/
  queue/
    urgent/
    temporal/
    evergreen/
  processing/
    synthesis/
    final_validation/
  ready/
    evergreen/
    temporal/
  aprovados/
    evergreen/
    temporal/
  distribuicao-app/
    public/
      manifest.json
      audio/
      scripts/
      metadata/
  failed/
    scripts/
    audio/
  logs/
  validators/   (futuro)
```

No app Android atual, o equivalente embrionario de `ready/` e:

```text
filesDir/radio_bulletins_ready/
```

Essa pasta nao deve ser renomeada diretamente sem uma migracao, porque o app ja depende
dela e ja ha logica de limpeza/manifesto ao redor.

## MVP de distribuicao para o app

A etapa aprovada gera uma saida estatica para o app futuro:

```text
_broadcast-boletins-local/distribuicao-app/public/
  manifest.json
  audio/{content_type}/{category}/...
  scripts/{content_type}/{category}/...
  metadata/{content_type}/{category}/...
```

Contrato inicial do `manifest.json`:

```json
{
  "schema_version": 1,
  "generated_at": "2026-09-16T21:30:00Z",
  "name": "Pailer FM Broadcast Distribution MVP",
  "total": 1,
  "categories": {
    "games": 1
  },
  "items": [
    {
      "id": "blt_abc123",
      "title": "Titulo interno",
      "slug": "titulo-interno",
      "status": "approved",
      "content_type": "temporal",
      "category": "games",
      "priority": 50,
      "created_at": "2026-09-16T21:00:00Z",
      "approved_at": "2026-09-16T21:20:00Z",
      "audio": {
        "path": "audio/temporal/games/blt_abc123_titulo-interno.wav",
        "format": "wav",
        "bytes": 123456,
        "duration_seconds": 45.2,
        "sha256": "..."
      },
      "script": {
        "path": "scripts/temporal/games/blt_abc123_titulo-interno.script.json",
        "line_count": 6,
        "speakers": ["Fran", "Nico"]
      },
      "metadata": {
        "path": "metadata/temporal/games/blt_abc123_titulo-interno.json"
      }
    }
  ]
}
```

O app deve tratar `manifest.json` como fonte da verdade. Arquivos antigos que sobrem na
pasta publica nao devem ser consumidos se nao estiverem listados no manifesto.

## Plano cauteloso de implementacao

1. Documentar o estado atual e o contrato de metadata. Feito neste arquivo.
2. Documentar a bancada local existente. Feito em `BROADCAST_LOCAL_WORKBENCH.md`.
3. Criar modelos puros de metadata, sem tocar na sintese.
4. Gerar nomes novos para WAVs futuros, mantendo leitura dos nomes antigos.
5. Expandir o manifesto atual para carregar/salvar campos novos com defaults seguros.
6. Criar validadores basicos:
   - metadata obrigatoria;
   - roteiro com falas nao vazias;
   - locutores conhecidos;
   - audio existente e maior que cabecalho WAV;
   - temporal nao expirado.
7. Adicionar `metadata/`, `manifests/`, `queue/`, `ready/` e `failed/` dentro da bancada
   local, sem mover os artefatos atuais.
8. Criar um comando local unico para simular o ciclo usando primeiro roteiros/audios ja
   existentes como entrada.
9. Conectar gradualmente a central local ao app, sem remover o buffer Android ate o novo
   caminho tocar boletins reais com seguranca.

## Registro de progresso

- 2026-09-16: analisado o broadcast atual. Conclusao: existe pipeline funcional embarcado,
  com buffer persistente de audio, mas ainda falta contrato de metadata, taxonomia e
  nomes de arquivo de broadcast. Primeiro documento criado para guiar a migracao.
- 2026-09-16: identificada `_broadcast-boletins-local/` como a bancada real do
  pre-pipeline. O plano foi ajustado para aproveitar essa pasta em vez de criar uma
  estrutura paralela. Tambem foi incorporada a necessidade de uma etapa explicita de
  pesquisa/enriquecimento antes da redacao.
