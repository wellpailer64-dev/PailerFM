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
