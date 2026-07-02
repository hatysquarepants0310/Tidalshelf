package app.tidalshelf.scrobbler

import java.text.Normalizer

data class YtCandidate(
    val videoId: String,
    val title: String,
    val artists: String,
    val isSong: Boolean,
)

/**
 * Matching difuso de una pista de Tidal contra resultados de YT Music.
 * Mismo diseño que se validó en el puente de escritorio: ratio de similitud
 * + boost por subcadena (los videos se titulan "Artista - Canción (Official
 * Video)") + preferencia por resultados tipo canción. Umbral 0.72.
 */
object Matcher {

    const val THRESHOLD = 0.72

    private val PAREN = Regex("""[(\[][^)\]]*[)\]]""")
    private val FEAT = Regex("""\b(feat|ft|featuring|con|with)\b.*""", RegexOption.IGNORE_CASE)
    private val PUNCT = Regex("""[^\p{L}\p{N}\s]""")
    private val SPACES = Regex("""\s+""")

    fun normalize(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replace(Regex("""\p{Mn}"""), "")
        t = PAREN.replace(t, " ")
        t = FEAT.replace(t, " ")
        t = PUNCT.replace(t.lowercase(), " ")
        return SPACES.replace(t, " ").trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }

    fun similarity(a: String, b: String): Double {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() && nb.isEmpty()) return 1.0
        val maxLen = maxOf(na.length, nb.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(na, nb).toDouble() / maxLen
    }

    fun score(wantedArtist: String, wantedTitle: String, candidate: YtCandidate): Double {
        var titleSim = similarity(wantedTitle, candidate.title)
        val normTitle = normalize(wantedTitle)
        if (normTitle.isNotEmpty() && normalize(candidate.title).contains(normTitle)) {
            titleSim = maxOf(titleSim, 0.9)
        }
        var artistSim = if (candidate.artists.isNotEmpty()) {
            similarity(wantedArtist, candidate.artists)
        } else 0.0
        val normArtist = normalize(wantedArtist)
        if (normArtist.isNotEmpty() &&
            normalize("${candidate.artists} ${candidate.title}").contains(normArtist)
        ) {
            artistSim = maxOf(artistSim, 0.9)
        }
        var s = 0.6 * titleSim + 0.4 * artistSim
        if (candidate.isSong) s += 0.05
        return minOf(s, 1.0)
    }

    fun findBest(wantedArtist: String, wantedTitle: String, candidates: List<YtCandidate>): Pair<YtCandidate?, Double> {
        var best: YtCandidate? = null
        var bestScore = 0.0
        for (candidate in candidates) {
            val s = score(wantedArtist, wantedTitle, candidate)
            if (s > bestScore) {
                best = candidate
                bestScore = s
            }
        }
        return if (best != null && bestScore >= THRESHOLD) best to bestScore else null to bestScore
    }
}
