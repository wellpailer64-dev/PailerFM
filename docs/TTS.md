# TTS — Vozes Locais

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

## Dois caminhos de voz

| Caminho | Tecnologia | Quando |
|---|---|---|
| **Principal** | sherpa-onnx offline, pacote instalado pelo usuário, roda no processo `:radio_voice` | `radioVoiceState.isEnabled` e síntese OK (timeout 12 s) |
| **Fallback** | `android.speech.tts.TextToSpeech` do sistema, pt-BR, rate 0.98 / pitch 0.88 | sem pacote ativo, ou síntese local falhou/estourou timeout |

O fallback usa utterance IDs distintos para intro (`pailer_player_radio_intro`) e boletim
(`pailer_player_news_break`); o `UtteranceProgressListener` chama `finishRadioIntro()` /
`finishNewsBreak()` em `onDone` e `onError`.

**Limitação conhecida (R7):** o fluxo espera `ttsReady` (init do TTS legado) mesmo quando
o caminho sherpa vai responder. Em aparelhos sem TTS do sistema funcional, a rádio perde a
intro/boletim sem precisão. Correção planejada ([TODO.md](TODO.md)).

## Processo separado `:radio_voice`

Declarado no manifest com `android:process=":radio_voice"` (ver ADR-001 em
[DECISIONS.md](DECISIONS.md)): OOM/crash no modelo de voz não derruba player nem UI.

Protocolo (`RadioVoiceSynthesisService`, `START_NOT_STICKY`, `onBind = null`):

```
Request  (Intent extras):
  EXTRA_RECEIVER : ResultReceiver   → resposta assíncrona
  EXTRA_TEXTS    : ArrayList<String> (uma entrada por fala)
  EXTRA_SPEAKERS : IntArray          (0 = Female, 1 = Male)

Response (ResultReceiver):
  RESULT_OK / RESULT_FAILED
  EXTRA_OUTPUT_PATH : caminho do WAV (>44 bytes = válido)
  EXTRA_DETAIL      : mensagem legível
  EXTRA_ELAPSED_MS  : tempo total
```

- Cada request cria um **novo** `LocalRadioVoiceEngine` e dá `release()` ao terminar
  (`use {}`) — não há vazamento entre requests, mas o modelo é recarregado do disco
  a cada boletim (custo aceito hoje, ver ADR-003);
- O `CoroutineScope` do serviço é global e **não serializa**: dois requests simultâneos
  carregam dois engines na RAM ao mesmo tempo;
- `stopSelfResult(startId)` encerra o serviço após cada request.

## Pacote de vozes

Instalado pela UI (Importar .zip). Layout interno:

```
filesDir/radio_voice_package/
├── manifest.json      ← obrigatório
└── package.ready      ← marcador escrito após import validado
```

### manifest.json (exemplo vits-dual)

```json
{
  "name": "Vozes PT-BR int8",
  "engine": "vits-dual",
  "femaleSpeaker": "Locutora",
  "maleSpeaker": "Locutor",
  "speed": 1.0,
  "voices": {
    "female": { "vits": { "model": "pt_BR-female-medium.onnx", "tokens": "tokens.txt" } },
    "male":   { "vits": { "model": "pt_BR-male-medium.onnx",   "tokens": "tokens.txt" } }
  }
}
```

### Motores suportados

| `engine` | Config exigida | Vozes |
|---|---|---|
| `vits` / `piper` | `vits` (ou campos soltos `model`/`tokens`) | multi-speaker via `femaleSpeakerId` / `maleSpeakerId` |
| `vits-dual` / `piper-dual` | `voices.female.vits` + `voices.male.vits` | um modelo por locutor (sid sempre 0) |
| `kokoro` | `kokoro`: model, voices, tokens (+ dataDir/lexicon/dictDir opcionais) | speaker id por locutor |
| `supertonic` | `supertonic`: durationPredictor, textEncoder, vectorEstimator, vocoder, ttsJson, unicodeIndexer, voiceStyle | voiceStyle por locutor |
| `mixed` (ADR-015) | `voices.female.engine` + config própria (`vits` ou `kokoro`); idem `voices.male` | cada locutor roda seu proprio motor — usado quando os dois locutores nao vem do mesmo pacote/motor (ex.: hoje, Frankie em Piper e Nicky em Kokoro) |

