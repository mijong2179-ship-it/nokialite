package kr.tntlabs.featurephone

// ── 천지인 한글 오토마타 ─────────────────────────────
private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

private val DOUBLE = mapOf('ㄱ' to 'ㄲ', 'ㄷ' to 'ㄸ', 'ㅂ' to 'ㅃ', 'ㅅ' to 'ㅆ', 'ㅈ' to 'ㅉ')
private val JONG_COMPOUND = mapOf(
    "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ',
    "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ'
)
private val JONG_SPLIT = JONG_COMPOUND.entries.associate { (k, v) -> v to k }

// 천지인 모음 전이: (현재 모음 상태, 입력 요소) → 새 상태. '·'=ㆍ, ':'=ㆍㆍ
private val VOWEL = mapOf(
    "" to mapOf('ㅣ' to "ㅣ", '·' to "·", 'ㅡ' to "ㅡ"),
    "ㅣ" to mapOf('·' to "ㅏ"),
    "ㅏ" to mapOf('·' to "ㅑ", 'ㅣ' to "ㅐ"),
    "ㅑ" to mapOf('ㅣ' to "ㅒ"),
    "·" to mapOf('ㅣ' to "ㅓ", '·' to ":", 'ㅡ' to "ㅗ"),
    ":" to mapOf('ㅣ' to "ㅕ", 'ㅡ' to "ㅛ"),
    "ㅓ" to mapOf('ㅣ' to "ㅔ"),
    "ㅕ" to mapOf('ㅣ' to "ㅖ"),
    "ㅗ" to mapOf('ㅣ' to "ㅚ"),
    "ㅚ" to mapOf('·' to "ㅘ"),
    "ㅘ" to mapOf('ㅣ' to "ㅙ"),
    "ㅡ" to mapOf('·' to "ㅜ", 'ㅣ' to "ㅢ"),
    "ㅜ" to mapOf('·' to "ㅠ", 'ㅣ' to "ㅟ"),
    "ㅠ" to mapOf('ㅣ' to "ㅝ"),
    "ㅝ" to mapOf('ㅣ' to "ㅞ")
)

class HangulComposer {
    var cho: Char? = null
    var jung: String = ""      // 전이 상태 (완성 모음 또는 ·/:)
    var jong: Char? = null

    val composing: Boolean get() = cho != null || jung.isNotEmpty() || jong != null

    fun current(): String {
        val c = cho; val j = jung; val g = jong
        val jungIdx = if (j.length == 1) JUNG.indexOf(j[0]) else -1
        if (c != null && jungIdx >= 0) {
            val jongIdx = g?.let { JONG.indexOf(it) }?.coerceAtLeast(0) ?: 0
            return ((0xAC00 + (CHO.indexOf(c) * 21 + jungIdx) * 28 + jongIdx).toChar()).toString()
        }
        val sb = StringBuilder()
        c?.let { sb.append(it) }
        if (j.isNotEmpty()) sb.append(if (j == "·") "ㆍ" else if (j == ":") "ㆍㆍ" else j)
        g?.let { sb.append(it) }
        return sb.toString()
    }

    fun clear() { cho = null; jung = ""; jong = null }

    fun snapshot() = Triple(cho, jung, jong)
    fun restore(s: Triple<Char?, String, Char?>) { cho = s.first; jung = s.second; jong = s.third }

    /** 자음 입력. 반환: 커밋된 문자열 */
    fun consonant(c: Char): String {
        var out = ""
        when {
            cho == null && jung.isEmpty() -> cho = c
            cho != null && jung.isEmpty() -> { out = current(); clear(); cho = c }
            jung.length == 1 && JUNG.indexOf(jung[0]) >= 0 && jong == null && JONG.indexOf(c) > 0 -> jong = c
            jong != null && JONG_COMPOUND[jong.toString() + c] != null -> jong = JONG_COMPOUND[jong.toString() + c]
            else -> { out = current(); clear(); cho = c }
        }
        return out
    }

    /** 모음 요소(ㅣ · ㅡ) 입력. 반환: 커밋된 문자열 */
    fun vowel(e: Char): String {
        var out = ""
        val next = VOWEL[jung]?.get(e)
        if (next != null && jong == null) { jung = next; return "" }
        if (jong != null) {
            // 받침을 다음 글자 초성으로 넘김
            val g = jong!!
            val split = JONG_SPLIT[g]
            val carry: Char
            if (split != null) { jong = split[0]; carry = split[1] } else { jong = null; carry = g }
            out = current(); clear(); cho = carry
            jung = VOWEL[""]!![e]!!
            return out
        }
        // 전이 불가: 현재 글자 커밋 후 새 모음 시작
        if (composing) { out = current(); clear() }
        jung = VOWEL[""]!![e]!!
        return out
    }

