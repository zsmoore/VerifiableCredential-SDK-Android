/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package com.microsoft.did.sdk.datasource.repository

import com.microsoft.did.sdk.credential.models.*
import com.microsoft.did.sdk.credential.service.*
import com.microsoft.did.sdk.credential.service.models.ExchangeRequest
import com.microsoft.did.sdk.credential.service.models.RevocationRequest
import com.microsoft.did.sdk.credential.service.models.oidc.OidcRequestContent
import com.microsoft.did.sdk.credential.service.protectors.OidcResponseFormatter
import com.microsoft.did.sdk.datasource.db.SdkDatabase
import com.microsoft.did.sdk.datasource.db.dao.ReceiptDao
import com.microsoft.did.sdk.datasource.db.dao.VerifiableCredentialDao
import com.microsoft.did.sdk.datasource.db.dao.VerifiableCredentialHolderDao
import com.microsoft.did.sdk.datasource.network.apis.ApiProvider
import com.microsoft.did.sdk.datasource.network.credentialOperations.SendPresentationResponseNetworkOperation
import com.microsoft.did.sdk.datasource.network.credentialOperations.SendVerifiableCredentialIssuanceRequestNetworkOperation
import com.microsoft.did.sdk.datasource.network.credentialOperations.SendVerifiablePresentationRevocationRequestNetworkOperation
import com.microsoft.did.sdk.identifier.models.Identifier
import com.microsoft.did.sdk.util.serializer.Serializer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.microsoft.did.sdk.util.controlflow.Result
import com.microsoft.did.sdk.util.controlflow.SdkException
import com.microsoft.did.sdk.util.unwrapSignedVerifiableCredential
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertEquals

class VerifiableCredentialHolderRepositoryTest {

    private val database: SdkDatabase = mockk()
    private val mockedVcDao: VerifiableCredentialDao = mockk()
    private val mockedIssuanceResponse: IssuanceResponse = mockk()
    private val mockedIssuanceRequest: IssuanceRequest = mockk()
    private val mockedPresentationResponse: PresentationResponse = mockk()
    private val mockedPresentationRequest: PresentationRequest = mockk()
    private val mockedRequestedVchMap: RequestedVchMap = mockk()
    private val mockedPrimeIdentifier: Identifier = mockk()
    private val mockedPairwiseIdentifier: Identifier = mockk()
    private val mockedFormatter: OidcResponseFormatter = mockk()
    private val mockedPrimeVcContent: VerifiableCredentialContent = mockk()
    private val mockedVch: VerifiableCredentialHolder = mockk()
    private val mockedPrimeVc: VerifiableCredential = mockk()
    private val mockedExchangedVcContent: VerifiableCredentialContent = mockk()

    private val repository: VerifiableCredentialHolderRepository
    private val serializer: Serializer = Serializer()

    private val expectedAudience: String = "audience23430"
    private val expectedSignedResponseToken: String = "responseToken49235"
    private val expectedRpDid: String = "did:ion:rp53292"
    private val expectedPairwiseDid: String = "did:ion:pairwise238"
    private val expectedPrimeDid: String = "did:ion:prime98493"
    private val expectedContractUrl: String = "https://contract.com/2434"
    private val expectedIdTokenContextField: String = "idTokenField48239"
    private val expectedSelfAttestedClaimKey: String = "selfAttestedClaim3454"
    private val expectedIdTokenContextMapping = mutableMapOf(expectedIdTokenContextField to "")
    private val expectedSelfAttestedClaimContext = mutableMapOf(expectedSelfAttestedClaimKey to "")
    private val expectedVcToken: String = "vcToken523094"
    private val expectedPrimeVcJti: String = "primeJti23723"
    private val expectedExchangedVcJti: String = "exchangeJti293859"