### Pacote ativo hoje: Frankie (Piper Faber) + Nicky (Piper Cadu) — `vits-dual`, ADR-015

O motor `mixed` (Frankie/Piper + Nicky/Kokoro) foi testado em boletim real e estourou
`BULLETIN_PREP_TIMEOUT_MS` (175s) — o Kokoro sozinho já era marginal (ADR-013/014), com
dois motores na mesma síntese ficou pior ainda. Decisão final: **Kokoro abandonado**,
os dois locutores em Piper puro (`vits-dual`), leve e rápido. Manifest real:

```json
{
  "name": "Frankie e Nicky (Piper leve)",
  "engine": "vits-dual",
  "femaleSpeaker": "Frankie",
  "maleSpeaker": "Nicky",
  "speed": 0.78,
  "voices": {
    "female": { "vits": { "model": "frankie/pt_BR-faber-medium.onnx", "tokens": "frankie/tokens.txt", "dataDir": "espeak-ng-data", "lengthScale": 1.0 } },
    "male":   { "vits": { "model": "nicky/pt_BR-cadu-medium.onnx",    "tokens": "nicky/tokens.txt",   "dataDir": "espeak-ng-data", "lengthScale": 1.0 } }
  }
}
```

- Nicky passou por Kokoro `pm_alex` (rejeitado por lentidão) → Piper `pt_BR-jeff-medium`
  (desempenho aprovado, timbre rejeitado) → Piper `pt_BR-miro-high` (também rejeitado) →
  **Piper `pt_BR-cadu-medium`** (mesmo dataset CC0 do Faber, ainda não testado antes
  desta rodada). Se ainda não for a definitiva, só sobra `Edresson` (qualidade "low",
  não baixado ainda) no pacote leve pt-BR conhecido, ou o não testado
  `vits-coqui-pt-cv` (Common Voice, multi-falante, pipeline bem diferente).
- `espeak-ng-data` fica uma única vez na raiz do pacote e é referenciado pelos dois
  slots — confirmado byte-a-byte idêntico entre todos os pacotes Piper testados.
- Fonte: `voice-models/piper_only_package/`; zip pronto pra importar:
  `voice-models/Pailer-Radio-Voices-FrankieFaber-NickyCadu.zip` (~36 MB, contra ~137 MB
  do pacote `mixed` original).
- Medido localmente (PC, não aparelho): os dois motores carregados + as 6 falas de um
  diálogo "Longo" inteiro sintetizadas em ~11s — folga enorme sobre os 175s de timeout,
  mesmo considerando que o aparelho real é mais lento que a máquina de teste.
- O motor `mixed` (código em `RadioVoicePackageRepository.kt`/`LocalRadioVoiceEngine.kt`,
  ver tabela acima) continua implementado e funcional, só não é mais o caminho ativo —
  ver ADR-015 antes de reativá-lo ou remover.
- Antes de trocar de voz de novo: ver ADR-015 pra lista de candidatos já testados e
  rejeitados/aprovados, e a explicação de que problemas de acentuação/pronúncia quase
  sempre são bug de texto sem acento, não do motor — testar sempre com frase acentuada.

### Histórico: pacotes Kokoro (Dora→Santa) e mixed (Faber+Kokoro) — ADR-013/014/015

Trocado do Piper vits-dual pro Kokoro em 26/08/2026 (voz aprovada pelo usuário depois de
ouvir no aparelho). Em 28/08/2026 (ADR-014) o slot `femaleSpeaker` — antes `pf_dora`
(ID 42), voz que o usuário achou ruim — trocou pra `pm_santa` (ID 44), a terceira voz
masculina do mesmo pacote (antes não usada). Motivo de reaproveitar o Kokoro em vez de
voltar pro Piper: o Piper já tinha sido testado e rejeitado antes por soar "fraco, sem
personalidade" (ver ADR-013) — trocar só o ID de um slot no manifest existente resolve
sem reabrir esse problema. Manifest real:

```json
{
  "name": "Kokoro Frankie e Nicky",
  "engine": "kokoro",
  "femaleSpeaker": "Frankie",
  "maleSpeaker": "Nicky",
  "femaleSpeakerId": 44,
  "maleSpeakerId": 43,
  "speed": 0.85,
  "kokoro": {
    "model": "model.int8.onnx",
    "voices": "voices.bin",
    "tokens": "tokens.txt",
    "dataDir": "espeak-ng-data",
    "lang": "pt-br",
    "lengthScale": 1.0
  }
}
```

