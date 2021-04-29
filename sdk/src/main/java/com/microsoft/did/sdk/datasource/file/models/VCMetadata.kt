package com.microsoft.did.sdk.datasource.file.models

import com.microsoft.did.sdk.credential.service.models.contracts.display.DisplayContract
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("BaseVC")
abstract class VCMetadata {
    abstract val displayContract: DisplayContract
}