    private val expectedRevocationReceipt = """eyJraWQiOiJkaWQ6aW9uOkVpQ2ZlT2NpRWp3dXB3UlFzSkMzd01aenozX00zWElvNmJoeTdhSmtDRzZDQVE_LWlvbi1pbml0aWFsLXN0YXRlPWV5SmtaV3gwWVY5b1lYTm9Jam9pUldsRU1EUXdZMmxRYWtVeFIweHFMWEV5V21SeUxWSmFYelZsY1U4eU5GbERNRkk1YlRsRWQyWkhNa2RHUVNJc0luSmxZMjkyWlhKNVgyTnZiVzFwZEcxbGJuUWlPaUpGYVVNeVJtUTVVRTkwZW1GTmNVdE1hRE5SVEZwMFdrNDNWMFJEUkhKamRrTjRlVE52ZGxORVJEaEtSR1ZSSW4wLmV5SjFjR1JoZEdWZlkyOXRiV2wwYldWdWRDSTZJa1ZwUTJndGFURkRNVzFmTTJONFNHSk5NM3BYZW1SUmRFeHhNbkJ2UmxkYVgyNUZWRUpUYjBOaFQySlpUV2NpTENKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpYMnRsZVhNaU9sdDdJbWxrSWpvaWMybG5YekJtT1RkbFpXWmpJaXdpZEhsd1pTSTZJa1ZqWkhOaFUyVmpjREkxTm1zeFZtVnlhV1pwWTJGMGFXOXVTMlY1TWpBeE9TSXNJbXAzYXlJNmV5SnJkSGtpT2lKRlF5SXNJbU55ZGlJNkluTmxZM0F5TlRack1TSXNJbmdpT2lKb1EweHNiM0pKYkd4Mk0yRldTa1JpWWtOeE0wVkhielUyYldWNlEzUkxXa1pHY1V0dlMzUlZjM0J6SWl3aWVTSTZJbWgxVkc1aVRFYzNNV1UwTkRORWVWSmtlVTVEWDNkZmMzcGFSMGhWWVVjeFVIZHNNSHBYYjBoMkxVRWlmU3dpY0hWeWNHOXpaU0k2V3lKaGRYUm9JaXdpWjJWdVpYSmhiQ0pkZlYxOWZWMTkjc2lnXzBmOTdlZWZjIiwidHlwIjoiSldUIiwiYWxnIjoiRVMyNTZLIn0.eyJqdGkiOiJhYzNlNDJlZmNiNTQ0ZmNkYWFlMGJiMzFiMTU2Zjc2YiIsImlzcyI6ImRpZDppb246RWlBQWhsZnJTX252UlNBa0Z3Xy1JUjQ2QkdrSnJzQUNxbk1UZnA4ank0VjdDdz8taW9uLWluaXRpYWwtc3RhdGU9ZXlKa1pXeDBZVjlvWVhOb0lqb2lSV2xFUTJwV1VUTXpiblV5TlZVM1owMWZYelppWW5WbFZFaGpRWGhVVVZSQ2VVRjJMV0ozYTBJMmNERkNVU0lzSW5KbFkyOTJaWEo1WDJOdmJXMXBkRzFsYm5RaU9pSkZhVVJFWnpKb2FYbHZTblJ2Y1ZOYVYzaHNRa2hDVlZGbmFtTnJXVU5XZUVkNWVEaDZNVFZNUlhsSGVHdEJJbjAuZXlKMWNHUmhkR1ZmWTI5dGJXbDBiV1Z1ZENJNklrVnBSRVJuTW1ocGVXOUtkRzl4VTFwWGVHeENTRUpWVVdkcVkydFpRMVo0UjNsNE9Ib3hOVXhGZVVkNGEwRWlMQ0p3WVhSamFHVnpJanBiZXlKaFkzUnBiMjRpT2lKeVpYQnNZV05sSWl3aVpHOWpkVzFsYm5RaU9uc2ljSFZpYkdsalgydGxlWE1pT2x0N0ltbGtJam9pV2tKM1gzTnBaMjVmYzJOUVZVMXdkVnBmTVNJc0luUjVjR1VpT2lKRlkyUnpZVk5sWTNBeU5UWnJNVlpsY21sbWFXTmhkR2x2Ymt0bGVUSXdNVGtpTENKcWQyc2lPbnNpYTNSNUlqb2lSVU1pTENKamNuWWlPaUp6WldOd01qVTJhekVpTENKNElqb2lRekJIV21oT1JFa3dTalZ4TFRkd09XOWFlazU2Tm1SUWVsYzNWVGMwU1VnMlptcEtVemR4VEVsa1l5SXNJbmtpT2lJNVVuQTJTRGRyZGxRdFdXeG9kWGxrV0hSYVZGWm9iREpsV1hwWmVHTllaSEIzTUZoWlRWaHpOVFJCSW4wc0luQjFjbkJ2YzJVaU9sc2lZWFYwYUNJc0ltZGxibVZ5WVd3aVhYMWRmWDFkZlEiLCJjcmVkZW50aWFsU3RhdHVzIjp7ImlkIjoidXJuOnBpYzpmODAxYWRjMy1lM2Y1LTQ5NzUtOWU5YS0yOTE4ZjQwMzI5YzAiLCJyZWFzb24iOiJ0ZXN0aW5nIHJldm9jYXRpb24iLCJzdGF0dXMiOiJyZXZva2VkIn0sImlhdCI6MTU5NTAxMDI1NX0.MEUCIAx_qdpfUUMLaXGt-AkDtBH7jAUt3LMYsxKXLdc7uu0tAiEA3ztST0SVhN_hJakzt6qMyzCGOWDuWwpUmKLOETRIJpc"""
    private val revocationRequest: RevocationRequest = mockk()
    private val formattedRevocationRequest = """eyJhbGciOiJFUzI1NksiLCJraWQiOiJkaWQ6aW9uOkVpQUFobGZyU19udlJTQWtGd18tSVI0NkJHa0pyc0FDcW5NVGZwOGp5NFY3Q3c_LWlvbi1pbml0aWFsLXN0YXRlPWV5SmtaV3gwWVY5b1lYTm9Jam9pUldsRVEycFdVVE16Ym5VeU5WVTNaMDFmWHpaaVluVmxWRWhqUVhoVVVWUkNlVUYyTFdKM2EwSTJjREZDVVNJc0luSmxZMjkyWlhKNVgyTnZiVzFwZEcxbGJuUWlPaUpGYVVSRVp6Sm9hWGx2U25SdmNWTmFWM2hzUWtoQ1ZWRm5hbU5yV1VOV2VFZDVlRGg2TVRWTVJYbEhlR3RCSW4wLmV5SjFjR1JoZEdWZlkyOXRiV2wwYldWdWRDSTZJa1ZwUkVSbk1taHBlVzlLZEc5eFUxcFhlR3hDU0VKVlVXZHFZMnRaUTFaNFIzbDRPSG94TlV4RmVVZDRhMEVpTENKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpYMnRsZVhNaU9sdDdJbWxrSWpvaVdrSjNYM05wWjI1ZmMyTlFWVTF3ZFZwZk1TSXNJblI1Y0dVaU9pSkZZMlJ6WVZObFkzQXlOVFpyTVZabGNtbG1hV05oZEdsdmJrdGxlVEl3TVRraUxDSnFkMnNpT25zaWEzUjVJam9pUlVNaUxDSmpjbllpT2lKelpXTndNalUyYXpFaUxDSjRJam9pUXpCSFdtaE9SRWt3U2pWeExUZHdPVzlhZWs1Nk5tUlFlbGMzVlRjMFNVZzJabXBLVXpkeFRFbGtZeUlzSW5raU9pSTVVbkEyU0RkcmRsUXRXV3hvZFhsa1dIUmFWRlpvYkRKbFdYcFplR05ZWkhCM01GaFpUVmh6TlRSQkluMHNJbkIxY25CdmMyVWlPbHNpWVhWMGFDSXNJbWRsYm1WeVlXd2lYWDFkZlgxZGZRI1pCd19zaWduX3NjUFVNcHVaXzEifQ.eyJpc3MiOiJodHRwczovL3NlbGYtaXNzdWVkLm1lIiwic3ViIjoiOW9BRFBteDcxSF96VHJ0Ym5kTnZDUkVGem1pN2VCQkYtTzBoUmxCNGYzSSIsImF1ZCI6Imh0dHBzOi8vcG9ydGFibGVpZGVudGl0eWNhcmRzLmF6dXJlLWFwaS5uZXQvZGV2LXYxLjAvNTM2Mjc5ZjYtMTVjYy00NWYyLWJlMmQtNjFlMzUyYjUxZWVmL3BvcnRhYmxlSWRlbnRpdGllcy9jYXJkL3Jldm9rZSIsImRpZCI6ImRpZDppb246RWlBQWhsZnJTX252UlNBa0Z3Xy1JUjQ2QkdrSnJzQUNxbk1UZnA4ank0VjdDdz8taW9uLWluaXRpYWwtc3RhdGU9ZXlKa1pXeDBZVjlvWVhOb0lqb2lSV2xFUTJwV1VUTXpiblV5TlZVM1owMWZYelppWW5WbFZFaGpRWGhVVVZSQ2VVRjJMV0ozYTBJMmNERkNVU0lzSW5KbFkyOTJaWEo1WDJOdmJXMXBkRzFsYm5RaU9pSkZhVVJFWnpKb2FYbHZTblJ2Y1ZOYVYzaHNRa2hDVlZGbmFtTnJXVU5XZUVkNWVEaDZNVFZNUlhsSGVHdEJJbjAuZXlKMWNHUmhkR1ZmWTI5dGJXbDBiV1Z1ZENJNklrVnBSRVJuTW1ocGVXOUtkRzl4VTFwWGVHeENTRUpWVVdkcVkydFpRMVo0UjNsNE9Ib3hOVXhGZVVkNGEwRWlMQ0p3WVhSamFHVnpJanBiZXlKaFkzUnBiMjRpT2lKeVpYQnNZV05sSWl3aVpHOWpkVzFsYm5RaU9uc2ljSFZpYkdsalgydGxlWE1pT2x0N0ltbGtJam9pV2tKM1gzTnBaMjVmYzJOUVZVMXdkVnBmTVNJc0luUjVjR1VpT2lKRlkyUnpZVk5sWTNBeU5UWnJNVlpsY21sbWFXTmhkR2x2Ymt0bGVUSXdNVGtpTENKcWQyc2lPbnNpYTNSNUlqb2lSVU1pTENKamNuWWlPaUp6WldOd01qVTJhekVpTENKNElqb2lRekJIV21oT1JFa3dTalZ4TFRkd09XOWFlazU2Tm1SUWVsYzNWVGMwU1VnMlptcEtVemR4VEVsa1l5SXNJbmtpT2lJNVVuQTJTRGRyZGxRdFdXeG9kWGxrV0hSYVZGWm9iREpsV1hwWmVHTllaSEIzTUZoWlRWaHpOVFJCSW4wc0luQjFjbkJ2YzJVaU9sc2lZWFYwYUNJc0ltZGxibVZ5WVd3aVhYMWRmWDFkZlEiLCJzdWJfandrIjp7Imt0eSI6IkVDIiwia2lkIjoiI1pCd19zaWduX3NjUFVNcHVaXzEiLCJ1c2UiOiJzaWciLCJrZXlfb3BzIjpbInZlcmlmeSJdLCJhbGciOiJFUzI1NksiLCJjcnYiOiJQLTI1NksiLCJ4IjoiQzBHWmhOREkwSjVxLTdwOW9aek56NmRQelc3VTc0SUg2ZmpKUzdxTElkYyIsInkiOiI5UnA2SDdrdlQtWWxodXlkWHRaVFZobDJlWXpZeGNYZHB3MFhZTVhzNTRBIn0sImlhdCI6MTU5NTAwNDY3MiwiZXhwIjoxNTk1MTg0NjQwLCJqdGkiOiI4ZjIyYmVmMy1mY2VmLTQ5ODAtOTNhNC1kYTllMTRkYmQ0NTIiLCJ2YyI6ImV5SnJhV1FpT2lKa2FXUTZhVzl1T2tWcFEyWmxUMk5wUldwM2RYQjNVbEZ6U2tNemQwMWFlbm96WDAweldFbHZObUpvZVRkaFNtdERSelpEUVZFX0xXbHZiaTFwYm1sMGFXRnNMWE4wWVhSbFBXVjVTbXRhVjNnd1dWWTViMWxZVG05SmFtOXBVbGRzUlUxRVVYZFpNbXhSWVd0VmVGSXdlSEZNV0VWNVYyMVNlVXhXU21GWWVsWnNZMVU0ZVU1R2JFUk5Sa2sxWWxSc1JXUXlXa2hOYTJSSFVWTkpjMGx1U214Wk1qa3lXbGhLTlZneVRuWmlWekZ3WkVjeGJHSnVVV2xQYVVwR1lWVk5lVkp0VVRWVlJUa3daVzFHVG1OVmRFMWhSRTVTVkVad01GZHJORE5XTUZKRVVraEthbVJyVGpSbFZFNTJaR3hPUlZKRWFFdFNSMVpTU1c0d0xtVjVTakZqUjFKb1pFZFdabGt5T1hSaVYyd3dZbGRXZFdSRFNUWkphMVp3VVRKbmRHRlVSa1JOVnpGbVRUSk9ORk5IU2s1Tk0zQllaVzFTVW1SRmVIaE5ia0oyVW14a1lWZ3lOVVpXUlVwVVlqQk9hRlF5U2xwVVYyTnBURU5LZDFsWVVtcGhSMVo2U1dwd1ltVjVTbWhaTTFKd1lqSTBhVTlwU25sYVdFSnpXVmRPYkVscGQybGFSemxxWkZjeGJHSnVVV2xQYm5OcFkwaFdhV0pIYkdwWU1uUnNaVmhOYVU5c2REZEpiV3hyU1dwdmFXTXliRzVZZWtKdFQxUmtiRnBYV21wSmFYZHBaRWhzZDFwVFNUWkphMVpxV2toT2FGVXlWbXBqUkVreFRtMXplRlp0Vm5saFYxcHdXVEpHTUdGWE9YVlRNbFkxVFdwQmVFOVRTWE5KYlhBellYbEpObVY1U25Ka1NHdHBUMmxLUmxGNVNYTkpiVTU1WkdsSk5rbHVUbXhaTTBGNVRsUmFjazFUU1hOSmJtZHBUMmxLYjFFd2VITmlNMHBLWWtkNE1rMHlSbGRUYTFKcFdXdE9lRTB3VmtoaWVsVXlZbGRXTmxFelVreFhhMXBIWTFWMGRsTXpVbFpqTTBKNlNXbDNhV1ZUU1RaSmJXZ3hWa2MxYVZSRll6Tk5WMVV3VGtST1JXVldTbXRsVlRWRVdETmtabU16Y0dGU01HaFdXVlZqZUZWSVpITk5TSEJZWWpCb01reFZSV2xtVTNkcFkwaFdlV05IT1hwYVUwazJWM2xLYUdSWVVtOUphWGRwV2pKV2RWcFlTbWhpUTBwa1psWXhPV1pXTVRramMybG5YekJtT1RkbFpXWmpJaXdpZEhsd0lqb2lTbGRVSWl3aVlXeG5Jam9pUlZNeU5UWkxJbjAuZXlKcWRHa2lPaUoxY200NmNHbGpPbVk0TURGaFpHTXpMV1V6WmpVdE5EazNOUzA1WlRsaExUSTVNVGhtTkRBek1qbGpNQ0lzSW5aaklqcDdJa0JqYjI1MFpYaDBJanBiSW1oMGRIQnpPaTh2ZDNkM0xuY3pMbTl5Wnk4eU1ERTRMMk55WldSbGJuUnBZV3h6TDNZeElpd2lhSFIwY0hNNkx5OXdiM0owWVdKc1pXbGtaVzUwYVhSNVkyRnlaSE11WVhwMWNtVXRZWEJwTG01bGRDOWtaWFl0ZGpFdU1DODFNell5TnpsbU5pMHhOV05qTFRRMVpqSXRZbVV5WkMwMk1XVXpOVEppTlRGbFpXWXZjRzl5ZEdGaWJHVkpaR1Z1ZEdsMGFXVnpMMk52Ym5SeVlXTjBjeTlDZFhOcGJtVnpjME5oY21RaVhTd2lkSGx3WlNJNld5SldaWEpwWm1saFlteGxRM0psWkdWdWRHbGhiQ0lzSWtKMWMybHVaWE56UTJGeVpFTnlaV1JsYm5ScFlXd2lYU3dpWTNKbFpHVnVkR2xoYkZOMVltcGxZM1FpT25zaVptbHljM1JPWVcxbElqb2liaUlzSW14aGMzUk9ZVzFsSWpvaVp5SXNJbUoxYzJsdVpYTnpUbUZ0WlNJNkltNW5JbjBzSW1OeVpXUmxiblJwWVd4VGRHRjBkWE1pT25zaWFXUWlPaUpvZEhSd2N6b3ZMM0J2Y25SaFlteGxhV1JsYm5ScGRIbGpZWEprY3k1aGVuVnlaUzFoY0drdWJtVjBMMlJsZGkxMk1TNHdMelV6TmpJM09XWTJMVEUxWTJNdE5EVm1NaTFpWlRKa0xUWXhaVE0xTW1JMU1XVmxaaTl3YjNKMFlXSnNaVWxrWlc1MGFYUnBaWE12WTJGeVpDOXpkR0YwZFhNaUxDSjBlWEJsSWpvaVVHOXlkR0ZpYkdWSlpHVnVkR2wwZVVOaGNtUlRaWEoyYVdObFEzSmxaR1Z1ZEdsaGJGTjBZWFIxY3pJd01qQWlmU3dpWlhoamFHRnVaMlZUWlhKMmFXTmxJanA3SW1sa0lqb2lhSFIwY0hNNkx5OXdiM0owWVdKc1pXbGtaVzUwYVhSNVkyRnlaSE11WVhwMWNtVXRZWEJwTG01bGRDOWtaWFl0ZGpFdU1DODFNell5TnpsbU5pMHhOV05qTFRRMVpqSXRZbVV5WkMwMk1XVXpOVEppTlRGbFpXWXZjRzl5ZEdGaWJHVkpaR1Z1ZEdsMGFXVnpMMk5oY21RdlpYaGphR0Z1WjJVaUxDSjBlWEJsSWpvaVVHOXlkR0ZpYkdWSlpHVnVkR2wwZVVOaGNtUlRaWEoyYVdObFJYaGphR0Z1WjJVeU1ESXdJbjBzSW5KbGRtOXJaVk5sY25acFkyVWlPbnNpYVdRaU9pSm9kSFJ3Y3pvdkwzQnZjblJoWW14bGFXUmxiblJwZEhsallYSmtjeTVoZW5WeVpTMWhjR2t1Ym1WMEwyUmxkaTEyTVM0d0x6VXpOakkzT1dZMkxURTFZMk10TkRWbU1pMWlaVEprTFRZeFpUTTFNbUkxTVdWbFppOXdiM0owWVdKc1pVbGtaVzUwYVhScFpYTXZZMkZ5WkM5eVpYWnZhMlVpTENKMGVYQmxJam9pVUc5eWRHRmliR1ZKWkdWdWRHbDBlVU5oY21SVFpYSjJhV05sVW1WMmIydGxNakF5TUNKOWZTd2lhWE56SWpvaVpHbGtPbWx2YmpwRmFVTm1aVTlqYVVWcWQzVndkMUpSYzBwRE0zZE5XbnA2TTE5Tk0xaEpielppYUhrM1lVcHJRMGMyUTBGUlB5MXBiMjR0YVc1cGRHbGhiQzF6ZEdGMFpUMWxlVXByV2xkNE1GbFdPVzlaV0U1dlNXcHZhVkpYYkVWTlJGRjNXVEpzVVdGclZYaFNNSGh4VEZoRmVWZHRVbmxNVmtwaFdIcFdiR05WT0hsT1JteEVUVVpKTldKVWJFVmtNbHBJVFd0a1IxRlRTWE5KYmtwc1dUSTVNbHBZU2pWWU1rNTJZbGN4Y0dSSE1XeGlibEZwVDJsS1JtRlZUWGxTYlZFMVZVVTVNR1Z0Ums1alZYUk5ZVVJPVWxSR2NEQlhhelF6VmpCU1JGSklTbXBrYTA0MFpWUk9kbVJzVGtWU1JHaExVa2RXVWtsdU1DNWxlVW94WTBkU2FHUkhWbVpaTWpsMFlsZHNNR0pYVm5Wa1EwazJTV3RXY0ZFeVozUmhWRVpFVFZjeFprMHlUalJUUjBwT1RUTndXR1Z0VWxKa1JYaDRUVzVDZGxKc1pHRllNalZHVmtWS1ZHSXdUbWhVTWtwYVZGZGphVXhEU25kWldGSnFZVWRXZWtscWNHSmxlVXBvV1ROU2NHSXlOR2xQYVVwNVdsaENjMWxYVG14SmFYZHBXa2M1YW1SWE1XeGlibEZwVDI1emFXTklWbWxpUjJ4cVdESjBiR1ZZVFdsUGJIUTNTVzFzYTBscWIybGpNbXh1V0hwQ2JVOVVaR3hhVjFwcVNXbDNhV1JJYkhkYVUwazJTV3RXYWxwSVRtaFZNbFpxWTBSSk1VNXRjM2hXYlZaNVlWZGFjRmt5UmpCaFZ6bDFVekpXTlUxcVFYaFBVMGx6U1cxd00yRjVTVFpsZVVweVpFaHJhVTlwU2taUmVVbHpTVzFPZVdScFNUWkpiazVzV1ROQmVVNVVXbkpOVTBselNXNW5hVTlwU205Uk1IaHpZak5LU21KSGVESk5Na1pYVTJ0U2FWbHJUbmhOTUZaSVlucFZNbUpYVmpaUk0xSk1WMnRhUjJOVmRIWlRNMUpXWXpOQ2VrbHBkMmxsVTBrMlNXMW9NVlpITldsVVJXTXpUVmRWTUU1RVRrVmxWa3ByWlZVMVJGZ3paR1pqTTNCaFVqQm9WbGxWWTNoVlNHUnpUVWh3V0dJd2FESk1WVVZwWmxOM2FXTklWbmxqUnpsNldsTkpObGQ1U21oa1dGSnZTV2wzYVZveVZuVmFXRXBvWWtOS1pHWldNVGxtVmpFNUlpd2ljM1ZpSWpvaVpHbGtPbWx2YmpwRmFVRkJhR3htY2xOZmJuWlNVMEZyUm5kZkxVbFNORFpDUjJ0S2NuTkJRM0Z1VFZSbWNEaHFlVFJXTjBOM1B5MXBiMjR0YVc1cGRHbGhiQzF6ZEdGMFpUMWxlVXByV2xkNE1GbFdPVzlaV0U1dlNXcHZhVkpYYkVWUk1uQlhWVlJOZW1KdVZYbE9WbFV6V2pBeFpsaDZXbWxaYmxac1ZrVm9hbEZZYUZWVlZsSkRaVlZHTWt4WFNqTmhNRWt5WTBSR1ExVlRTWE5KYmtwc1dUSTVNbHBZU2pWWU1rNTJZbGN4Y0dSSE1XeGlibEZwVDJsS1JtRlZVa1ZhZWtwdllWaHNkbE51VW5aalZrNWhWak5vYzFGcmFFTldWa1p1WVcxT2NsZFZUbGRsUldRMVpVUm9OazFVVmsxU1dHeElaVWQwUWtsdU1DNWxlVW94WTBkU2FHUkhWbVpaTWpsMFlsZHNNR0pYVm5Wa1EwazJTV3RXY0ZKRlVtNU5iV2h3WlZjNVMyUkhPWGhWTVhCWVpVZDRRMU5GU2xaVlYyUnhXVEowV2xFeFdqUlNNMncwVDBodmVFNVZlRVpsVldRMFlUQkZhVXhEU25kWldGSnFZVWRXZWtscWNHSmxlVXBvV1ROU2NHSXlOR2xQYVVwNVdsaENjMWxYVG14SmFYZHBXa2M1YW1SWE1XeGlibEZwVDI1emFXTklWbWxpUjJ4cVdESjBiR1ZZVFdsUGJIUTNTVzFzYTBscWIybFhhMG96V0ROT2NGb3lOV1pqTWs1UlZsVXhkMlJXY0daTlUwbHpTVzVTTldOSFZXbFBhVXBHV1RKU2VsbFdUbXhaTTBGNVRsUmFjazFXV214amJXeHRZVmRPYUdSSGJIWmlhM1JzWlZSSmQwMVVhMmxNUTBweFpESnphVTl1YzJsaE0xSTFTV3B2YVZKVlRXbE1RMHBxWTI1WmFVOXBTbnBhVjA1M1RXcFZNbUY2UldsTVEwbzBTV3B2YVZGNlFraFhiV2hQVWtWcmQxTnFWbmhNVkdSM1QxYzVZV1ZyTlRaT2JWSlJaV3hqTTFaVVl6QlRWV2N5V20xd1MxVjZaSGhVUld4cldYbEpjMGx1YTJsUGFVazFWVzVCTWxORVpISmtiRkYwVjFkNGIyUlliR3RYU0ZKaFZrWmFiMkpFU214WFdIQmFaVWRPV1ZwSVFqTk5SbWhhVkZab2VrNVVVa0pKYmpCelNXNUNNV051UW5aak1sVnBUMnh6YVZsWVZqQmhRMGx6U1cxa2JHSnRWbmxaVjNkcFdGZ3haR1pZTVdSbVVTSXNJbWxoZENJNk1UVTVORGswTWpFNU5Td2laWGh3SWpveE5UazNOVE0wTVRrMWZRLk1FVUNJUUNfeFBCdzNYOEZvTWQ0WHlxVlFFeWZxUHZVV0d0cjRCdzJURmt0OGdjZ1RBSWdNRzlLbHRuUWd6NEVocVdLc3FGTHFNRkkwUUdMeVNUbU5rMEtqd0F1QWZJIiwicmVhc29uIjoidGVzdGluZyByZXZvY2F0aW9uIn0.XgywYNPHoNUaYZrBNtQ3l9va4u1pj9bxgVZ7KtymrOuHjCG15b3KAGljiN-gILsVdTWbuFVeFRQEU1E5Iw6NfQ"""

