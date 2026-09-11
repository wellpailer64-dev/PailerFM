# TTS — Vozes Locais

> **Regra de ouro deste projeto:** preservar o comportamento atual antes de refatorar.
> Toda mudança estrutural mantém as funcionalidades existentes e é feita incrementalmente.
> O app já funciona e está em uso diário. Nada de "clean architecture deluxe" que mate a rádio no processo.

## Dois caminhos de voz

| Caminho | Tecnologia | Quando |
|---|---|---|
| **Principal** | sherpa-onnx offline, pacote instalado pelo usuário, roda no processo `:radio_voice` | `radioVoiceState.isEnabled` e síntese OK (timeout 12 s) |
| **Fallback** | `android.speech.tts.TextToSpeech` do sistema, pt-BR, rate 0.98 / pitch 0.88 | sem pacote ativo, ou síntese local falhou/estourou timeout |

Depois do ajuste de 11/09/2026, o fallback Android não é mais aceito para a entrada
automática da rádio quando a voz dos boletins está ligada. Um boletim verde/Gemini ou
com voz local preparada precisa tocar pelo WAV do buffer; se esse WAV não estiver
tocável na hora do intervalo, a entrada é cancelada e o buffer é reposto/corrigido em
segundo plano. O TTS Android continua existindo como fallback quando a voz dos boletins
está desligada, e como rede de compatibilidade para caminhos que não representam um
boletim pré-sintetizado pronto.

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

### manifest.json (motor único: Supertonic 3)

```json
{
  "name": "Fran e Nico (Supertonic 3)",
  "femaleSpeaker": "Fran",
  "maleSpeaker": "Nico",
  "femaleSpeakerId": 1,
  "maleSpeakerId": 5,
  "speed": 1.0,
  "numSteps": 6,
  "lang": "pt",
  "backgroundMusic": ["bed1.pcm", "bed2.pcm"],
  "backgroundMusicVolume": 0.05623,
  "supertonic": {
    "durationPredictor": "duration_predictor.int8.onnx",
    "textEncoder": "text_encoder.int8.onnx",
    "vectorEstimator": "vector_estimator.int8.onnx",
    "vocoder": "vocoder.int8.onnx",
    "ttsJson": "tts.json",
    "unicodeIndexer": "unicode_indexer.bin",
    "voiceStyle": "voice.bin"
  }
}
```

`femaleSpeaker`/`maleSpeaker` são só nomes de exibição; quem seleciona a voz de fato são
`femaleSpeakerId`/`maleSpeakerId` — o `sid` (0-9) dentro do `voice.bin` do Supertonic
(único arquivo, 10 vozes: `F1..F5 = sid 0-4`, `M1..M5 = sid 5-9`, ordem alfabética de
montagem — ver `generate_voices_bin.py` do sherpa-onnx). `numSteps` é o número de passos
de denoising (qualidade x velocidade, default oficial do modelo é 5 — usado aqui também,
depois de medir em campo que `numThreads` mais alto piorava por throttling térmico, ver
ADR-018/019); `lang` tem que ser `"pt"` — sem isso o motor cai no default `"en"` do C++
(bug encontrado e corrigido na ADR-018).

`backgroundMusic`/`backgroundMusicVolume` são opcionais — bed de música baixinho por
baixo do boletim inteiro (`LocalRadioVoiceEngine.mixBackgroundMusic()`, ver ADR-019).
`backgroundMusic` é uma **lista** de arquivos **PCM16 mono sem cabeçalho WAV** (não é
`.wav`), no mesmo sample rate que o motor gera (44100Hz pro Supertonic, sem reamostragem
em runtime) — gerar com `ffmpeg -i bed.mp3 -ar 44100 -ac 1 -f s16le -acodec pcm_s16le
bed.pcm`. Com mais de um arquivo, um é sorteado por boletim num índice que sobrevive
entre requests (companion object, ver `nextBackgroundMusicIndex`) pra não repetir sempre
o mesmo. Aceita também uma string única (compatibilidade). `backgroundMusicVolume` é
ganho linear (não dB) — `0.05623` ≈ -25dB, o valor aprovado depois de testar fora do app
com ffmpeg. Lista vazia = pacote funciona exatamente como antes (sem custo, sem música).

### Motor suportado: `supertonic` (ADR-018)

