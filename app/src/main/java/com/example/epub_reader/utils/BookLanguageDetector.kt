package com.example.epub_reader.utils

import com.example.epub_reader.epub.EpubBook
import java.text.Normalizer
import java.util.Locale

/**
 * Guesses the language a book is written in, to pick the text-to-speech voice:
 *  1. writing system of the text (Thai, Chinese, Japanese, Korean, Cyrillic, Arabic...)
 *  2. Latin script: Vietnamese diacritics, then the most frequent common words (en, fr, de, es...)
 *  3. language written in the book (<dc:language>, xml:lang) when the text is not conclusive
 *
 * Returns a [TtsLanguages] code ("vi", "en", "zh-CN", "zh-TW", "th"...) or null.
 * Works on a small sample of the book (a few thousand characters), safe to run on a background thread.
 */
object BookLanguageDetector {

    private class TextGuess(
        val code: String,
        val confident: Boolean,
        /** Chinese text without any character that tells simplified from traditional */
        val chineseVariantUnknown: Boolean = false
    )

    fun detect(book: EpubBook): String? {
        val guess = detectText(sampleText(book))
        val declared = normalizeTag(book.language) ?: htmlLanguage(book)
        return when {
            guess != null && guess.confident ->
                if (guess.chineseVariantUnknown && declared == "zh-TW") "zh-TW" else guess.code
            declared != null -> declared
            else -> guess?.code
        }
    }

    // region Sample

    private const val SAMPLED_CHAPTERS = 8
    private const val MAX_HTML_PER_CHAPTER = 60_000
    private const val MAX_TEXT_PER_CHAPTER = 3_000
    private const val MAX_SAMPLE_LENGTH = 12_000