    init {
        mockkConstructor(SendVerifiableCredentialIssuanceRequestNetworkOperation::class)
        mockkConstructor(SendPresentationResponseNetworkOperation::class)
        mockkConstructor(SendVerifiablePresentationRevocationRequestNetworkOperation::class)
        val apiProvider: ApiProvider = mockk()
        setUpFormatter()
        setUpDatabase()
        every { mockedPrimeIdentifier.id } returns expectedPrimeDid
        every { mockedPairwiseIdentifier.id } returns expectedPairwiseDid
        repository = VerifiableCredentialHolderRepository(database, apiProvider, mockedFormatter, serializer)
    }

    @Test
    fun `send issuance response successfully`() {
        setUpIssuanceResponse()
        setUpMockedVcContents(mockedPrimeVcContent, expectedPrimeVcJti, expectedPrimeDid)
        mockUnwrapSignedVcTopLevelFunction(mockedPrimeVcContent)
        coEvery { anyConstructed<SendVerifiableCredentialIssuanceRequestNetworkOperation>().fire() } returns Result.Success(expectedVcToken)

        runBlocking {
            val actualResult = repository.sendIssuanceResponse(
                mockedIssuanceResponse,
                mockedRequestedVchMap,
                mockedPrimeIdentifier
            )
            assertThat(actualResult).isInstanceOf(Result.Success::class.java)
            assertEquals((actualResult as Result.Success).payload.contents, mockedPrimeVcContent)
            assertEquals((actualResult).payload.jti, expectedPrimeVcJti)
            assertEquals((actualResult).payload.picId, expectedPrimeVcJti)
            assertEquals((actualResult).payload.raw, expectedVcToken)
        }
    }