    fun doubleLast(): Boolean {
        jong?.let { g -> DOUBLE[g]?.let { jong = it; return true }; return false }
        cho?.let { c -> if (jung.isEmpty() || true) DOUBLE[c]?.let { cho = it; return true } }
        return false
    }

    /** 마지막 요소 삭제. 반환: 삭제할 게 없었으면 false */
    fun backspace(): Boolean {
        when {
            jong != null -> { val s = JONG_SPLIT[jong!!]; jong = s?.get(0) }
            jung.isNotEmpty() -> jung = ""
            cho != null -> cho = null
            else -> return false
        }
        return true
    }

    fun flush(): String { val s = current(); clear(); return s }
}

// ── 키패드 문자 입력 (한글/영문/숫자) ───────────────
enum class InputMode { KO, EN, NUM }

class MultitapState {
    var committed: String = ""
    var mode: InputMode = InputMode.KO
    var upper: Boolean = false
    private val hangul = HangulComposer()
    private var lastKey: Char? = null
    private var lastIdx = 0
    private var lastAt = 0L
    private var beforeLast: Triple<Char?, String, Char?>? = null
    private var beforeLastCommitted = ""

    val text: String get() = committed + hangul.current()
    val modeLabel: String get() = when (mode) { InputMode.KO -> "한글"; InputMode.EN -> if (upper) "ABC" else "abc"; InputMode.NUM -> "123" }

    private val ko = mapOf(
        '4' to "ㄱㅋ", '5' to "ㄴㄹ", '6' to "ㄷㅌ", '7' to "ㅂㅍ", '8' to "ㅅㅎ", '9' to "ㅈㅊ", '0' to "ㅇㅁ"
    )
    private val en = mapOf(
        '1' to ".,?!'-@/:", '2' to "abc2", '3' to "def3", '4' to "ghi4", '5' to "jkl5",
        '6' to "mno6", '7' to "pqrs7", '8' to "tuv8", '9' to "wxyz9", '0' to " 0"
    )

    fun setText(s: String) { committed = s; hangul.clear(); lastKey = null }

    fun press(c: Char) {
        val now = System.currentTimeMillis()
        when (mode) {
            InputMode.NUM -> { hangul.flush().let { committed += it }; committed += c; lastKey = null }
            InputMode.EN -> {
                hangul.flush().let { committed += it }
                val chars = en[c] ?: return
                if (c == lastKey && now - lastAt < 900 && committed.isNotEmpty()) {
                    lastIdx = (lastIdx + 1) % chars.length
                    committed = committed.dropLast(1) + shape(chars[lastIdx])
                } else { lastIdx = 0; committed += shape(chars[0]) }
            }
            InputMode.KO -> {
                val elem = when (c) { '1' -> 'ㅣ'; '2' -> '·'; '3' -> 'ㅡ'; else -> null }
                if (elem != null) {
                    committed += hangul.vowel(elem); lastKey = null; return
                }
                val chars = ko[c] ?: return
                if (c == lastKey && now - lastAt < 900 && beforeLast != null) {
                    lastIdx = (lastIdx + 1) % chars.length
                    hangul.restore(beforeLast!!); committed = beforeLastCommitted
                } else { lastIdx = 0; beforeLast = hangul.snapshot(); beforeLastCommitted = committed }
                committed += hangul.consonant(chars[lastIdx])
            }
        }
        lastKey = c; lastAt = now
    }

    fun doubleConsonant() { if (mode == InputMode.KO) hangul.doubleLast(); lastKey = null }
    fun toggleCase() { upper = !upper; lastKey = null }
    fun toggleMode() {
        committed += hangul.flush()
        mode = when (mode) { InputMode.KO -> InputMode.EN; InputMode.EN -> InputMode.NUM; InputMode.NUM -> InputMode.KO }
        lastKey = null
    }
    fun space() { committed += hangul.flush() + " "; lastKey = null }
    fun backspace() {
        if (!hangul.backspace()) committed = committed.dropLast(1)
        lastKey = null
    }
    fun commit() { committed += hangul.flush(); lastKey = null }
    private fun shape(ch: Char) = if (upper) ch.uppercaseChar() else ch
}
