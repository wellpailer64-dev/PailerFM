package com.pailer.localtune.data

// Motor de sintese de voz do boletim (Fran/Nico) - CURRENT e o motor local de sempre
// (LocalRadioVoiceEngine/Supertonic), sem nenhuma mudanca de comportamento. GEMINI_FLASH e a
// opcao EXPERIMENTAL pedida pelo usuario (10/09/2026): avaliar se o Gemini Flash TTS entrega
// qualidade boa o bastante pra virar opcao real no futuro. Liga/desliga em Configuracoes >
// Boletins > "Voz dos boletins" (ver RadioBulletinSettings.ttsProvider) - falha do Gemini em
// qualquer boletim real cai pro CURRENT automaticamente (ver GeminiFlashTtsEngine.kt e
// LocalTuneViewModel.synthesizeLocalVoiceSafely/synthesizeCoreVoiceSafely), nunca trava a radio.
enum class BulletinTtsProvider {
    CURRENT,
    GEMINI_FLASH,
}

// gemini-2.5-flash-preview-tts e o padrao pra quem liga essa opcao agora (pedido do usuario
// 10/09/2026: "nao quero usar o 3.1 porque ele e pesado... o 2.5 esta de bom tamanho", validado
// ao vivo via multi-speaker TTS - ver GeminiFlashTtsEngine); gemini-3.1-flash-tts-preview fica
// como alternativa selecionavel pra quem quiser testar.
enum class GeminiTtsModel(val modelId: String, val label: String) {
    GEMINI_3_1_FLASH("gemini-3.1-flash-tts-preview", "Gemini 3.1 Flash TTS"),
    GEMINI_2_5_FLASH("gemini-2.5-flash-preview-tts", "Gemini 2.5 Flash TTS"),
}

// voiceName = voz pre-pronta do Gemini TTS (speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName
// na API). stylePrompt e a instrucao de interpretacao embutida como prefixo do texto enviado (a
// API do Gemini TTS nao tem campo separado pra isso, ver GeminiFlashTtsEngine.buildStyledText) -
// ponto de partida pedido pelo usuario, centralizado aqui pra poder ajustar sem mexer no resto do
// pipeline (e futuramente virar selecionavel na UI, se o experimento for bem).
data class GeminiTtsVoiceConfig(val voiceName: String, val stylePrompt: String)

object GeminiTtsVoices {
    val FRAN = GeminiTtsVoiceConfig(
        voiceName = "Kore",
        stylePrompt = "Fale em português brasileiro de forma natural e conversacional, como " +
            "numa conversa de verdade entre amigos - sem forçar entonação nem exagerar em " +
            "subidas e descidas de tom. Voz de locutora de rádio alternativa, leve e " +
            "inteligente, mas comedida - nunca robótica nem em modo propaganda",
    )
    // Voz trocada 10/09/2026 (pedido do usuario: "Puck ficou muito fina e estranha, não combinou
    // com a personalidade dele... tem alguma mais grossa e imponente?") - Algenib e documentada
    // como a voz masculina de textura "gravelly"/grave entre as pre-prontas do Gemini TTS, contra
    // Puck (tom animado/upbeat, nada a ver com o Nico cetico e durao). Instrucao tambem suavizada
    // junto (pedido do usuario: "estão forçando muito entonações, pode ficar mais natural").
    val NICO = GeminiTtsVoiceConfig(
        voiceName = "Algenib",
        stylePrompt = "Fale em português brasileiro de forma natural e conversacional, tom " +
            "grave e firme, sem forçar entonação nem exagerar em subidas e descidas de tom. " +
            "Voz de locutor de rádio alternativo, cético e durão mas comedido - nunca robótico " +
            "nem em modo propaganda",
    )
}