    @Test
    fun `send issuance response with failed response from service`() {
        setUpIssuanceResponse()
        mockUnwrapSignedVcTopLevelFunction(mockedPrimeVcContent)
        val expectedException = SdkException()
        coEvery { anyConstructed<SendVerifiableCredentialIssuanceRequestNetworkOperation>().fire() } returns Result.Failure(
            expectedException
        )

        runBlocking {
            val actualResult = repository.sendIssuanceResponse(
                mockedIssuanceResponse,
                mockedRequestedVchMap,
                mockedPrimeIdentifier
            )
            assertThat(actualResult).isInstanceOf(Result.Failure::class.java)
            assertEquals((actualResult as Result.Failure).payload, expectedException)
        }
    }

    @Test
    fun `send presentation response successfully`() {
        setUpPresentationResponse()
        coEvery { anyConstructed<SendPresentationResponseNetworkOperation>().fire() } returns Result.Success(Unit)

        runBlocking {
            val actualResult = repository.sendPresentationResponse(
                mockedPresentationResponse,
                mockedRequestedVchMap,
                mockedPrimeIdentifier
            )
            assertThat(actualResult).isInstanceOf(Result.Success::class.java)
            assertEquals((actualResult as Result.Success).payload, Unit)
        }
    }

    @Test
    fun `send presentation response with failed response from service`() {
        setUpPresentationResponse()
        val expectedException = SdkException()
        coEvery { anyConstructed<SendPresentationResponseNetworkOperation>().fire() } returns Result.Failure(expectedException)

        runBlocking {
            val actualResult = repository.sendPresentationResponse(
                mockedPresentationResponse,
                mockedRequestedVchMap,
                mockedPrimeIdentifier
            )
            assertThat(actualResult).isInstanceOf(Result.Failure::class.java)
            assertEquals((actualResult as Result.Failure).payload, expectedException)
        }
    }