- `femaleSpeaker`/`maleSpeaker` são só nomes de **slot** (mesmo campo que
  `RadioVoicePackageRepository` e `LocalRadioVoiceEngine` usam pra escolher modelo/ID —
  ver `RadioSpeaker.Female`/`Male` em `RadioBulletin.kt`), não implicam gênero da voz.
  Frankie (otimista) fala pelo slot Female, Nicky (pessimista) pelo slot Male — os dois
  com vozes masculinas do mesmo pacote.
- Origem: `kokoro-multi-lang-v1_0` do sherpa-onnx (não o `v1_1`, que **não** tem vozes
  pt-BR — ver ADR-013), modelo quantizado pra int8 localmente (326 MB → 114 MB).
- Antes de trocar o ID 44 pra produção, sintetizado localmente com o pacote Python
  `sherpa-onnx` (mesma cautela do ADR-013) — RMS/pico saudáveis, comparáveis aos IDs
  42/43, nenhum sinal de áudio degenerado. Ainda não teve aprovação por ouvido humano
  no aparelho — validar depois de importar o pacote novo.
- `speed` caiu de 0.92 pra 0.85 (locutores mais lentos, pedido do usuário) — ~9% mais
  samples de áudio pro mesmo texto, `BULLETIN_PREP_TIMEOUT_MS` subiu de 160s pra 175s
  de acordo (ver comentário em `LocalTuneViewModel.kt`).
- **Muito mais lento que o Piper**: ~9-10s pra carregar cada locutor (sem cache entre
  requests, ADR-003) + ~2 caracteres/s de geração (mais com `speed` mais baixo). Um
  diálogo "Curta" (2 falas no bate-bola novo, ver
  [RADIO_PIPELINE.md](RADIO_PIPELINE.md)) fica na faixa de 150-160s. Durações
  Normal/Longa tendem a estourar esse prazo e cair pra voz do Android no boletim ao
  vivo.
- Fonte/script de build do pacote: `voice-models/kokoro/` no workspace (fora do git,
  grande demais — reconstrutível: baixar `kokoro-multi-lang-v1_0.tar.bz2`, quantizar,
  reempacotar só com `model.int8.onnx` + `voices.bin` + `tokens.txt` +
  `espeak-ng-data/` + este `manifest.json`). Zip pronto pra importar pela UI:
  `voice-models/kokoro/Pailer-Radio-Voices-Kokoro-FrankieNicky.zip` (o zip anterior,
  `Pailer-Radio-Voices-Kokoro-PTBR.zip`, continua no workspace como rollback).

### Validações no import (`RadioVoicePackageRepository.importPackage`)

1. Descompacta em `filesDir/radio_voice_import/` (temporário);
2. Proteção zip-slip (`ensureInsideDirectory` em toda entrada);
3. Limite de **350 MB**;
4. Exige `manifest.json` na raiz e **pelo menos um `.onnx`**;
5. Valida arquivos declarados conforme o motor (modelo, tokens, lexicon, etc.);
6. Só depois troca atômica para `radio_voice_package/` + escreve `package.ready`.

`clearPackage()` remove tudo; import novo substitui o anterior.

## Geração (`LocalRadioVoiceEngine.synthesize`)

- Uma chamada sherpa por linha do script (`generateWithConfig`);
- Parâmetros globais: `speed` do manifest (coerido 0.65–1.35), `silenceScale = 0.6`,
  `numThreads = 2`, provider `cpu`, `maxNumSentences = 1`;
- Amostras concatenadas com **gap de silêncio de 0,18 s** entre falas;
- WAV PCM 16-bit mono escrito à mão (sem dependência de encoder) em `cacheDir`;
- Engines cacheados num map **por request** (chave = raiz+motor+speaker+modelo);
  liberados no fim do request.

## Diagnóstico

Logs com tag `PailerRadioVoice` nas duas pontas (app e serviço): carregamento do engine,
tempo de geração por fala, tamanho do WAV, falhas. Comece por aí antes de qualquer debug
mais fundo.
