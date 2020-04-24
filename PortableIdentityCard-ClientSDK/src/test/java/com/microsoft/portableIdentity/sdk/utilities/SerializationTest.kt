package com.microsoft.portableIdentity.sdk.utilities

import com.microsoft.portableIdentity.sdk.crypto.models.webCryptoApi.JsonWebKey
import com.microsoft.portableIdentity.sdk.identifier.models.identifierdocument.IdentifierDocument
import com.microsoft.portableIdentity.sdk.identifier.models.identifierdocument.IdentifierDocumentPublicKey
import com.microsoft.portableIdentity.sdk.identifier.models.payload.document.IdentifierDocumentService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SerializationTest {
    private var actualDocument: IdentifierDocument =
        IdentifierDocument(
            id = "did:test:hub.id",
            publicKey = listOf(
                IdentifierDocumentPublicKey(
                    id = "#signingKey",
                    type = "Secp256k1VerificationKey2018",
                    controller = "did:test:hub.id",
                    publicKeyJwk = JsonWebKey(
                        kty = "EC",
                        crv = "secp256k1",
                        x = "AEaA_TMpNsRwmZNwe70z2q_dz1rQ7G8gN0_UAydEMyU",
                        y = "ICzV5CiqZJeAS34tJ6t9AwKoe5dQpqlf25Eay5Stpco"
                    )
                )
            ),
            service = listOf(
                IdentifierDocumentService(
                    id = "#serviceEndpointId123",
                    type = "IdentityHub",
                    serviceEndpoint = "https://beta.hub.microsoft.com/"
                )
            ),
            recoveryKey = JsonWebKey(
                kty = "EC",
                crv = "secp256k1",
                x = "AEaA_TMpNsRwmZNwe70z2q_dz1rQ7G8gN0_UAydEMyU",
                y = "ICzV5CiqZJeAS34tJ6t9AwKoe5dQpqlf25Eay5Stpco"
            )
        )

    @Test
    fun `serialize and deserialize an identity document`() {
        val serializer = Serializer()
        val serializedDocument = serializer.stringify(IdentifierDocument.serializer(), actualDocument)
        val expectedDocument = serializer.parse(IdentifierDocument.serializer(), serializedDocument)
        val serializedExpectedDocument = serializer.stringify(IdentifierDocument.serializer(), expectedDocument)
        assertThat(serializedDocument).isEqualTo(serializedExpectedDocument)
    }
}