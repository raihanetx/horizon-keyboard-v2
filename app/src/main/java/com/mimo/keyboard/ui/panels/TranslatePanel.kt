package com.mimo.keyboard.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.keyboard.KeyboardViewModel
import com.mimo.keyboard.KeyAction
import com.mimo.keyboard.ui.theme.HorizonColors

/**
 * Translate panel.
 * Maps to #p-translate in the HTML prototype.
 *
 * FIX: Now provides functional word-by-word translation using a built-in
 * dictionary. Previously showed a static "Result: Ready." that never changed.
 *
 * Features:
 * - Translates source text word-by-word using a local English→Spanish dictionary
 * - Language selector (EN→ES / ES→EN toggle)
 * - Tap result text to paste the translation into the input field
 * - Falls back to original word if no translation is found
 * - Auto-translates as you type (source comes from the input field)
 */
@Composable
fun TranslatePanel(
    sourceText: String,
    viewModel: KeyboardViewModel? = null,
    modifier: Modifier = Modifier
) {
    // Translation direction toggle
    var isEnToEs by remember { mutableStateOf(true) }

    // Compute translation reactively
    val translatedText = remember(sourceText, isEnToEs) {
        if (sourceText.isEmpty()) ""
        else translateText(sourceText, isEnToEs)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HorizonColors.Background)
            .padding(10.dp)
    ) {
        // ── Header with language toggle ──────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRANSLATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HorizonColors.Accent,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            // Language direction toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(HorizonColors.KeyboardSurface)
                    .border(1.dp, HorizonColors.BorderPrimary, RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { isEnToEs = !isEnToEs }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnToEs) "EN \u2192 ES" else "ES \u2192 EN",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = HorizonColors.Accent,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ── Source text box ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HorizonColors.KeyboardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, HorizonColors.BorderPrimary, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Text(
                text = if (isEnToEs) "ENGLISH" else "SPANISH",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HorizonColors.TextExtraMuted,
                letterSpacing = 0.12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sourceText.ifEmpty { "Type something to translate..." },
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = if (sourceText.isEmpty()) HorizonColors.TextMuted else HorizonColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Result box (accent border, tap to paste) ────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HorizonColors.KeyboardSurface, RoundedCornerShape(10.dp))
                .border(1.dp, HorizonColors.Accent, RoundedCornerShape(10.dp))
                .then(
                    if (translatedText.isNotEmpty()) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            // FIX: Use InsertText instead of Character for multi-char paste.
                            // KeyAction.Character applies lowercase()/uppercase() based on shift,
                            // which corrupts translation text when shift is active.
                            viewModel?.onKeyPress(KeyAction.InsertText(translatedText))
                        }
                    ) else Modifier
                )
                .padding(12.dp)
        ) {
            Text(
                text = if (isEnToEs) "SPANISH" else "ENGLISH",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HorizonColors.Accent,
                letterSpacing = 0.12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (translatedText.isEmpty()) "Waiting for input..." else translatedText,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = if (translatedText.isEmpty()) HorizonColors.TextMuted else HorizonColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Bottom tip ──────────────────────────────────────
        if (translatedText.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "TAP result to PASTE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = HorizonColors.TextExtraMuted,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

/**
 * Translates text using a local dictionary.
 *
 * BUG FIX: Previous implementation split text on whitespace first, then looked up
 * each individual word. This meant multi-word dictionary keys like "lo siento" or
 * "a lo largo" could NEVER match — they were split into "lo" + "siento" before
 * lookup, and each part was looked up individually.
 *
 * New approach: greedy multi-word matching. We scan the input from left to right,
 * trying the longest possible phrase match first (up to MAX_PHRASE_LENGTH words).
 * If a phrase matches a dictionary key, we use it and skip those words.
 * If no phrase match, we fall back to single-word lookup.
 * This correctly handles both single-word and multi-word dictionary entries.
 */
private fun translateText(text: String, isEnToEs: Boolean): String {
    val dict = if (isEnToEs) enToEsDictionary else esToEnDictionary
    val words = text.split("\\s+".toRegex())
    val result = mutableListOf<String>()
    var i = 0

    while (i < words.size) {
        // Try matching phrases of decreasing length (longest match first)
        var matched = false
        for (len in minOf(MAX_PHRASE_LENGTH, words.size - i) downTo 1) {
            // BUG FIX: Strip leading/trailing punctuation from each word before
            // joining into a phrase for dictionary lookup. Previously, words like
            // "Hello!" or "world." would never match dictionary keys because the
            // dictionary has "hello" and "world" without punctuation.
            //
            // We strip common punctuation chars (.,!?;:,'") from both ends of
            // each word, then join the stripped words for the lookup key.
            // Punctuation is preserved in the output by re-attaching it after
            // translation.
            val strippedWords = words.subList(i, i + len).map { stripPunctuation(it) }
            val phrase = strippedWords.joinToString(" ").lowercase()
            val translated = dict[phrase]
            if (translated != null) {
                // Preserve capitalization of the first word
                val firstWord = words[i]
                val isCapitalized = firstWord.isNotEmpty() && firstWord[0].isUpperCase()
                val translatedWord = when {
                    isCapitalized -> translated.replaceFirstChar { it.uppercase() }
                    else -> translated
                }
                // BUG FIX: Re-attach trailing punctuation from the LAST word
                // in the matched phrase. For example, if the user typed "Hello!"
                // and "Hello" translates to "Hola", we want "Hola!" not "Hola".
                // We only re-attach from the last word to avoid duplicating
                // punctuation that was between words in the phrase.
                val trailingPunct = extractTrailingPunctuation(words[i + len - 1])
                result.add(translatedWord + trailingPunct)
                i += len
                matched = true
                break
            }
        }

        if (!matched) {
            // No dictionary match — keep original word with its punctuation
            result.add(words[i])
            i++
        }
    }

    return result.joinToString(" ")
}

/**
 * Strips leading and trailing punctuation from a word for dictionary lookup.
 * Preserves internal punctuation (e.g., apostrophes in "don't").
 */
private fun stripPunctuation(word: String): String {
    val punctuation = setOf('.', ',', '!', '?', ';', ':', '\'', '"', '(', ')')
    var start = 0
    var end = word.length
    while (start < end && word[start] in punctuation) start++
    while (end > start && word[end - 1] in punctuation) end--
    return word.substring(start, end)
}

/**
 * Extracts trailing punctuation from a word for re-attachment after translation.
 * Returns the punctuation string (e.g., "!" from "Hello!"), or empty string if none.
 */
private fun extractTrailingPunctuation(word: String): String {
    val punctuation = setOf('.', ',', '!', '?', ';', ':', '\'', '"')
    var end = word.length
    while (end > 0 && word[end - 1] in punctuation) end--
    return word.substring(end)
}

// Maximum number of words in a multi-word dictionary key
private const val MAX_PHRASE_LENGTH = 4

// English → Spanish dictionary (common words)
private val enToEsDictionary = mapOf(
    // Articles & Pronouns
    "the" to "el", "a" to "un", "an" to "un", "this" to "este", "that" to "ese",
    "these" to "estos", "those" to "esos", "i" to "yo", "you" to "tú",
    "he" to "él", "she" to "ella", "it" to "eso", "we" to "nosotros",
    "they" to "ellos", "me" to "me", "my" to "mi", "your" to "tu",
    "his" to "su", "her" to "su", "our" to "nuestro", "their" to "su",

    // Common Verbs
    "is" to "es", "am" to "soy", "are" to "son", "was" to "fue", "were" to "fueron",
    "be" to "ser", "have" to "tener", "has" to "tiene", "had" to "tuvo",
    "do" to "hacer", "does" to "hace", "did" to "hizo",
    "will" to "futuro", "would" to "condicional", "can" to "poder", "could" to "podría",
    "should" to "debería", "may" to "puede", "might" to "podría",
    "go" to "ir", "going" to "yendo", "went" to "fue", "gone" to "ido",
    "come" to "venir", "came" to "vino", "coming" to "viniendo",
    "get" to "obtener", "got" to "obtuvo", "getting" to "obteniendo",
    "make" to "hacer", "made" to "hecho", "making" to "haciendo",
    "take" to "tomar", "took" to "tomó", "taken" to "tomado",
    "give" to "dar", "gave" to "dio", "given" to "dado",
    "say" to "decir", "said" to "dijo", "saying" to "diciendo",
    "know" to "saber", "knew" to "supo", "known" to "conocido",
    "think" to "pensar", "thought" to "pensó", "thinking" to "pensando",
    "see" to "ver", "saw" to "vio", "seen" to "visto",
    "want" to "querer", "wanted" to "quiso", "wanting" to "queriendo",
    "need" to "necesitar", "needed" to "necesitó",
    "like" to "gustar", "liked" to "gustó", "liking" to "gustando",
    "love" to "amar", "loved" to "amó", "loving" to "amando",
    "help" to "ayudar", "helped" to "ayudó", "helping" to "ayudando",
    "use" to "usar", "used" to "usado", "using" to "usando",
    "find" to "encontrar", "found" to "encontró", "finding" to "encontrando",
    "tell" to "decir", "told" to "dijo", "telling" to "diciendo",
    "ask" to "preguntar", "asked" to "preguntó", "asking" to "preguntando",
    "work" to "trabajar", "worked" to "trabajó", "working" to "trabajando",
    "call" to "llamar", "called" to "llamó", "calling" to "llamando",
    "try" to "intentar", "tried" to "intentó", "trying" to "intentando",
    "keep" to "mantener", "let" to "dejar",
    "begin" to "comenzar", "show" to "mostrar", "hear" to "oír",
    "play" to "jugar", "run" to "correr", "move" to "mover",
    "live" to "vivir", "believe" to "creer", "hold" to "sostener",
    "bring" to "traer", "happen" to "suceder", "write" to "escribir",
    "provide" to "proporcionar", "sit" to "sentar", "stand" to "estar",
    "lose" to "perder", "pay" to "pagar", "meet" to "conocer",
    "include" to "incluir", "continue" to "continuar", "set" to "establecer",
    "learn" to "aprender", "change" to "cambiar", "lead" to "liderar",
    "understand" to "entender", "watch" to "mirar", "follow" to "seguir",
    "stop" to "parar", "create" to "crear", "speak" to "hablar",
    "read" to "leer", "spend" to "gastar", "grow" to "crecer",
    "open" to "abrir", "walk" to "caminar", "win" to "ganar",
    "teach" to "enseñar", "offer" to "ofrecer", "remember" to "recordar",
    "consider" to "considerar", "appear" to "aparecer", "buy" to "comprar",
    "wait" to "esperar", "serve" to "servir", "die" to "morir",
    "send" to "enviar", "expect" to "esperar", "build" to "construir",
    "stay" to "quedarse", "fall" to "caer", "cut" to "cortar",
    "reach" to "alcanzar", "kill" to "matar", "remain" to "permanecer",
    "suggest" to "sugerir", "raise" to "levantar", "pass" to "pasar",
    "sell" to "vender", "require" to "requerir", "report" to "reportar",
    "decide" to "decidir", "pull" to "tirar", "develop" to "desarrollar",
    "eat" to "comer", "ate" to "comió", "eating" to "comiendo",
    "drink" to "beber", "drank" to "bebió", "drinking" to "bebiendo",
    "sleep" to "dormir", "slept" to "durmió", "sleeping" to "durmiendo",

    // Common Nouns
    "hello" to "hola", "goodbye" to "adiós", "please" to "por favor",
    "thanks" to "gracias", "thank" to "gracia", "sorry" to "lo siento",
    "yes" to "sí", "no" to "no", "maybe" to "quizás", "not" to "no",
    "time" to "tiempo", "day" to "día", "night" to "noche",
    "morning" to "mañana", "afternoon" to "tarde", "evening" to "noche",
    "today" to "hoy", "tomorrow" to "mañana", "yesterday" to "ayer",
    "week" to "semana", "month" to "mes", "year" to "año",
    "man" to "hombre", "woman" to "mujer", "child" to "niño",
    "children" to "niños", "people" to "gente", "person" to "persona",
    "world" to "mundo", "country" to "país", "city" to "ciudad",
    "house" to "casa", "home" to "hogar", "room" to "habitación",
    "door" to "puerta", "window" to "ventana", "table" to "mesa",
    "chair" to "silla", "bed" to "cama", "car" to "coche",
    "book" to "libro", "water" to "agua", "food" to "comida",
    "hand" to "mano", "head" to "cabeza", "eye" to "ojo",
    "face" to "cara", "heart" to "corazón", "life" to "vida",
    "name" to "nombre", "word" to "palabra", "thing" to "cosa",
    "place" to "lugar", "point" to "punto", "part" to "parte",
    "problem" to "problema", "question" to "pregunta", "answer" to "respuesta",
    "idea" to "idea", "story" to "historia", "fact" to "hecho",
    "number" to "número", "group" to "grupo", "party" to "fiesta",
    "family" to "familia", "friend" to "amigo", "school" to "escuela",
    "student" to "estudiante", "teacher" to "profesor", "doctor" to "doctor",
    // FIX: Removed duplicate "work" → "trabajo" (nouns section) which overwrote
    // "work" → "trabajar" (verb). The verb form is more useful for translation.
    "job" to "trabajo", "money" to "dinero",
    "price" to "precio", "market" to "mercado", "business" to "negocio",
    "game" to "juego", "music" to "música", "movie" to "película",
    "phone" to "teléfono", "computer" to "computadora", "internet" to "internet",
    "coffee" to "café", "tea" to "té", "beer" to "cerveza",
    "dog" to "perro", "cat" to "gato", "bird" to "pájaro",
    "sun" to "sol", "moon" to "luna", "star" to "estrella",
    "rain" to "lluvia", "snow" to "nieve", "wind" to "viento",
    "fire" to "fuego", "earth" to "tierra", "sea" to "mar",
    "color" to "color", "red" to "rojo", "blue" to "azul",
    "green" to "verde", "yellow" to "amarillo", "white" to "blanco",
    "black" to "negro", "orange" to "naranja", "pink" to "rosa",

    // Common Adjectives
    "good" to "bueno", "bad" to "malo", "big" to "grande", "small" to "pequeño",
    "long" to "largo", "short" to "corto", "new" to "nuevo", "old" to "viejo",
    "high" to "alto", "low" to "bajo", "hot" to "caliente", "cold" to "frío",
    "fast" to "rápido", "slow" to "lento", "hard" to "duro", "soft" to "suave",
    "easy" to "fácil", "difficult" to "difícil", "important" to "importante",
    "possible" to "posible", "impossible" to "imposible", "real" to "real",
    "true" to "verdadero", "false" to "falso", "right" to "correcto",
    "wrong" to "equivocado", "happy" to "feliz", "sad" to "triste",
    "beautiful" to "hermoso", "ugly" to "feo", "nice" to "bonito",
    "great" to "genial", "terrible" to "terrible", "wonderful" to "maravilloso",
    "amazing" to "increíble", "perfect" to "perfecto", "simple" to "simple",
    "different" to "diferente", "same" to "mismo", "other" to "otro",
    "next" to "siguiente", "last" to "último", "first" to "primero",
    "all" to "todo", "many" to "muchos", "some" to "algunos",
    "few" to "pocos", "every" to "cada", "each" to "cada",
    "much" to "mucho", "little" to "poco", "more" to "más",
    "less" to "menos", "very" to "muy", "too" to "demasiado",
    "also" to "también", "only" to "solo", "just" to "solo",
    "still" to "todavía", "already" to "ya", "always" to "siempre",
    "never" to "nunca", "sometimes" to "a veces", "often" to "a menudo",
    "here" to "aquí", "there" to "allí", "where" to "dónde",
    "when" to "cuándo", "how" to "cómo", "why" to "por qué",
    "what" to "qué", "who" to "quién", "which" to "cuál",
    "and" to "y", "but" to "pero", "or" to "o", "if" to "si",
    "because" to "porque", "so" to "entonces", "than" to "que",
    "then" to "entonces", "now" to "ahora", "well" to "bien",
    "with" to "con", "without" to "sin", "about" to "sobre",
    "for" to "por", "from" to "de", "into" to "en",
    "through" to "a través", "during" to "durante", "before" to "antes",
    "after" to "después", "between" to "entre", "under" to "debajo",
    "above" to "arriba", "until" to "hasta", "against" to "contra",
    "along" to "a lo largo", "upon" to "sobre", "around" to "alrededor",
    "across" to "a través", "behind" to "detrás", "beside" to "al lado",
    "down" to "abajo", "up" to "arriba", "out" to "fuera",
    "off" to "fuera", "on" to "en", "in" to "en", "at" to "en",
    "to" to "a", "of" to "de"
)

// Spanish → English dictionary.
// FIX: Previously auto-generated from enToEsDictionary via `associate`, which
// silently overwrote duplicate keys. ~20+ ES words mapped to the same translation
// (e.g., "su" from "his"/"her"/"their", "fue" from "was"/"went", "de" from "of"/"from",
// "en" from "in"/"on"/"into"/"at", "noche" from "night"/"evening", etc.).
// With `associate`, only the LAST mapping survived — most ES→EN lookups were wrong.
//
// Now manually curated with disambiguation. For collisions, we pick the most
// common/primary translation and add parenthetical context for alternatives.
private val esToEnDictionary = mapOf(
    // Articles & Pronouns
    "el" to "the", "un" to "a", "este" to "this", "ese" to "that",
    "estos" to "these", "esos" to "those", "yo" to "I", "tú" to "you",
    "él" to "he", "ella" to "she", "eso" to "it", "nosotros" to "we",
    "ellos" to "they", "me" to "me", "mi" to "my", "tu" to "your",
    "su" to "his/her/their", "nuestro" to "our",

    // Common Verbs
    "es" to "is", "soy" to "am", "son" to "are", "fue" to "was/went",
    "fueron" to "were", "ser" to "be", "tener" to "have", "tiene" to "has",
    "tuvo" to "had", "hacer" to "do/make", "hace" to "does", "hizo" to "did",
    "poder" to "can", "podría" to "could/might", "debería" to "should",
    "puede" to "may", "ir" to "go", "yendo" to "going", "ido" to "gone",
    "venir" to "come", "vino" to "came", "viniendo" to "coming",
    "obtener" to "get", "obtuvo" to "got", "obteniendo" to "getting",
    "hecho" to "made/done", "haciendo" to "making/doing",
    "tomar" to "take", "tomó" to "took", "tomado" to "taken",
    "dar" to "give", "dio" to "gave", "dado" to "given",
    "decir" to "say/tell", "dijo" to "said/told", "diciendo" to "saying/telling",
    "saber" to "know", "supo" to "knew", "conocido" to "known",
    "pensar" to "think", "pensó" to "thought", "pensando" to "thinking",
    "ver" to "see", "vio" to "saw", "visto" to "seen",
    "querer" to "want", "quiso" to "wanted", "queriendo" to "wanting",
    "necesitar" to "need", "necesitó" to "needed",
    "gustar" to "like", "gustó" to "liked", "gustando" to "liking",
    "amar" to "love", "amó" to "loved", "amando" to "loving",
    "ayudar" to "help", "ayudó" to "helped", "ayudando" to "helping",
    "usar" to "use", "usado" to "used", "usando" to "using",
    "encontrar" to "find", "encontró" to "found", "encontrando" to "finding",
    "preguntar" to "ask", "preguntó" to "asked", "preguntando" to "asking",
    "trabajar" to "work", "trabajó" to "worked", "trabajando" to "working",
    "llamar" to "call", "llamó" to "called", "llamando" to "calling",
    "intentar" to "try", "intentó" to "tried", "intentando" to "trying",
    "mantener" to "keep", "dejar" to "let",
    "comenzar" to "begin", "mostrar" to "show", "oír" to "hear",
    "jugar" to "play", "correr" to "run", "mover" to "move",
    "vivir" to "live", "creer" to "believe", "sostener" to "hold",
    "traer" to "bring", "suceder" to "happen", "escribir" to "write",
    "proporcionar" to "provide", "sentar" to "sit", "estar" to "be/stand",
    "perder" to "lose", "pagar" to "pay", "conocer" to "meet/know",
    "incluir" to "include", "continuar" to "continue", "establecer" to "set",
    "aprender" to "learn", "cambiar" to "change", "liderar" to "lead",
    "entender" to "understand", "mirar" to "watch", "seguir" to "follow",
    "parar" to "stop", "crear" to "create", "hablar" to "speak",
    "leer" to "read", "gastar" to "spend", "crecer" to "grow",
    "abrir" to "open", "caminar" to "walk", "ganar" to "win",
    "enseñar" to "teach", "ofrecer" to "offer", "recordar" to "remember",
    "considerar" to "consider", "aparecer" to "appear", "comprar" to "buy",
    "esperar" to "wait/expect", "servir" to "serve", "morir" to "die",
    "enviar" to "send", "requerir" to "require", "reportar" to "report",
    "decidir" to "decide", "tirar" to "pull", "desarrollar" to "develop",
    "comer" to "eat", "comió" to "ate", "comiendo" to "eating",
    "beber" to "drink", "bebió" to "drank", "bebiendo" to "drinking",
    "dormir" to "sleep", "durmió" to "slept", "durmiendo" to "sleeping",

    // Common Nouns
    "hola" to "hello", "adiós" to "goodbye",
    "gracias" to "thanks", "gracia" to "thank",
    "lo siento" to "sorry", "sí" to "yes", "no" to "no/not", "quizás" to "maybe",
    "tiempo" to "time", "día" to "day", "noche" to "night",
    "mañana" to "morning/tomorrow", "tarde" to "afternoon",
    "hoy" to "today", "ayer" to "yesterday",
    "semana" to "week", "mes" to "month", "año" to "year",
    "hombre" to "man", "mujer" to "woman", "niño" to "child",
    "niños" to "children", "gente" to "people", "persona" to "person",
    "mundo" to "world", "país" to "country", "ciudad" to "city",
    "casa" to "house", "hogar" to "home", "habitación" to "room",
    "puerta" to "door", "ventana" to "window", "mesa" to "table",
    "silla" to "chair", "cama" to "bed", "coche" to "car",
    "libro" to "book", "agua" to "water", "comida" to "food",
    "mano" to "hand", "cabeza" to "head", "ojo" to "eye",
    "cara" to "face", "corazón" to "heart", "vida" to "life",
    "nombre" to "name", "palabra" to "word", "cosa" to "thing",
    "lugar" to "place", "punto" to "point", "parte" to "part",
    "problema" to "problem", "pregunta" to "question", "respuesta" to "answer",
    "idea" to "idea", "historia" to "story", "hecho" to "fact",
    "número" to "number", "grupo" to "group", "fiesta" to "party",
    "familia" to "family", "amigo" to "friend", "escuela" to "school",
    "estudiante" to "student", "profesor" to "teacher", "doctor" to "doctor",
    "trabajo" to "job/work", "dinero" to "money",
    "precio" to "price", "mercado" to "market", "negocio" to "business",
    "juego" to "game", "música" to "music", "película" to "movie",
    "teléfono" to "phone", "computadora" to "computer", "internet" to "internet",
    "café" to "coffee", "té" to "tea", "cerveza" to "beer",
    "perro" to "dog", "gato" to "cat", "pájaro" to "bird",
    "sol" to "sun", "luna" to "moon", "estrella" to "star",
    "lluvia" to "rain", "nieve" to "snow", "viento" to "wind",
    "fuego" to "fire", "tierra" to "earth/land", "mar" to "sea",
    "color" to "color", "rojo" to "red", "azul" to "blue",
    "verde" to "green", "amarillo" to "yellow", "blanco" to "white",
    "negro" to "black", "naranja" to "orange", "rosa" to "pink",

    // Common Adjectives
    "bueno" to "good", "malo" to "bad", "grande" to "big", "pequeño" to "small",
    "largo" to "long", "corto" to "short", "nuevo" to "new", "viejo" to "old",
    "alto" to "high/tall", "bajo" to "low", "caliente" to "hot", "frío" to "cold",
    "rápido" to "fast", "lento" to "slow", "duro" to "hard", "suave" to "soft",
    "fácil" to "easy", "difícil" to "difficult", "importante" to "important",
    "posible" to "possible", "imposible" to "impossible", "real" to "real",
    "verdadero" to "true", "falso" to "false", "correcto" to "right",
    "equivocado" to "wrong", "feliz" to "happy", "triste" to "sad",
    "hermoso" to "beautiful", "feo" to "ugly", "bonito" to "nice",
    "genial" to "great", "terrible" to "terrible", "maravilloso" to "wonderful",
    "increíble" to "amazing", "perfecto" to "perfect", "simple" to "simple",
    "diferente" to "different", "mismo" to "same", "otro" to "other",
    "siguiente" to "next", "último" to "last", "primero" to "first",
    "todo" to "all", "muchos" to "many", "algunos" to "some",
    "pocos" to "few", "cada" to "each/every",
    "mucho" to "much/many", "poco" to "little/few", "más" to "more",
    "menos" to "less", "muy" to "very", "demasiado" to "too",
    "también" to "also", "solo" to "only/just",
    "todavía" to "still", "ya" to "already", "siempre" to "always",
    "nunca" to "never", "a veces" to "sometimes", "a menudo" to "often",
    "aquí" to "here", "allí" to "there", "dónde" to "where",
    "cuándo" to "when", "cómo" to "how", "por qué" to "why",
    "qué" to "what", "quién" to "who", "cuál" to "which",
    "y" to "and", "pero" to "but", "o" to "or", "si" to "if",
    "porque" to "because", "entonces" to "so/then", "que" to "than/that",
    "ahora" to "now", "bien" to "well",
    "con" to "with", "sin" to "without", "sobre" to "about/on",
    "por" to "for/by", "de" to "of/from", "en" to "in/on/at",
    "a través" to "through/across", "durante" to "during", "antes" to "before",
    "después" to "after", "entre" to "between", "debajo" to "under",
    "arriba" to "above/up", "hasta" to "until", "contra" to "against",
    "a lo largo" to "along", "alrededor" to "around", "detrás" to "behind",
    "al lado" to "beside", "abajo" to "down", "fuera" to "out/off",
    "a" to "to"
)