    @Test
    fun `send exchange response successfully`() {
        setUpMockedVcContents(mockedExchangedVcContent, expectedExchangedVcJti, expectedPairwiseDid)
        mockUnwrapSignedVcTopLevelFunction(mockedExchangedVcContent)
        setUpExchangeRequest()
        setUpVpContext()
        coEvery { repository.getAllVerifiableCredentialsById(expectedPrimeVcJti) } returns emptyList()
        coEvery { anyConstructed<SendVerifiableCredentialIssuanceRequestNetworkOperation>().fire() } returns Result.Success(expectedVcToken)

        runBlocking {
            val actualResult = repository.getExchangedVerifiableCredential(
                mockedVch,
                mockedPairwiseIdentifier
            )
            assertThat(actualResult).isInstanceOf(Result.Success::class.java)
            assertEquals((actualResult as Result.Success).payload.raw, expectedVcToken)
            assertEquals((actualResult).payload.contents, mockedExchangedVcContent)
            assertEquals((actualResult).payload.jti, expectedExchangedVcJti)
            assertEquals((actualResult).payload.picId, expectedPrimeVcJti)
            assertEquals((actualResult).payload.raw, expectedVcToken)
        }
    }

    @Test
    fun `send exchange response with failed response from service`() {
        setUpMockedVcContents(mockedExchangedVcContent, expectedExchangedVcJti, expectedPairwiseDid)
        mockUnwrapSignedVcTopLevelFunction(mockedExchangedVcContent)
        setUpExchangeRequest()
        setUpVpContext()
        coEvery { repository.getAllVerifiableCredentialsById(expectedPrimeVcJti) } returns emptyList()
        val expectedException = SdkException()
        coEvery { anyConstructed<SendVerifiableCredentialIssuanceRequestNetworkOperation>().fire() } returns Result.Failure(
            expectedException
        )

        runBlocking {
            val actualResult = repository.getExchangedVerifiableCredential(
                mockedVch,
                mockedPairwiseIdentifier
            )
            assertThat(actualResult).isInstanceOf(Result.Failure::class.java)
            assertEquals((actualResult as Result.Failure).payload, expectedException)
        }
    }

