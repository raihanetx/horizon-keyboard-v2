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
                            viewModel?.onKeyPress(KeyAction.Character(translatedText))
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
 * Translates text word-by-word using a local dictionary.
 * Falls back to the original word if no translation is found.
 */
private fun translateText(text: String, isEnToEs: Boolean): String {
    val dict = if (isEnToEs) enToEsDictionary else esToEnDictionary
    return text.split("\\s+".toRegex())
        .joinToString(" ") { word ->
            val lower = word.lowercase()
            val translated = dict[lower]
            // Preserve capitalization: if original starts with uppercase, capitalize result
            when {
                translated != null && word.isNotEmpty() && word[0].isUpperCase() ->
                    translated.replaceFirstChar { it.uppercase() }
                translated != null -> translated
                else -> word  // No translation found, keep original
            }
        }
}

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
    "will" to "will", "would" to "would", "can" to "poder", "could" to "podría",
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
    "ask" to "preguntar", "keep" to "mantener", "let" to "dejar",
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
    "work" to "trabajo", "job" to "trabajo", "money" to "dinero",
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

// Spanish → English dictionary (reverse of the above)
private val esToEnDictionary = enToEsDictionary.entries.associate { (en, es) ->
    es.lowercase() to en
}
