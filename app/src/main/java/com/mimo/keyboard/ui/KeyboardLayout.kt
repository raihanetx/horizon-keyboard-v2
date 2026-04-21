package com.mimo.keyboard.ui

import com.mimo.keyboard.KeyAction

/**
 * Defines the complete QWERTY keyboard layout.
 * Maps directly to the HTML prototype's keyboard rows.
 *
 * Row 1: q w e r t y u i o p
 * Row 2: a s d f g h j k l
 * Row 3: ⇧ z x c v b n m ⌫
 * Row 4: 123  @  SPACE  .  DONE
 *
 * FIX: Vowels now have long-press accent alternatives.
 * For example, long-pressing 'e' shows: é, è, ë, ê
 * FIX: Number row 2 now has 10 keys (added "%") for alignment.
 */
object KeyboardLayout {

    val qwertyRows: List<List<KeyDef>> = listOf(
        // Row 1: Top letter row (10 keys)
        // FIX: Vowels have accent alternatives on long-press
        listOf(
            KeyDef("q", KeyAction.Character("q")),
            KeyDef("w", KeyAction.Character("w"),
                alternatives = listOf("ñ" to KeyAction.Character("ñ"))),
            KeyDef("e", KeyAction.Character("e"),
                alternatives = listOf("é" to KeyAction.Character("é"), "è" to KeyAction.Character("è"), "ê" to KeyAction.Character("ê"), "ë" to KeyAction.Character("ë"))),
            KeyDef("r", KeyAction.Character("r")),
            KeyDef("t", KeyAction.Character("t")),
            KeyDef("y", KeyAction.Character("y"),
                alternatives = listOf("ý" to KeyAction.Character("ý"), "ÿ" to KeyAction.Character("ÿ"))),
            KeyDef("u", KeyAction.Character("u"),
                alternatives = listOf("ú" to KeyAction.Character("ú"), "ù" to KeyAction.Character("ù"), "û" to KeyAction.Character("û"), "ü" to KeyAction.Character("ü"))),
            KeyDef("i", KeyAction.Character("i"),
                alternatives = listOf("í" to KeyAction.Character("í"), "ì" to KeyAction.Character("ì"), "î" to KeyAction.Character("î"), "ï" to KeyAction.Character("ï"))),
            KeyDef("o", KeyAction.Character("o"),
                alternatives = listOf("ó" to KeyAction.Character("ó"), "ò" to KeyAction.Character("ò"), "ô" to KeyAction.Character("ô"), "ö" to KeyAction.Character("ö"))),
            KeyDef("p", KeyAction.Character("p"))
        ),
        // Row 2: Middle letter row (9 keys)
        // FIX: Vowel 'a' has accent alternatives on long-press
        listOf(
            KeyDef("a", KeyAction.Character("a"),
                alternatives = listOf("á" to KeyAction.Character("á"), "à" to KeyAction.Character("à"), "â" to KeyAction.Character("â"), "ä" to KeyAction.Character("ä"))),
            KeyDef("s", KeyAction.Character("s"),
                alternatives = listOf("ß" to KeyAction.Character("ß"))),
            KeyDef("d", KeyAction.Character("d")),
            KeyDef("f", KeyAction.Character("f")),
            KeyDef("g", KeyAction.Character("g")),
            KeyDef("h", KeyAction.Character("h")),
            KeyDef("j", KeyAction.Character("j")),
            KeyDef("k", KeyAction.Character("k")),
            KeyDef("l", KeyAction.Character("l"))
        ),
        // Row 3: Bottom letter row with shift & backspace
        // Shift (flex:1.4), 7 letters, Backspace (flex:1.4)
        listOf(
            KeyDef("⇧", KeyAction.Shift, KeyStyle.SPECIAL, 1.4f),
            KeyDef("z", KeyAction.Character("z")),
            KeyDef("x", KeyAction.Character("x")),
            KeyDef("c", KeyAction.Character("c"),
                alternatives = listOf("ç" to KeyAction.Character("ç"))),
            KeyDef("v", KeyAction.Character("v")),
            KeyDef("b", KeyAction.Character("b")),
            KeyDef("n", KeyAction.Character("n"),
                alternatives = listOf("ñ" to KeyAction.Character("ñ"))),
            KeyDef("m", KeyAction.Character("m")),
            KeyDef("⌫", KeyAction.Backspace, KeyStyle.BACKSPACE, 1.4f)
        ),
        // Row 4: Bottom utility row
        // 123 (flex:1.4), @ (flex:1), SPACE (flex:5), . (flex:1), DONE (flex:2)
        listOf(
            KeyDef("123", KeyAction.NumberToggle, KeyStyle.SPECIAL, 1.4f),
            KeyDef("@", KeyAction.Character("@"), KeyStyle.NORMAL, 1f),
            KeyDef("", KeyAction.Space, KeyStyle.SPACE, 5f),
            KeyDef(".", KeyAction.Character("."), KeyStyle.NORMAL, 1f,
                alternatives = listOf("," to KeyAction.Character(","), "!" to KeyAction.Character("!"), "?" to KeyAction.Character("?"))),
            KeyDef("DONE", KeyAction.Done, KeyStyle.ENTER, 2f)
        )
    )