    @Test
    fun `get exchanged vc from database successfully`() {
        setUpVpContext()
        setUpMockedVcContents(mockedExchangedVcContent, expectedExchangedVcJti, expectedPairwiseDid)
        val mockedExchangedVc: VerifiableCredential = mockk()
        every { mockedExchangedVc.contents } returns mockedExchangedVcContent
        coEvery { repository.getAllVerifiableCredentialsById(expectedPrimeVcJti) } returns listOf(mockedExchangedVc)

        runBlocking {
            val actualResult = repository.getExchangedVerifiableCredential(
                mockedVch,
                mockedPairwiseIdentifier
            )
            assertThat(actualResult).isInstanceOf(Result.Success::class.java)
            assertEquals((actualResult as Result.Success).payload.contents, mockedExchangedVcContent)
        }
    }

    @Test
    fun `revoke Verifiable Presentation successfully`() {
        val expectedRevocationStatus = "revoked"
        val expectedRevocationReason = "testing revocation"
        mockRevocationRequest()

        runBlocking {
            val actualRevocationReceipt = repository.sendRevocationRequest(revocationRequest, formattedRevocationRequest)
            assertThat(actualRevocationReceipt.credentialStatus.status).isEqualTo(expectedRevocationStatus)
            assertThat(actualRevocationReceipt.credentialStatus.reason).isEqualTo(expectedRevocationReason)
        }
        coVerify(exactly = 1) {
            anyConstructed<SendVerifiablePresentationRevocationRequestNetworkOperation>().fire()
        }
    }

