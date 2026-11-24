package com.example.domain

/**
 * Metadata for a persisted record that could not be decoded.
 */
data class UnreadableRecord(
    val source: String,
    val recordId: String,
    val errorMessage: String
)

/**
 * Result of decoding a batch of persisted rows without dropping or hiding failures.
 */
data class BatchDecodeResult<T>(
    val items: List<T>,
    val unreadable: List<UnreadableRecord>
)

/**
 * Pure-Kotlin JSON decoding wrappers so unreadable Room rows and DataStore entries are logged
 * and surfaced to the user rather than silently swallowed or deleted.
 */
object RecordDecodeRules {

    fun <Raw, T> decodeBatch(
        source: String,
        rawItems: List<Raw>,
        idOf: (Raw) -> String,
        decode: (Raw) -> T?,
        onError: (UnreadableRecord, Throwable?) -> Unit = { _, _ -> }
    ): BatchDecodeResult<T> {
        val decoded = ArrayList<T>(rawItems.size)
        val unreadable = ArrayList<UnreadableRecord>()
        for (raw in rawItems) {
            val id = idOf(raw)
            try {
                val value = decode(raw)
                if (value != null) {
                    decoded.add(value)
                } else {
                    val rec = UnreadableRecord(
                        source = source,
                        recordId = id,
                        errorMessage = "Decoded null payload"
                    )
                    unreadable.add(rec)
                    onError(rec, null)
                }
            } catch (t: Throwable) {
                val rec = UnreadableRecord(
                    source = source,
                    recordId = id,
                    errorMessage = t.message ?: t::class.java.simpleName
                )
                unreadable.add(rec)
                onError(rec, t)
            }
        }
        return BatchDecodeResult(items = decoded, unreadable = unreadable)
    }

    fun <T> decodeSingle(
        source: String,
        recordId: String,
        rawJson: String?,
        decode: (String) -> T?,
        onError: (UnreadableRecord, Throwable?) -> Unit = { _, _ -> }
    ): Pair<T?, UnreadableRecord?> {
        if (rawJson.isNullOrBlank()) return null to null
        return try {
            val value = decode(rawJson)
            if (value != null) {
                value to null
            } else {
                val rec = UnreadableRecord(
                    source = source,
                    recordId = recordId,
                    errorMessage = "Decoded null payload"
                )
                onError(rec, null)
                null to rec
            }
        } catch (t: Throwable) {
            val rec = UnreadableRecord(
                source = source,
                recordId = recordId,
                errorMessage = t.message ?: t::class.java.simpleName
            )
            onError(rec, t)
            null to rec
        }
    }

    fun formatUnreadableBannerMessage(count: Int): String {
        if (count <= 0) return ""
        return if (count == 1) {
            "1 saved item couldn't be loaded"
        } else {
            "$count saved items couldn't be loaded"
        }
    }
}
