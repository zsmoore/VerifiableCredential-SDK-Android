/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package com.microsoft.portableIdentity.sdk.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.microsoft.portableIdentity.sdk.cards.PortableIdentityCard
import com.microsoft.portableIdentity.sdk.repository.networking.apis.ApiProvider
import com.microsoft.portableIdentity.sdk.repository.networking.cardOperations.FetchContractNetworkOperation
import com.microsoft.portableIdentity.sdk.repository.networking.cardOperations.FetchPresentationRequestNetworkOperation
import com.microsoft.portableIdentity.sdk.repository.networking.cardOperations.SendIssuanceResponseNetworkOperation
import com.microsoft.portableIdentity.sdk.repository.networking.cardOperations.SendPresentationResponseNetworkOperation
import com.microsoft.portableIdentity.sdk.utilities.Serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository is an abstraction layer that is consumed by business logic and abstracts away the various data sources
 * that an app can have. In the common case there are two data sources: network and database. The repository decides
 * where to get this data, how and when to cache it, how to handle issues etc. so that the business logic will only
 * ever care to get the object it wants.
 */
@Singleton
class CardRepository @Inject constructor(
    database: SdkDatabase,
    private val apiProvider: ApiProvider,
    private val serializer: Serializer
) {

    private val cardDao = database.cardDao()

    suspend fun insert(portableIdentityCard: PortableIdentityCard) = cardDao.insert(portableIdentityCard)

    suspend fun delete(portableIdentityCard: PortableIdentityCard) = cardDao.delete(portableIdentityCard)

    fun getAllCards(): LiveData<List<PortableIdentityCard>> = cardDao.getAllCards()

    fun getCardsByType(type: String): LiveData<List<PortableIdentityCard>> {
        return getAllCards().map { cardList -> filterCardsByType(cardList, type) }
    }

    private fun filterCardsByType(cardList: List<PortableIdentityCard>, type: String): List<PortableIdentityCard> {
        return cardList.filter { it.verifiableCredential.contents.vc.type.contains(type) }
    }

    fun getCardById(id: String): LiveData<PortableIdentityCard> = cardDao.getCardById(id)

    suspend fun getContract(url: String) = FetchContractNetworkOperation(
        url,
        apiProvider
    ).fire()

    suspend fun getRequest(url: String) = FetchPresentationRequestNetworkOperation(
        url,
        apiProvider
    ).fire()

    suspend fun sendIssuanceResponse(url: String, serializedResponse: String) = SendIssuanceResponseNetworkOperation(
        url,
        serializedResponse,
        apiProvider,
        serializer
    ).fire()

    suspend fun sendPresentationResponse(url: String, serializedResponse: String) = SendPresentationResponseNetworkOperation(
        url,
        serializedResponse,
        apiProvider
    ).fire()
}