package com.microsoft.did.sdk.datasource.file.models

import com.microsoft.did.sdk.credential.models.VerifiableCredential
import com.microsoft.did.sdk.credential.models.VerifiableCredentialContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * @constructor
 * @param vcs map of VC identifier (JTI) to raw VC token data
 * @param vcsMetaInf map of VC Identifier (JTI) to VC metadata
 * @param metaInf backup metadata
 * @param identifiers List of raw identifiers.
 */
@Serializable
data class MicrosoftBackup2020 (
    val vcs: Map<String, String>,
    val vcsMetaInf: Map<String, VCMetadata>,
    val metaInf: WalletMetadata,
    val identifiers: List<RawIdentity>
) : BackupFile() {
    companion object {
        const val MICROSOFT_BACKUP_TYPE = "MicrosoftWallet2020"
    }

    override val type: String
        get() = MICROSOFT_BACKUP_TYPE

    fun vcsToIterator(serializer: Json): Iterator<Pair<VerifiableCredential, VCMetadata>> {
        return VCIterator(vcs, vcsMetaInf, serializer)
    }

    private class VCIterator (
        val vcs: Map<String, String>,
        val vcsMetaInf: Map<String, VCMetadata>,
        val serializer: Json
    ) : Iterator<Pair<VerifiableCredential, VCMetadata>> {
        val jtis: Iterator<String> = vcs.keys.iterator()

        override fun hasNext(): Boolean {
            return jtis.hasNext()
        }

        override fun next(): Pair<VerifiableCredential, VCMetadata> {
            val jti = jtis.next()
            val rawToken = vcs[jti]!!
            val verifiableCredentialContent = serializer.decodeFromString(VerifiableCredentialContent.serializer(), rawToken)
            val vc = VerifiableCredential(verifiableCredentialContent.jti, rawToken, verifiableCredentialContent)
            return Pair(vc, vcsMetaInf[jti]!!)
        }
    }
}
