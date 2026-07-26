package com.lianyu.ai.feature.memory.engine

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class WordPieceTokenizer(context: Context, assetName: String) {
    private val vocab: Map<String, Int>
    private val clsId: Int
    private val sepId: Int
    private val unkId: Int
    private val padId: Int

    init {
        val map = LinkedHashMap<String, Int>()
        context.assets.open(assetName).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).forEachLine { line ->
                if (line.isNotEmpty()) map[line] = map.size
            }
        }
        vocab = map
        clsId = vocab["[CLS]"] ?: 101
        sepId = vocab["[SEP]"] ?: 102
        unkId = vocab["[UNK]"] ?: 100
        padId = vocab["[PAD]"] ?: 0
    }

    fun encode(text: String, maxLen: Int = 64): Pair<LongArray, LongArray> {
        val chars = text.trim().lowercase().toCharArray()
        val tokens = mutableListOf<String>()

        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            if (c.isLetterOrDigit() && c.code < 128) {
                val sb = StringBuilder()
                while (i < chars.size && chars[i].isLetterOrDigit() && chars[i].code < 128) {
                    sb.append(chars[i]); i++
                }
                tokens.addAll(wordPiece(sb.toString()))
            } else if (!c.isWhitespace()) {
                tokens.add(c.toString())
                i++
            } else {
                i++
            }
        }

        val ids = mutableListOf(clsId)
        tokens.take(maxLen - 2).forEach { ids.add(vocab[it] ?: unkId) }
        ids.add(sepId)

        val inputIds = LongArray(maxLen) { idx -> if (idx < ids.size) ids[idx].toLong() else padId.toLong() }
        val attentionMask = LongArray(maxLen) { idx -> if (idx < ids.size) 1L else 0L }
        return inputIds to attentionMask
    }

    private fun wordPiece(word: String): List<String> {
        if (vocab.containsKey(word)) return listOf(word)
        val result = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: String? = null
            while (end > start) {
                val sub = (if (start > 0) "##" else "") + word.substring(start, end)
                if (vocab.containsKey(sub)) { found = sub; break }
                end--
            }
            if (found == null) return listOf("[UNK]")
            result.add(found)
            start = end
        }
        return result
    }
}