    private val HIDDEN_BLOCKS = Regex(
        "<(head|script|style)\\b[^>]*>.*?</\\1\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ENTITY = Regex("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")
    private val HTML_LANG = Regex(
        "<html\\b[^>]*?\\b(?:xml:)?lang\\s*=\\s*[\"']([^\"']+)[\"']",
        RegexOption.IGNORE_CASE
    )

    /** Text of a few chapters spread over the book (the first ones are often cover / copyright pages) */
    private fun sampleText(book: EpubBook): String {
        val chapters = book.chapters
        val builder = StringBuilder()
        if (chapters.isNotEmpty()) {
            val count = chapters.size
            val indices = if (count <= SAMPLED_CHAPTERS) {
                (0 until count).toList()
            } else {
                (0 until SAMPLED_CHAPTERS).map { it * count / SAMPLED_CHAPTERS }
            }
            for (index in indices) {
                val html = chapters[index].contentHtml
                val text = htmlToText(if (html.length > MAX_HTML_PER_CHAPTER) html.substring(0, MAX_HTML_PER_CHAPTER) else html)
                builder.append(if (text.length > MAX_TEXT_PER_CHAPTER) text.substring(0, MAX_TEXT_PER_CHAPTER) else text)
                builder.append(' ')
                if (builder.length >= MAX_SAMPLE_LENGTH) break
            }
        }
        if (builder.isBlank()) builder.append(book.title)
        // Some books store Vietnamese letters as letter + combining marks
        return Normalizer.normalize(builder, Normalizer.Form.NFC)
    }

    private fun htmlToText(html: String): String {
        val visible = HIDDEN_BLOCKS.replace(html, " ")
        val builder = StringBuilder(visible.length)
        var inTag = false
        for (ch in visible) {
            if (ch == '<') {
                inTag = true
                builder.append(' ')
            } else if (ch == '>') {
                inTag = false
            } else if (!inTag) {
                builder.append(ch)
            }
        }
        return ENTITY.replace(builder) { match -> decodeEntity(match.groupValues[1]) }
    }

    private fun decodeEntity(body: String): String {
        if (body.startsWith("#")) {
            val codePoint = try {
                if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
                    body.substring(2).toInt(16)
                } else {
                    body.substring(1).toInt()
                }
            } catch (_: NumberFormatException) {
                return " "
            }
            return if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else " "
        }
        return when (body.lowercase(Locale.ROOT)) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> " "
        }
    }

    /** lang / xml:lang of the chapter files (e.g. <html xml:lang="th">) */
    private fun htmlLanguage(book: EpubBook): String? {
        for (chapter in book.chapters.take(5)) {
            val html = chapter.contentHtml
            val head = if (html.length > 4_000) html.substring(0, 4_000) else html
            val value = HTML_LANG.find(head)?.groupValues?.getOrNull(1)
            normalizeTag(value)?.let { return it }
        }
        return null
    }

    // endregion

    // region Writing systems

    private const val LATIN = 0
    private const val GREEK = 1
    private const val CYRILLIC = 2
    private const val HEBREW = 3
    private const val ARABIC = 4
    private const val DEVANAGARI = 5
    private const val THAI = 6
    private const val LAO = 7
    private const val MYANMAR = 8
    private const val KHMER = 9
    private const val HANGUL = 10
    private const val KANA = 11
    private const val HAN = 12
    private const val OTHER = 13
    private const val SCRIPT_COUNT = 14

    private const val MIN_LETTERS = 30
    private const val MAX_TOKENS = 4_000

    /** Letters only used by Vietnamese (ạ ả ấ ầ ... ỹ, đ, ơ, ư) */
    private val VIETNAMESE_LETTERS = setOf(0x0110, 0x0111, 0x01A0, 0x01A1, 0x01AF, 0x01B0)
    private val UKRAINIAN_LETTERS = "іїєґІЇЄҐ".map { it.code }.toSet()
    private val RUSSIAN_LETTERS = "ыэъёЫЭЪЁ".map { it.code }.toSet()
    private val SIMPLIFIED_CHARS =
        "这们说来时个为国会对过还发么没现样进种头东两长门问间听见车书马话经开关实点让认应与万学".map { it.code }.toSet()
    private val TRADITIONAL_CHARS =
        "這們說來時個為國會對過還發麼沒現樣進種頭東兩長門問間聽見車書馬話經開關實點讓認應與萬學".map { it.code }.toSet()

    private fun scriptOf(cp: Int): Int = when (cp) {
        in 0x41..0x5A, in 0x61..0x7A, in 0xC0..0x24F, in 0x1E00..0x1EFF -> LATIN
        in 0x370..0x3FF, in 0x1F00..0x1FFF -> GREEK
        in 0x400..0x52F -> CYRILLIC
        in 0x590..0x5FF -> HEBREW
        in 0x600..0x6FF, in 0x750..0x77F, in 0x8A0..0x8FF, in 0xFB50..0xFDFF, in 0xFE70..0xFEFF -> ARABIC
        in 0x900..0x97F -> DEVANAGARI
        in 0xE00..0xE7F -> THAI
        in 0xE80..0xEFF -> LAO
        in 0x1000..0x109F -> MYANMAR
        in 0x1780..0x17FF -> KHMER
        in 0x1100..0x11FF, in 0x3130..0x318F, in 0xAC00..0xD7AF -> HANGUL
        in 0x3040..0x30FF, in 0x31F0..0x31FF, in 0xFF66..0xFF9F -> KANA
        in 0x3400..0x4DBF, in 0x4E00..0x9FFF, in 0xF900..0xFAFF, in 0x20000..0x2FFFF -> HAN
        else -> OTHER
    }

    private fun detectText(text: String): TextGuess? {
        val counts = IntArray(SCRIPT_COUNT)
        var letters = 0
        var vietnameseMarks = 0
        var ukrainianMarks = 0
        var russianMarks = 0
        var simplified = 0
        var traditional = 0

        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (!Character.isLetter(cp)) continue
            val script = scriptOf(cp)
            counts[script]++
            letters++
            if (script == LATIN) {
                if (cp in 0x1EA0..0x1EF9 || cp in VIETNAMESE_LETTERS) vietnameseMarks++
            } else if (script == CYRILLIC) {
                if (cp in UKRAINIAN_LETTERS) {
                    ukrainianMarks++
                } else if (cp in RUSSIAN_LETTERS) {
                    russianMarks++
                }
            } else if (script == HAN) {
                if (cp in SIMPLIFIED_CHARS) {
                    simplified++
                } else if (cp in TRADITIONAL_CHARS) {
                    traditional++
                }
            }
        }
        if (letters < MIN_LETTERS) return null

        // Weighted: one Chinese character / Hangul block carries more text than one Latin letter
        var dominant = OTHER
        var best = 0
        for (script in LATIN..HANGUL) {
            val score = counts[script] * (if (script == HANGUL) 2 else 1)
            if (score > best) {
                dominant = script
                best = score
            }
        }
        val cjk = counts[HAN] + counts[KANA]
        val cjkScore = counts[HAN] * 3 + counts[KANA] * 2
        if (cjkScore > best) {
            dominant = HAN
            best = cjkScore
        }
        if (best == 0) return null

        return when (dominant) {
            HAN -> when {
                counts[KANA] * 10 >= cjk -> TextGuess("ja", true)
                traditional > simplified -> TextGuess("zh-TW", true)
                else -> TextGuess("zh-CN", true, chineseVariantUnknown = simplified == 0)
            }
            CYRILLIC -> TextGuess(if (ukrainianMarks > russianMarks) "uk" else "ru", true)
            THAI -> TextGuess("th", true)
            LAO -> TextGuess("lo", true)
            KHMER -> TextGuess("km", true)
            MYANMAR -> TextGuess("my", true)
            HANGUL -> TextGuess("ko", true)
            GREEK -> TextGuess("el", true)
            HEBREW -> TextGuess("he", true)
            DEVANAGARI -> TextGuess("hi", true)
            ARABIC -> TextGuess("ar", true)
            LATIN -> guessLatin(text, counts[LATIN], vietnameseMarks)
            else -> null
        }
    }

    // endregion

    // region Latin script

    private class StopwordLanguage(val code: String, val words: Set<String>)

    private class Boost(val chars: String, val code: String, val points: Int)

    private fun words(list: String): Set<String> = list.split(' ').filter { it.isNotEmpty() }.toHashSet()

    /** The most frequent words of each language */
    private val STOPWORD_LANGUAGES = listOf(
        StopwordLanguage(
            "vi",
            words(
                "và của là không những được cho người một có này đã với các trong anh tôi em nó thì mà cũng như khi lại rồi nhưng đến ra đi lên nói vào " +
                    "khong nhung duoc nguoi mot cua trong nhu roi nay voi cac toi"
            )
        ),
        StopwordLanguage("en", words("the and of to a in is that it was he for with as his on be at by i you she her had not are but they this have from which were him my me")),
        StopwordLanguage("fr", words("le la les de des et un une du que qui dans est il elle pas pour sur au avec ne se ce son sa mais je vous nous était plus lui")),
        StopwordLanguage("de", words("der die das und ist nicht ein eine zu den mit sich des auf für im dem von er sie es ich auch wie war aber wenn noch nur nach")),
        StopwordLanguage("es", words("el la los las de y que en un una es por con para se no su al lo como más pero sus le ya fue este ha era muy")),
        StopwordLanguage("pt", words("o a os as de e que em um uma do da dos das no na não para com por se mais como mas ao ele ela foi é seu sua")),
        StopwordLanguage("it", words("il la le di e che un una è per non in del della si con da al sono ma come lo gli dei nel anche più era questo")),
        StopwordLanguage("nl", words("de het een en van is dat niet op te zijn in voor met die hij ze er maar aan als ook bij ik je was om nog wat")),
        StopwordLanguage("id", words("yang dan di ke dari ini itu dengan untuk tidak ada dalam akan pada saya kamu dia mereka sudah juga atau bisa karena oleh seperti adalah kita aku tetapi")),
        StopwordLanguage("fil", words("ang ng sa na at mga ay si ni ko mo ka siya niya hindi ito iyon kung para may lang din rin pa ba ako kami tayo")),
        StopwordLanguage("pl", words("i w nie na się z to do że jest jak o ale po co tak za od jego go już przez czy tylko był jej mnie ich tym")),
        StopwordLanguage("tr", words("ve bir bu da de için ile ne çok ama gibi daha o değil olan kadar var sonra ben sen onu ona diye her şey mi mı en yok")),
        StopwordLanguage("sv", words("och i att det som en på är av för med till den har de inte om ett han men var jag sig från vi så kan man när")),
        StopwordLanguage("da", words("og i at det en den til er som på de med han af for ikke der var mig sig men et har om vi min havde ham hun")),
        StopwordLanguage("nb", words("og i det er en til på som at av for med har de ikke den han var jeg om et men så seg hun vi kan skal ble")),
        StopwordLanguage("fi", words("ja on ei se että oli hän mutta kun niin ovat jos sen hänen minä sinä me he tämä mitä kuin vain myös nyt ole olen olla jo")),
        StopwordLanguage("cs", words("a se na v je že to s z do o jako ale by pro si k jsem jeho tak po už které nebo jsou ve ani byl bylo")),
        StopwordLanguage("ro", words("și de la în a cu că nu pe o un se din care este sa ce mai pentru ca au fost lui el ea sunt dar era am")),
        StopwordLanguage("hu", words("a az és hogy nem is egy ez meg de van volt csak már még mint el ki be fel mert sem azt ha én te ő minden úgy"))
    )

    /** Letters found in one language only */
    private val BOOSTS = listOf(
        Boost("ñ¿¡", "es", 2),
        Boost("ãõ", "pt", 2),
        Boost("œ", "fr", 2),
        Boost("ß", "de", 2),
        Boost("łśżźęąćń", "pl", 2),
        Boost("ğşı", "tr", 2),
        Boost("őű", "hu", 2),
        Boost("řěů", "cs", 2),
        Boost("șțţ", "ro", 2)
    )

    private fun guessLatin(text: String, latinLetters: Int, vietnameseMarks: Int): TextGuess? {
        if (vietnameseMarks * 100 >= latinLetters * 3) return TextGuess("vi", true)

        val scores = IntArray(STOPWORD_LANGUAGES.size)
        val word = StringBuilder()
        var tokens = 0

        fun countWord() {
            if (word.isEmpty()) return
            val token = word.toString().lowercase(Locale.ROOT)
            word.setLength(0)
            tokens++
            STOPWORD_LANGUAGES.forEachIndexed { index, language ->
                if (token in language.words) scores[index]++
            }
        }

        var i = 0
        while (i < text.length && tokens < MAX_TOKENS) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isLetter(cp)) {
                word.appendCodePoint(cp)
            } else {
                countWord()
            }
        }
        countWord()

        for (boost in BOOSTS) {
            val index = STOPWORD_LANGUAGES.indexOfFirst { it.code == boost.code }
            if (index < 0) continue
            var found = 0
            for (ch in text) {
                if (boost.chars.indexOf(ch) >= 0) found++
            }
            scores[index] += found * boost.points
        }

        var bestIndex = -1
        var best = 0
        var second = 0
        for (index in scores.indices) {
            val score = scores[index]
            if (score > best) {
                second = best
                best = score
                bestIndex = index
            } else if (score > second) {
                second = score
            }
        }
        if (bestIndex < 0) return null
        val confident = best >= 6 && best * 4 >= second * 5
        return TextGuess(STOPWORD_LANGUAGES[bestIndex].code, confident)
    }

    // endregion

    // region Language tags

    private val TAG_ALIASES = mapOf(
        "vie" to "vi", "eng" to "en", "chi" to "zh", "zho" to "zh", "cmn" to "zh", "yue" to "zh-TW",
        "tha" to "th", "jpn" to "ja", "kor" to "ko", "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de",
        "spa" to "es", "por" to "pt", "ita" to "it", "rus" to "ru", "ukr" to "uk", "ind" to "id", "in" to "id",
        "may" to "ms", "msa" to "ms", "tl" to "fil", "tgl" to "fil", "khm" to "km", "lao" to "lo",
        "bur" to "my", "mya" to "my", "hin" to "hi", "ara" to "ar", "tur" to "tr", "pol" to "pl",
        "dut" to "nl", "nld" to "nl", "swe" to "sv", "dan" to "da", "nob" to "nb", "nor" to "nb", "no" to "nb",
        "nno" to "nb", "nn" to "nb", "fin" to "fi", "cze" to "cs", "ces" to "cs", "rum" to "ro", "ron" to "ro",
        "mo" to "ro", "hun" to "hu", "gre" to "el", "ell" to "el", "heb" to "he", "iw" to "he",
        "vietnamese" to "vi", "english" to "en", "chinese" to "zh", "thai" to "th", "japanese" to "ja",
        "korean" to "ko", "french" to "fr", "german" to "de", "spanish" to "es", "portuguese" to "pt",
        "italian" to "it", "russian" to "ru", "indonesian" to "id"
    )

    /** "vi-VN", "eng", "zh-Hant", "zh_TW"... -> a [TtsLanguages] code, or null */
    fun normalizeTag(raw: String?): String? {
        val tag = raw?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-').orEmpty()
        if (tag.isEmpty()) return null
        val parts = tag.split('-')
        val primary = TAG_ALIASES[parts[0]] ?: parts[0]
        val code = if (primary == "zh") {
            val traditional = parts.drop(1).any { it == "tw" || it == "hk" || it == "mo" || it == "hant" }
            if (traditional) "zh-TW" else "zh-CN"
        } else {
            primary
        }
        return if (TtsLanguages.find(code) != null) code else null
    }

    // endregion
}
