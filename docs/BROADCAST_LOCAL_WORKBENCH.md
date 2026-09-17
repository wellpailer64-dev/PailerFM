# Bancada local de broadcast

> Esta pagina registra as conclusoes oficiais tiradas da pasta local
> `_broadcast-boletins-local/`. A pasta em si e area de trabalho privada e esta ignorada
> pelo Git; quando uma conclusao amadurece, ela deve ser copiada para `docs/`.

Analise iniciada em 2026-09-16.

## Papel da pasta

`_broadcast-boletins-local/` ja e a bancada real do pre-pipeline de boletins do Pailer FM.
Ela nao substitui o app Android atual; ela organiza experimentos, rotina diaria, roteiros,
audios e pesquisas que podem virar a futura central local de producao.

Estrutura observada:

```text
_broadcast-boletins-local/
  00-operacao-diaria/
  01-fontes/
  02-roteiros/
  03-audios/
  04-pacotes-app/
  90-pesquisa-voz/
  91-pesquisa-redator/
  inbox/
  logs/
  omnivoice_env/
  tests/
  voice_models/
```

## O que ja existe

- Checklist diario para operar boletins com revisao humana.
- Area para fontes e materias candidatas.
- Area de roteiros em JSON simples, ja no formato de falas Fran/Nico.
- Area de renders e comparativos de audio.
- Area para pacotes que futuramente podem ser importados/testados no app.
- Pesquisa de voz OmniVoice com receita aprovada fora do app.
- Pesquisa de redator local com Qwen3 14B via Ollama.
- Pipeline local sem fallback automatico de redacao: falha do Qwen vira falha visivel,
  nao roteiro generico.
- Logs de testes com tempo, parametros e decisao qualitativa.
- Primeira central local com interface minima:
  - `broadcast_core.py`
  - `broadcast_webview.py`
  - `broadcast_dashboard.py`
  - `abrir_central_broadcast.bat`
  - `CONTROL_PANEL.md`
  - interface principal HTML/pywebview em modo escuro;
  - versao Tkinter mantida como fallback legado;
  - abas separadas para `Redações` e `Boletins prontos`;
  - reproducao de audio final dentro da central;
  - revisao com `aprovado`, `eliminado` ou `refazer com observacao`.
  - botao `Rodar Pipeline` com acompanhamento em tempo real;
  - botao `Limpar ruido`, que arquiva testes antigos sem apagar nada.
  - cards de redacao com estado `na fila`, `fazendo agora` ou `pronto`;
  - aba de noticias/reserva, aba de redacoes, aba de boletins em producao e aba de
    boletins prontos;
  - popup de leitura das falas Fran/Nico;
  - medidores de CPU/RAM/GPU quando disponiveis;
  - log humano e log estruturado JSONL para auditoria.

## Pesquisa de voz

Status em 2026-09-16:

- Candidato forte: OmniVoice (`k2-fsa/OmniVoice`).
- Vozes definitivas de Fran e Nico aprovadas em teste local.
- Referencias atuais:
  - Fran: `voice_models/omnivoice/references/voz_da_fran_ref.wav`
  - Nico: `voice_models/omnivoice/references/voz_do_nico_ref.wav`
- Receita validada:
  - `num-step`: 25
  - `speed`: 1.05
  - `fade-duration`: 0.03
  - `guidance-scale`: 3.0
  - referencia curta, por volta de 7-11s
- Tempo observado no PC com RTX 3050 8GB: cerca de 15-25s para 6 falas.
- Ainda nao integrado ao app Android.

Pontos tecnicos importantes:

- Referencia de voz maior que 15-20s piora muito a velocidade sem ganho percebido.
- Referencia cortada no meio de palavra prejudica geracoes futuras.
- A fala nao deve terminar em sigla, estrangeirismo ou palavra rara, porque isso pode
  cortar o fim da frase na sintese.
- Houve patch manual no venv OmniVoice instalado: `trail_sil` de 100ms para 300ms.
  Esse patch precisa ser reaplicado se o ambiente for recriado.

## Pesquisa de redator

Status em 2026-09-16:

- O app Android continua com redator local proprio, sem mudanca nesta etapa.
- Pesquisa no PC: Qwen3 14B via Ollama, local e gratuito.
- A central de broadcast local passou a usar esse Qwen3 14B como redator operacional,
  com prompt v5, schema JSON rigido, validacao basica de qualidade e uma segunda
  tentativa no proprio Qwen quando a primeira resposta viola regra de formato/tom.
- O modelo gera JSON valido e com acentuacao correta, mas precisou de varias iteracoes de
  prompt para acertar tom, ordem e foco factual.
- Melhor versao documentada: prompt v5 em `91-pesquisa-redator/prompt_v5_system_instructions.txt`.

Aprendizado principal:

O redator melhorou de verdade quando a mensagem do usuario passou a incluir um campo de
`Contexto adicional`: fatos pesquisados fora do resumo principal da noticia. Isso deu
cor pessoal e especificidade ao bate-bola, evitando falas genericas.

Consequencia para a arquitetura:

```text
FONTES
  -> PESQUISA/ENRIQUECIMENTO
  -> REDACAO
  -> VALIDACAO
  -> FILA
  -> SINTESE
  -> VALIDACAO FINAL
  -> READY
```

Ou seja: a fase `PESQUISA/ENRIQUECIMENTO` deve virar etapa explicita do broadcast local,
nao um detalhe solto do prompt.

