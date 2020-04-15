/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package com.microsoft.portableIdentity.sdk.utilities.controlflow

sealed class Result<out S> {
    class Success<out S>(val payload: S) : Result<S>()
    class Failure(val payload: PortableIdentitySdkException) : Result<Nothing>()
}

fun <U, T> Result<T>.map(transform: (T) -> U): Result<U> =
    when (this) {
        is Result.Success -> Result.Success(transform(payload))
        is Result.Failure -> this
    }

fun <T> Result<T>.mapError(transform: (PortableIdentitySdkException) -> PortableIdentitySdkException): Result<T> =
    when (this) {
        is Result.Success -> this
        is Result.Failure -> Result.Failure(transform(payload))
    }

fun <U, T> Result<T>.andThen(transform: (T) -> Result<U>): Result<U> =
    when (this) {
        is Result.Success -> transform(payload)
        is Result.Failure -> this
    }

suspend fun <T, E> runResultTry(block: suspend RunResultTryContext<E>.() -> Result<T>): Result<T> =
    try {
        RunResultTryContext<E>().block()
    } catch (ex: RunResultTryAbortion) {
        Result.Failure(ex.error as PortableIdentitySdkException)
    }

class RunResultTryContext<E> {
    fun <T> Result<T>.abortOnError(): T =
        when (this) {
            is Result.Success -> payload
            is Result.Failure -> throw RunResultTryAbortion(payload as Any)
        }
}

private class RunResultTryAbortion(val error: Any) : PortableIdentitySdkException()