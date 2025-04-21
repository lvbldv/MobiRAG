package com.mobirag

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import com.ml.shubham0204.sentence_embeddings.HFTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

class SentenceEmbeddingWrapper {
    private val sentenceEmbedding = SentenceEmbedding()
    private lateinit var hfTokenizer: HFTokenizer
    private lateinit var ortEnvironment: OrtEnvironment
    private var useTokenTypeIds_l = false
    private lateinit var ortSession: OrtSession
    private var normalizeEmbedding = false

    // Non-suspending init function that wraps the suspend function
    fun initSync(
        modelFilepath: String,
        tokenizerBytes: ByteArray,
        useTokenTypeIds: Boolean,
        outputTensorName: String,
        normalizeEmbedding: Boolean
    ): Int {
        this.useTokenTypeIds_l = useTokenTypeIds
        this.normalizeEmbedding = normalizeEmbedding
        this.hfTokenizer = HFTokenizer(tokenizerBytes)
        ortEnvironment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {addXnnpack(mapOf(
            "intra_op_num_threads" to "2"
        ))}
        ortSession = ortEnvironment.createSession(modelFilepath,options)
        return runBlocking {
            sentenceEmbedding.init(
                modelFilepath,
                tokenizerBytes,
                useTokenTypeIds,
                outputTensorName,
                normalizeEmbeddings = normalizeEmbedding
            )
        }
    }

    // Non-suspending encode function
    fun encodeSync(text: String): FloatArray {
        return runBlocking {
            sentenceEmbedding.encode(text)
        }
    }

    fun encodeBatchSync(sentences: List<String>): List<FloatArray> {
        return runBlocking {
            encodeBatch(sentences)
        }
    }

    suspend fun encodeBatch(sentences: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        val batchSize = sentences.size
        val tokens = sentences.map { hfTokenizer.tokenize(it) }

        val maxLength = tokens.maxOf { it.ids.size }

        // Prepare padded inputs
        val inputIds = Array(batchSize) { LongArray(maxLength) { 0L } }
        val attentionMask = Array(batchSize) { LongArray(maxLength) { 0L } }

        for (i in tokens.indices) {
            for (j in tokens[i].ids.indices) {
                inputIds[i][j] = tokens[i].ids[j]
                attentionMask[i][j] = tokens[i].attentionMask[j]
            }
        }

        val inputIdsTensor = OnnxTensor.createTensor(
            ortEnvironment,
            inputIds,
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            ortEnvironment,
            attentionMask,
        )

        val inputTensorMap = mutableMapOf<String, OnnxTensor>()
        inputTensorMap["input_ids"] = inputIdsTensor
        inputTensorMap["attention_mask"] = attentionMaskTensor

        if (useTokenTypeIds_l) {
            val tokenTypeIds = Array(batchSize) { LongArray(maxLength) { 0L } }
            for (i in tokens.indices) {
                for (j in tokens[i].tokenTypeIds.indices) {
                    tokenTypeIds[i][j] = tokens[i].tokenTypeIds[j]
                }
            }
            val tokenTypeIdsTensor = OnnxTensor.createTensor(ortEnvironment, tokenTypeIds)
            inputTensorMap["token_type_ids"] = tokenTypeIdsTensor
        }

        val outputs = ortSession.run(inputTensorMap)
        val tokenEmbeddings = outputs[0].value as Array<Array<FloatArray>>

        return@withContext tokenEmbeddings.mapIndexed { i, tokenEmbedding ->
            val pooled = meanPooling(tokenEmbedding, attentionMask[i])
            if (normalizeEmbedding) normalize(pooled) else pooled
        }
    }

    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, attentionMask: LongArray): FloatArray {
        var pooledEmbeddings = FloatArray(tokenEmbeddings[0].size) { 0f }
        var validTokenCount = 0

        tokenEmbeddings
            .filterIndexed{ index, _ -> attentionMask[index] == 1L }
            .forEachIndexed{ index, token ->
                validTokenCount++
                token.forEachIndexed{ j, value ->
                    pooledEmbeddings[j] += value
                }
            }

        // Avoid division by zero
        val divisor = max(validTokenCount, 1)
        pooledEmbeddings = pooledEmbeddings.map{ it / divisor }.toFloatArray()

        return pooledEmbeddings
    }

    // Function to normalize embeddings
    private fun normalize(embeddings: FloatArray): FloatArray {
        // Calculate the L2 norm (Euclidean norm)
        val norm = sqrt(embeddings.sumOf{ it * it.toDouble() }).toFloat()
        // Normalize each embedding by dividing by the norm
        return embeddings.map { it / norm }.toFloatArray()
    }
}