    /**
     * Number/symbol layer (accessed via 123 key).
     * FIX: Row 2 now includes "%" as the 10th key to visually align.
     */
    val numberRows: List<List<KeyDef>> = listOf(
        // Row 1: Numbers (10 keys)
        listOf(
            KeyDef("1", KeyAction.Character("1")),
            KeyDef("2", KeyAction.Character("2")),
            KeyDef("3", KeyAction.Character("3")),
            KeyDef("4", KeyAction.Character("4")),
            KeyDef("5", KeyAction.Character("5")),
            KeyDef("6", KeyAction.Character("6")),
            KeyDef("7", KeyAction.Character("7")),
            KeyDef("8", KeyAction.Character("8")),
            KeyDef("9", KeyAction.Character("9")),
            KeyDef("0", KeyAction.Character("0"))
        ),
        // Row 2: Symbols (10 keys)
        listOf(
            KeyDef("-", KeyAction.Character("-")),
            KeyDef("/", KeyAction.Character("/")),
            KeyDef(":", KeyAction.Character(":")),
            KeyDef(";", KeyAction.Character(";")),
            KeyDef("(", KeyAction.Character("(")),
            KeyDef(")", KeyAction.Character(")")),
            KeyDef("$", KeyAction.Character("$")),
            KeyDef("&", KeyAction.Character("&")),
            KeyDef("\"", KeyAction.Character("\"")),
            KeyDef("%", KeyAction.Character("%"))
        ),
        // Row 3: More symbols with shift & backspace
        listOf(
            KeyDef("⇧", KeyAction.Shift, KeyStyle.SPECIAL, 1.4f),
            KeyDef(".", KeyAction.Character(".")),
            KeyDef(",", KeyAction.Character(",")),
            KeyDef("?", KeyAction.Character("?")),
            KeyDef("!", KeyAction.Character("!")),
            KeyDef("'", KeyAction.Character("'")),
            KeyDef("_", KeyAction.Character("_")),
            KeyDef("+", KeyAction.Character("+")),
            KeyDef("⌫", KeyAction.Backspace, KeyStyle.BACKSPACE, 1.4f)
        ),
        // Row 4: Bottom utility row
        listOf(
            KeyDef("ABC", KeyAction.NumberToggle, KeyStyle.SPECIAL, 1.4f),
            KeyDef("@", KeyAction.Character("@"), KeyStyle.NORMAL, 1f),
            KeyDef("", KeyAction.Space, KeyStyle.SPACE, 5f),
            KeyDef(",", KeyAction.Character(","), KeyStyle.NORMAL, 1f),
            KeyDef("DONE", KeyAction.Done, KeyStyle.ENTER, 2f)
        )
    )
}
