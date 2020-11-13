package io.gnosis.data.backend

import io.gnosis.data.BuildConfig
import io.gnosis.data.backend.dto.*
import io.gnosis.data.models.assets.Collectible
import io.gnosis.data.models.Page
import io.gnosis.data.models.transaction.Transaction
import io.gnosis.data.models.transaction.TransactionConfirmationRequest
import io.gnosis.data.models.transaction.TransactionDetails
import retrofit2.http.*

interface GatewayApi {

    @GET("/v1/safes/{address}/balances/{fiat}")
    suspend fun loadBalances(@Path("address") address: String, @Path("fiat") fiat: String = "usd"): CoinBalancesDto

    @GET("v1/safes/{address}/transactions")
    suspend fun loadTransactions(@Path("address") address: String): Page<Transaction>

    @GET
    suspend fun loadTransactionsPage(@Url pageLink: String): Page<Transaction>

    @GET("v1/transactions/{transactionId}")
    suspend fun loadTransactionDetails(@Path("transactionId") transactionId: String): TransactionDetails

    @POST("v1/transactions/{safeTxHash}/confirmations")
    suspend fun submitConfirmation(
        @Path("safeTxHash") safeTxHash: String,
        @Body txConfirmationRequest: TransactionConfirmationRequest
    ): TransactionDetails

    @GET("v1/safes/{safeAddress}/collectibles")
    suspend fun loadCollectibles(@Path("safeAddress") safeAddress: String): List<Collectible>

    companion object {
        const val BASE_URL = BuildConfig.CLIENT_GATEWAY_URL
    }
}