    private fun setUpDatabase() {
        val mockedVchDao: VerifiableCredentialHolderDao = mockk()
        val mockedReceiptDao: ReceiptDao = mockk()
        every { database.verifiableCredentialHolderDao() } returns mockedVchDao
        every { database.receiptDao() } returns mockedReceiptDao
        every { database.verifiableCredentialDao() } returns mockedVcDao
        coEvery { mockedVcDao.insert(any()) } returns Unit
    }

    private fun setUpFormatter() {
        every {
            mockedFormatter.format(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns expectedSignedResponseToken
    }

    private fun setUpIssuanceResponse() {
        every { mockedIssuanceResponse.audience } returns expectedAudience
        every { mockedIssuanceResponse.request } returns mockedIssuanceRequest
        every { mockedIssuanceRequest.entityIdentifier } returns expectedRpDid
        every { mockedIssuanceRequest.contractUrl } returns expectedContractUrl
        every { mockedIssuanceResponse.getRequestedIdTokens() } returns expectedIdTokenContextMapping
        every { mockedIssuanceResponse.getRequestedSelfAttestedClaims() } returns expectedSelfAttestedClaimContext
    }

    private fun setUpPresentationResponse() {
        val oidcRequestContent: OidcRequestContent = mockk()
        every { mockedPresentationResponse.audience } returns expectedAudience
        every { mockedPresentationResponse.request } returns mockedPresentationRequest
        every { mockedPresentationRequest.entityIdentifier } returns expectedRpDid
        every { mockedPresentationRequest.content } returns oidcRequestContent
        every { oidcRequestContent.nonce } returns ""
        every { oidcRequestContent.state } returns ""
        every { mockedPresentationResponse.getRequestedIdTokens() } returns expectedIdTokenContextMapping
        every { mockedPresentationResponse.getRequestedSelfAttestedClaims() } returns expectedSelfAttestedClaimContext
    }

    private fun setUpExchangeRequest() {
        mockkConstructor(ExchangeRequest::class)
        every { anyConstructed<ExchangeRequest>().pairwiseDid } returns expectedPairwiseDid
        every { anyConstructed<ExchangeRequest>().verifiableCredential } returns mockedPrimeVc
        every { mockedPrimeVc.picId } returns expectedPrimeVcJti
        every { mockedPrimeVc.contents } returns mockedExchangedVcContent
    }

    private fun setUpVpContext() {
        setUpMockedVch()
        setUpMockedPrimeVc()
        setUpMockedVcContents(mockedPrimeVcContent, expectedPrimeVcJti, expectedPrimeDid)
    }

    private fun setUpMockedVch() {
        every { mockedVch.owner } returns mockedPrimeIdentifier
        every { mockedVch.verifiableCredential } returns mockedPrimeVc
        every { mockedVch.cardId } returns expectedPrimeVcJti
    }

    private fun setUpMockedPrimeVc() {
        every { mockedPrimeVc.contents } returns mockedPrimeVcContent
        every { mockedPrimeVc.picId } returns expectedPrimeVcJti
    }

    private fun setUpMockedVcContents(vcContent: VerifiableCredentialContent, jti: String, subjectDid: String) {
        val mockedVcDescriptor: VerifiableCredentialDescriptor = mockk()
        val mockedServiceDescriptor: ServiceDescriptor = mockk()
        val expectedExchangeUrl = "https://exchange.com/23948"
        every { vcContent.vc } returns mockedVcDescriptor
        every { vcContent.jti } returns jti
        every { vcContent.sub } returns subjectDid
        every { mockedVcDescriptor.exchangeService } returns mockedServiceDescriptor
        every { mockedServiceDescriptor.id } returns expectedExchangeUrl
    }

    private fun mockUnwrapSignedVcTopLevelFunction(returnedVcContent: VerifiableCredentialContent) {
        mockkStatic("com.microsoft.did.sdk.util.VerifiableCredentialUtilKt")
        every { unwrapSignedVerifiableCredential(any(), serializer) } returns returnedVcContent
    }

    private fun mockRevocationRequest() {
        coEvery { anyConstructed<SendVerifiablePresentationRevocationRequestNetworkOperation>().fire() } returns Result.Success(expectedRevocationReceipt)
        every { revocationRequest.audience } returns expectedAudience
    }
}