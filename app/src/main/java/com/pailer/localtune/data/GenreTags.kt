package com.pailer.localtune.data

// Separador usado pra gravar mais de uma tag de genero no MESMO campo ID3 real (FieldKey.GENRE,
// ver MusicLibraryRepository.writeTagsToAudioFile) - o app nao tem um campo separado pra "generos
// multiplos", entao a lista vira uma unica string tipo "Coldwave; Post-Punk" gravada no arquivo.
const val GENRE_TAG_SEPARATOR = "; "

private val GENRE_TAG_SPLIT_REGEX = Regex("[;/,]")

fun splitGenreTags(raw: String): List<String> {
    val seen = LinkedHashSet<String>()
    val result = mutableListOf<String>()
    raw.split(GENRE_TAG_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { tag ->
            if (seen.add(tag.lowercase())) result += tag
        }
    return result
}

fun joinGenreTags(tags: List<String>): String =
    tags.map { it.trim() }.filter { it.isNotBlank() }.joinToString(GENRE_TAG_SEPARATOR)

// Garante que toda tag comece com maiuscula (pedido explicito do usuario) sem mexer no resto do
// texto - so a primeira letra, pra nao estragar siglas ja corretas tipo "EBM"/"IDM"/"R&B"/"K-Pop"
// que uma capitalizacao por palavra (title case) deixaria erradas ("Ebm", "Idm"...).
fun capitalizeGenreTag(tag: String): String {
    val trimmed = tag.trim()
    return trimmed.replaceFirstChar { it.uppercase() }
}

// Catalogo padronizado de generos/subgeneros pra sugestao com autocomplete no editor de
// metadados (ver GenreTagField em LocalTuneApp.kt). Cobre as familias mais comuns pra nao deixar
// faltar um subgenero especifico - lista ampliada a pedido do usuario (ex.: Acid House, Lo-fi
// House, Leftfield House, Ambient Rock, entre outros que faltavam na primeira versao).
// availableGenres() em MusicLibraryRepository junta isso com os generos ja usados na biblioteca.
val GENRE_TAG_CATALOG: List<String> = listOf(
    // Rock e derivados
    "Rock", "Classic Rock", "Hard Rock", "Soft Rock", "Arena Rock", "Progressive Rock",
    "Psychedelic Rock", "Acid Rock", "Garage Rock", "Blues Rock", "Southern Rock", "Glam Rock",
    "Art Rock", "Krautrock", "Math Rock", "Post-Rock", "Space Rock", "Ambient Rock", "Surf Rock",
    "Rockabilly", "Alternative Rock", "Alternative", "Indie Rock", "College Rock", "Yacht Rock",
    "Stoner Rock", "Desert Rock", "Noise Rock", "Symphonic Rock", "Roots Rock",

    // Punk e derivados
    "Punk", "Punk Rock", "Post-Punk", "Coldwave", "Darkwave", "Deathrock", "Goth Rock",
    "Hardcore Punk", "Post-Hardcore", "Pop Punk", "Ska Punk", "Anarcho-Punk", "Garage Punk",
    "Street Punk", "Oi!", "Horror Punk", "Emo", "Screamo", "Riot Grrrl",

    // Metal e derivados
    "Metal", "Heavy Metal", "Nu Metal", "Thrash Metal", "Death Metal", "Melodic Death Metal",
    "Technical Death Metal", "Black Metal", "Atmospheric Black Metal", "Doom Metal",
    "Stoner Metal", "Sludge Metal", "Groove Metal", "Power Metal", "Progressive Metal",
    "Symphonic Metal", "Folk Metal", "Industrial Metal", "Metalcore", "Deathcore", "Grindcore",
    "Speed Metal", "Gothic Metal", "Viking Metal", "Post-Metal", "Djent",

    // Grunge / alternativo / indie
    "Grunge", "Indie", "Indie Pop", "Indie Folk", "Shoegaze", "Dream Pop", "Jangle Pop",
    "Twee Pop", "Chamber Pop", "Noise Pop", "Slowcore", "Sadcore", "Lo-fi", "Indie Psicodelico",
    "Psychedelic Pop",

    // New wave / synth
    "New Wave", "No Wave", "Synth-pop", "Synthwave", "EBM", "Industrial",

    // Eletronica - house e derivados
    "Eletronica", "House", "Deep House", "Tech House", "Acid House", "Lo-fi House",
    "Leftfield House", "Chicago House", "Electro House", "Future House", "Tribal House",
    "Vocal House", "Outsider House", "Progressive House", "UK Garage", "Speed Garage",
    "Bassline", "Grime",

    // Eletronica - techno, trance, bass e ambient
    "Techno", "Minimal Techno", "Acid Techno", "Detroit Techno", "Dub Techno", "Trance",
    "Progressive Trance", "Psytrance", "Goa Trance", "Hardstyle", "Hardcore Techno", "Gabber",
    "Drum and Bass", "Liquid Drum and Bass", "Neurofunk", "Jungle", "Dubstep", "Future Bass",
    "UK Bass", "Footwork", "Juke", "Breakbeat", "Big Beat", "Broken Beat", "IDM", "Glitch",
    "Ambient", "Ambient Techno", "Downtempo", "Trip-Hop", "Chillwave", "Vaporwave", "Witch House",
    "Electro", "Disco", "Nu-Disco", "Eurodance", "EDM", "Dance",

    // Hip-hop / rap
    "Hip-Hop", "Rap", "Trap", "Boom Bap", "Rap Nacional", "Drill", "Old School Hip-Hop",
    "Conscious Hip-Hop", "Alternative Hip-Hop", "Jazz Rap", "Gangsta Rap", "G-Funk", "Crunk",
    "Horrorcore", "Mumble Rap", "Cloud Rap",

    // Soul / funk / R&B
    "Soul", "Funk", "R&B", "Neo Soul", "Motown", "Disco Funk", "Funk Carioca", "Brega Funk",
    "Afrobeat",

    // Jazz / blues
    "Jazz", "Bebop", "Smooth Jazz", "Jazz Fusion", "Free Jazz", "Modal Jazz", "Latin Jazz",
    "Nu Jazz", "Acid Jazz", "Swing", "Cool Jazz", "Gypsy Jazz",
    "Blues", "Delta Blues", "Chicago Blues",

    // Pop
    "Pop", "Dance Pop", "Electropop", "Art Pop", "K-Pop", "City Pop", "Power Pop", "Britpop",
    "Bubblegum Pop", "Sophisti-Pop", "Hyperpop", "Bedroom Pop",

    // Brasileiros
    "MPB", "Samba", "Samba-Rock", "Bossa Nova", "Pagode", "Forro", "Axe", "Sertanejo",
    "Tropicalia", "Brega", "Frevo", "Maracatu", "Choro", "Piseiro", "Arrocha", "Baiao",
    "Carimbo",

    // Folk / country / mundo
    "Folk", "Folk Rock", "Country", "Americana", "Bluegrass", "Reggae", "Ska", "Dub", "Latin",
    "Salsa", "Cumbia", "Merengue", "Bachata", "Tango", "Flamenco", "Fado", "Celtic",
    "Gypsy Music", "Klezmer", "Balkan", "Highlife", "Soukous", "World Music",

    // Outros
    "Classical", "Opera", "Soundtrack", "Experimental", "Noise", "Spoken Word",
).sortedBy { it.lowercase() }
