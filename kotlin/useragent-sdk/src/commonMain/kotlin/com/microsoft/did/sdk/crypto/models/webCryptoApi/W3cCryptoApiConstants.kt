/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/
package com.microsoft.did.sdk.crypto.models.webCryptoApi

/**
 * Class for W3C Crypto API constants
 */
enum class W3cCryptoApiConstants(val value: String) {
    /**
     * Define W3C JWK constants
     */
    Jwk("jwk"),

    /**
     * Define W3C algorithm constants
     */
    RsaOaep("RSA-OAEP-256"),

    /**
     * Define W3C algorithm constants
     */
    RsaSsaPkcs1V15("RSASSA-PKCS1-v1_5"),

    Sha1("SHA-1"),

    Sha224("SHA-224"),

    /**
     * Define W3C algorithm constants
     */
    Sha256("SHA-256"),

    Sha384("SHA-384"),

    /**
     * Define W3C algorithm constants
     */
    Sha512("SHA-512"),

    /**
     * Define W3C algorithm constants
     */
    AesGcm("AES-GCM"),

    EcDsa("ECDSA"),

    EdDsa("EDDSA"),

    /**
     * Define W3C algorithm constants
     */
    Hmac("HMAC"),

    /**
     * @see https://www.w3.org/TR/WebCryptoAPI/#dfn-NamedCurve
     */
    Secp256r1("P-256"),
    Secp384r1("P-384"),
    Secp521r1("P-521"),
    Secp256k1("P-256K"),
    Ed25519("ed25519")
}