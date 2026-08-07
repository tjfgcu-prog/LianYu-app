package com.lianyu.ai.network.tts

import android.content.Context
import java.io.File

/**
 * 本地离线 TTS 模型（sherpa-onnx VITS 格式）。
 *
 * 不提供任何内置可下载模型：所有模型文件都由用户通过系统文件选择器
 * 手动导入（跟 GGUF 本地模型一样）。固定存放位置
 * `<filesDir>/models/tts/custom/`，固定文件名 model.onnx / tokens.txt / lexicon.txt。
 */
object LocalTtsModel {
    const val MODEL_FILE_NAME = "model.onnx"
    const val TOKENS_FILE_NAME = "tokens.txt"
    const val LEXICON_FILE_NAME = "lexicon.txt"

    private const val MIN_VALID_FILE_BYTES = 64L

    fun modelDir(context: Context): File =
        File(File(context.filesDir, "models"), "tts/custom").also { it.mkdirs() }

    fun modelFile(context: Context): File = File(modelDir(context), MODEL_FILE_NAME)
    fun tokensFile(context: Context): File = File(modelDir(context), TOKENS_FILE_NAME)
    fun lexiconFile(context: Context): File = File(modelDir(context), LEXICON_FILE_NAME)

    /** 主模型 + tokens 是必需的；lexicon 视模型而定，允许缺失（部分 sherpa VITS 模型不需要词典） */
    fun isReady(context: Context): Boolean =
        isValid(modelFile(context)) && isValid(tokensFile(context))

    fun hasLexicon(context: Context): Boolean = isValid(lexiconFile(context))

    fun delete(context: Context) {
        modelDir(context).deleteRecursively()
    }

    private fun isValid(file: File): Boolean =
        file.exists() && file.length() >= MIN_VALID_FILE_BYTES
}