Piper (`vits`/`vits-dual`/`piper-dual`) e Kokoro (`kokoro`), incluindo o modo `mixed`
(ADR-015) que rodava um motor por locutor, foram **removidos** — ver ADR-018 para o
histórico completo dos candidatos testados (Kokoro, Piper com várias vozes, VITS-Coqui,
OmniVoice, Pocket TTS) e o motivo de cada rejeição. Hoje só existe um `SupertonicConfig`
(`durationPredictor`, `textEncoder`, `vectorEstimator`, `vocoder`, `ttsJson`,
`unicodeIndexer`, `voiceStyle`), compartilhado pelos dois locutores via `sid` diferente.

### Pacote ativo: Fran (F2) e Nico (M1) — Supertonic 3 int8

Escolhidos numa comparação às cegas com 4 duplas homem+mulher conversando (M2+F2, M5+F3,
M3+F4, M1+F5); o usuário preferiu a dupla 4 mas pediu a voz F2 (não a F5) pareada com o
M1. Descrições oficiais do fabricante: **F2** "alegre, jovem, brincalhona"; **M1**
"animado, confiante, tom padrão".

- Fonte: `voice-models/supertonic-3-int8/` (o próprio `manifest.json` + os arquivos do
  modelo + `bed1.pcm`/`bed2.pcm` da música de fundo — essa pasta *é* a raiz do zip, sem
  wrapper). Zip pronto pra importar:
  `voice-models/Pailer-Radio-Voices-Supertonic3-Fran-Nico.zip` (~165 MB). Scripts usados
  nos testes (comparação de duplas, ajuste de pausa/acentuação) em
  `voice-models/supertonic-3-int8-scripts/`.
- Download original: `wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2`
  (reconstrutível a qualquer momento, é release oficial do k2-fsa/sherpa-onnx).
- `speed=1.0` (dentro da faixa 0,9-1,5 recomendada oficialmente — não herdar o `0.78`
  usado antes pro Piper). `numSteps` começou em 10 (mais qualidade, RTF≈0,28 medido em
  PC), caiu pra 5 (default oficial) depois de medir em campo (ver ADR-019) e subiu de
  novo pra **6** depois que a queda de tempo se confirmou boa o bastante pra sobrar
  margem de qualidade. `numThreads` também foi ajustado em campo: 2→4 **piorou**
  (throttling térmico), landing em **3**.
- **Uma pendência conhecida, documentada em detalhe na ADR-018** (a outra — sentenças
  curtas isoladas saindo atropeladas — foi corrigida em produção, ver ADR-019):
  Palavras de hiato mal pronunciadas (ex. "tardio" → soa como ditongo, sem separar o
  "i"). Sem parâmetro de motor pra isso — é o texto do boletim que precisa grafar com
  acento forçado (`tardío`) quando o problema aparecer.
- Música de fundo (bed baixinho por baixo do boletim) **implementada** (ADR-019) —
  `backgroundMusic`/`backgroundMusicVolume` no manifest, ver seção do manifest acima.

### Validações no import (`RadioVoicePackageRepository.importPackage`)

1. Descompacta em `filesDir/radio_voice_import/` (temporário);
2. Proteção zip-slip (`ensureInsideDirectory` em toda entrada);
3. Limite de **350 MB**;
4. Exige `manifest.json` na raiz e **pelo menos um `.onnx`**;
5. Valida arquivos declarados conforme o motor (modelo, tokens, lexicon, etc.);
6. Só depois troca atômica para `radio_voice_package/` + escreve `package.ready`.

`clearPackage()` remove tudo; import novo substitui o anterior.

## Geração (`LocalRadioVoiceEngine.synthesize`)

- Uma chamada sherpa por linha do script (`generateWithConfig`), variando só `sid`
  (feminino/masculino) — o mesmo `OfflineTts` (Supertonic) atende os dois locutores;
- Parâmetros globais: `speed`/`numSteps`/`lang` do manifest (`speed` coerido
  0.65–1.35), `silenceScale = 0.6` na config do engine, `numThreads = 2`, provider
  `cpu`, `maxNumSentences = 1`;
- Amostras concatenadas com **gap de silêncio de 0,18 s** entre falas;
- WAV PCM 16-bit mono escrito à mão (sem dependência de encoder) em `cacheDir`;
- Engine cacheado num map **por request** (chave = `rootDir` do pacote); liberado no
  fim do request.

## Diagnóstico

Logs com tag `PailerRadioVoice` nas duas pontas (app e serviço): carregamento do engine,
tempo de geração por fala, tamanho do WAV, falhas. Comece por aí antes de qualquer debug
mais fundo.