## Formatos atuais

Roteiros de teste usam JSON simples:

```json
[
  { "speaker": "Fran", "text": "..." },
  { "speaker": "Nico", "text": "..." }
]
```

Metricas do OmniVoice ja existem em JSON com:

- modelo;
- output;
- device;
- parametros de sintese;
- tempo de carregamento;
- tempo de geracao;
- duracao final;
- RTF;
- VRAM de pico;
- metricas por fala.

Isso e uma boa base para o futuro metadata do boletim, mas ainda nao substitui um
manifesto de broadcast com `id`, categoria, validade, prioridade e status.

## Lacunas antes de promover

- Conferir licenca do modelo e dos pesos do OmniVoice para o uso desejado.
- Transformar a receita de voz em etapa automatizada, nao so comando manual.
- Formalizar metadata por boletim.
- Separar `ready` de teste de `ready` de distribuicao.
- Criar validadores basicos para roteiro e audio.
- Definir como o app Android vai consumir boletins produzidos no PC sem quebrar o buffer
  atual.
- Decidir se OmniVoice entra como motor do PC apenas ou se um dia vira pacote de voz do
  Android.

## Direcao recomendada

As proximas implementacoes devem aproveitar a bancada local em vez de criar outra
estrutura. A primeira central local ja adiciona, ao lado dos roteiros e audios existentes:

```text
metadata/
  bulletins/
manifests/
ready/
queue/
failed/
control/
```

Sem mover nem apagar os artefatos atuais. A central ja le roteiros JSON existentes, cruza
com audios quando a relacao e clara, gera metadata v1, valida estrutura/audio e permite
publicar uma copia nomeada corretamente em `ready/`.

## Registro de progresso

- 2026-09-16: identificada a pasta `_broadcast-boletins-local/` como bancada principal do
  pre-pipeline. Documentados estado da pesquisa de voz, pesquisa de redator e impacto do
  `Contexto adicional` como etapa nova de enriquecimento.
- 2026-09-16: criada primeira central local Tkinter para inventario, validacao, pausa,
  retomada, logs, contadores por classe/tema/status e publicacao manual em `ready/`.
- 2026-09-16: central reorganizada em modo escuro com resumo discreto, abas de
  `Redações` e `Boletins prontos`, revisao/escuta abaixo das abas, limpeza organizada
  por arquivamento e primeiro ciclo de pipeline para buscar noticias e gerar redacoes
  acompanhadas em tempo real.
- 2026-09-16: criada central principal em HTML/pywebview, com fila superior de redacoes
  e boletins, bancada inferior de escuta/revisao/detalhes e timeline de acompanhamento
  em tempo real. A versao Tkinter ficou como fallback legado.
- 2026-09-16: adicionados estados visuais por card de redacao, progresso percentual,
  botao `Ler`, popup de falas Fran/Nico, indicadores de CPU/RAM/GPU e logs estruturados
  JSONL. O refresh automatico da tela deixou de poluir o log humano.
- 2026-09-16: removido fallback automatico da redacao no broadcast local. O pipeline agora
  usa Qwen3 14B via Ollama com prompt v5 e contexto adicional; se falhar, registra falha
  em vez de criar texto generico.
- 2026-09-16: adicionada reserva persistente de noticias e fila persistente de producao.
  `Rodar Pipeline` escolhe ate 10 noticias, produz redacao, envia automaticamente para
  sintese/mixagem e acompanha boletins em producao por status/porcentagem. Aprovacao
  humana ficou como revisao, nao como trava do funil automatico.
- 2026-09-16: adicionada acao manual `Sintetizar` para mandar uma redacao existente
  diretamente ao funil de sintese/mixagem sem rodar nova busca de noticias.
- 2026-09-16: refinada a UI de revisao: botoes de acao em faixa horizontal compacta,
  `Ouvir` dentro do card de boletim pronto, porcentagem visivel no card de producao,
  detalhes/timeline recolhidos por padrao e redacoes eliminadas/refazer fora da bancada
  normal sem apagar os arquivos.
- 2026-09-16: separada a captacao de noticias da producao. `Captar noticias` abastece a
  reserva; `Rodar Pipeline` consome primeiro a reserva e passa a produzir uma noticia
  por rodada, seguindo preparo -> redacao -> sintese/mixagem. Se a reserva estiver
  vazia, a captacao acontece automaticamente antes da producao. A UI removeu
  detalhes/timeline/player fixo e ganhou cor por tema nos cards.
- 2026-09-16: adicionada a etapa `Boletins aprovados`. A aprovacao copia o audio final,
  roteiro e metadados para `aprovados/<classe>/<tema>/`, deixando uma pasta final para o
  sistema futuro consumir. `Boletins prontos` e `Boletins aprovados` ganharam janelas por
  categoria e grade compacta de cards.
- 2026-09-16: criado MVP de distribuicao local para o app em
  `_broadcast-boletins-local/distribuicao-app/public/`. A saida contem `manifest.json`,
  audios, roteiros e metadados relativos, gerados a partir de `aprovados/`, com servidor
  local de teste em `servir_distribuicao_app.bat`.
- 2026-09-16: adicionada publicacao Cloudflare para o MVP. A central ganhou o botao
  `Publicar Cloudflare`, e a pasta local ganhou `publicar_cloudflare.bat` e
  `login_cloudflare.bat` para enviar a distribuicao ao projeto `pailer-fm-boletins` sem
  fazer upload manual de zip pelo painel.
