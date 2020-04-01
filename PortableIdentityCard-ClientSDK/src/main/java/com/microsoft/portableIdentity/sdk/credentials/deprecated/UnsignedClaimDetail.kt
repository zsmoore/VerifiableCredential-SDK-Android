package com.microsoft.portableIdentity.sdk.credentials.deprecated

import com.microsoft.portableIdentity.sdk.credentials.deprecated.ClaimDetail
import com.microsoft.portableIdentity.sdk.crypto.CryptoOperations
import com.microsoft.portableIdentity.sdk.resolvers.IResolver
import com.microsoft.portableIdentity.sdk.utilities.SdkLog
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("unsigned")
data class UnsignedClaimDetail(
    val data: List<Map<String, String>>
): ClaimDetail {
    @Required
    override val type: String
        get() = "unsigned"

    override suspend fun verify(cryptoOperations: CryptoOperations, resolver: IResolver) {
        // nothing to do
    }
}