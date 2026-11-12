package com.vyrocut.app

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

internal data class ProjectRecoverySnapshot(
    val projectId: String,
    val projectName: String,
    val savedAtMs: Long,
    val projectJson: String,
    val checksum: String,
)

/**
 * A small, versioned recovery copy kept separately from the primary project value.
 *
 * The fields are URL-safe Base64 so project names and JSON can contain any UTF-8
 * characters without making the journal format ambiguous. A SHA-256 checksum
 * detects truncated or partially overwritten recovery values.
 */
internal object ProjectRecoveryJournal {
    private const val HEADER = "VYROCUT_RECOVERY_V1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(
        projectId: String,
        projectName: String,
        savedAtMs: Long,
        projectJson: String,
    ): String {
        require(projectId.isNotBlank())
        require(savedAtMs > 0L)
        val fields = listOf(
            encodeField(projectId),
            encodeField(projectName),
            savedAtMs.toString(),
            encodeField(projectJson),
        )
        val signedContent = (listOf(HEADER) + fields).joinToString("\n")
        return "$signedContent\n${sha256(signedContent)}"
    }

    fun decode(raw: String?, expectedProjectId: String): ProjectRecoverySnapshot? {
        if (raw.isNullOrBlank() || expectedProjectId.isBlank()) return null
        val lines = raw.split('\n')
        if (lines.size != 6 || lines[0] != HEADER) return null
        val signedContent = lines.take(5).joinToString("\n")
        if (!constantTimeEquals(sha256(signedContent), lines[5])) return null

        return runCatching {
            val projectId = decodeField(lines[1])
            if (projectId != expectedProjectId) return null
            val projectName = decodeField(lines[2])
            val savedAtMs = lines[3].toLong().takeIf { it > 0L } ?: return null
            val projectJson = decodeField(lines[4])
            ProjectRecoverySnapshot(
                projectId = projectId,
                projectName = projectName,
                savedAtMs = savedAtMs,
                projectJson = projectJson,
                checksum = lines[5],
            )
        }.getOrNull()
    }

    fun shouldOfferRecovery(
        primaryProjectJson: String?,
        snapshot: ProjectRecoverySnapshot,
    ): Boolean = primaryProjectJson != snapshot.projectJson

    private fun encodeField(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun constantTimeEquals(first: String, second: String): Boolean {
        if (first.length != second.length) return false
        var difference = 0
        for (index in first.indices) {
            difference = difference or (first[index].code xor second[index].code)
        }
        return difference == 0
    }
}
