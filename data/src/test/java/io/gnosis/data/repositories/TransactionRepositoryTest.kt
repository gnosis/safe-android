package io.gnosis.data.repositories

import io.gnosis.data.backend.GatewayApi
import io.gnosis.data.backend.dto.Custom
import io.gnosis.data.backend.dto.DataDecodedDto
import io.gnosis.data.backend.dto.Erc20Transfer
import io.gnosis.data.backend.dto.Erc721Transfer
import io.gnosis.data.backend.dto.EtherTransfer
import io.gnosis.data.backend.dto.ExecutionInfo
import io.gnosis.data.backend.dto.GateTransactionDto
import io.gnosis.data.backend.dto.GateTransactionType
import io.gnosis.data.backend.dto.GateTransferType
import io.gnosis.data.backend.dto.ParamsDto
import io.gnosis.data.backend.dto.ServiceTokenInfo
import io.gnosis.data.backend.dto.SettingsChange
import io.gnosis.data.backend.dto.TransactionDirection
import io.gnosis.data.backend.dto.TransactionInfo
import io.gnosis.data.backend.dto.Transfer
import io.gnosis.data.backend.dto.TransferInfo
import io.gnosis.data.models.Page
import io.gnosis.data.models.Transaction
import io.gnosis.data.models.TransactionStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asDecimalString
import pm.gnosis.utils.asEthereumAddress

class TransactionRepositoryTest {

    private val gatewayApi = mockk<GatewayApi>()

    private val transactionRepository = TransactionRepository(gatewayApi)
    private val defaultSafeAddress = "0x1C8b9B78e3085866521FE206fa4c1a67F49f153A".asEthereumAddress()!!
    private val defaultFromAddress = "0x7cd310A8AeBf268bF78ea16C601F201ca81e84Cc".asEthereumAddress()!!
    private val defaultToAddress = "0x2134Bb3DE97813678daC21575E7A77a95079FC51".asEthereumAddress()!!

    @Test
    fun `getTransactions (api failure) should throw`() = runBlockingTest {
        val throwable = Throwable()
        coEvery { gatewayApi.loadTransactions(any()) } throws throwable

        val actual = runCatching { transactionRepository.getTransactions(defaultSafeAddress) }

        with(actual) {
            assert(isFailure)
            assertEquals(throwable, exceptionOrNull())
        }
        coVerify(exactly = 1) { gatewayApi.loadTransactions(defaultSafeAddress.asEthereumAddressChecksumString()) }
    }

    @Test
    fun `getTransactions (all tx type) should return respective tx type`() = runBlockingTest {
        val pagedResult = listOf(
            buildGateTransactionDto(txInfo = buildTransferTxInfo()),
            buildGateTransactionDto(txInfo = buildCustomTxInfo()),
            buildGateTransactionDto(txInfo = buildSettingsChangeTxInfo())
        )
        coEvery { gatewayApi.loadTransactions(any()) } returns Page(1, null, null, pagedResult)

        val actual = transactionRepository.getTransactions(defaultSafeAddress)

        assertEquals(3, actual.results.size)
        (0..2).forEach { i ->
            with(actual.results[i]) {
                when (pagedResult[i].txInfo) {
                    is Transfer -> {
                        assertEquals(pagedResult[i].executionInfo?.nonce, (this as Transaction.Transfer).nonce)
                        assertEquals(pagedResult[i].txStatus, this.status)
                        assertEquals((pagedResult[i].txInfo as Transfer).direction != TransactionDirection.OUTGOING, incoming)
                    }
                    is Custom -> {
                        assertEquals(pagedResult[i].executionInfo?.nonce, (this as Transaction.Custom).nonce)
                        assertEquals(pagedResult[i].txStatus, this.status)
                        assertEquals((pagedResult[i].txInfo as Custom).dataSize, "${dataSize}")
                    }
                    is SettingsChange -> {
                        assertEquals(pagedResult[i].executionInfo?.nonce, (this as Transaction.SettingsChange).nonce)
                        assertEquals(pagedResult[i].txStatus, this.status)
                        assertEquals((pagedResult[i].txInfo as SettingsChange).dataDecoded, dataDecoded)
                    }
                }
            }
        }
    }

    @Test
    fun `loadTransactionsPage (all tx type) should return respective tx type`() = runBlockingTest {
        val pagedResult = listOf(
            buildGateTransactionDto(txInfo = buildTransferTxInfo()),
            buildGateTransactionDto(txInfo = buildCustomTxInfo()),
            buildGateTransactionDto(txInfo = buildSettingsChangeTxInfo())
        )
        coEvery { gatewayApi.loadTransactionsPage(any()) } returns Page(1, null, null, pagedResult)

        val actual = transactionRepository.loadTransactionsPage("url")

        assertEquals(3, actual.results.size)

        (0..2).forEach { i ->
            with(actual.results[i]) {
                when (pagedResult[i].txInfo) {
                    is Transfer -> {
                        assertEquals(pagedResult[i].executionInfo?.nonce, (this as Transaction.Transfer).nonce)
                        assertEquals(pagedResult[i].txStatus, this.status)
                        assertEquals((pagedResult[i].txInfo as Transfer).direction != TransactionDirection.OUTGOING, incoming)
                    }
                    is Custom -> {
                        assertEquals(pagedResult[i].executionInfo?.nonce, (this as Transaction.Custom).nonce)
                        assertEquals(pagedResult[i].txStatus, this.status)
                        assertEquals((pagedResult[i].txInfo as Custom).dataSize, "${dataSize}")
                    }
                    is SettingsChange -> {
                        assertEquals(pagedResult[i].executionInfo?.nonce, (this as Transaction.SettingsChange).nonce)
                        assertEquals(pagedResult[i].txStatus, this.status)
                        assertEquals((pagedResult[i].txInfo as SettingsChange).dataDecoded, dataDecoded)
                    }
                }
            }
        }
    }

    @Test
    fun `getTransactions (all transfer types) should return Transfers`() = runBlockingTest {
        val pagedResult = listOf(
            buildGateTransactionDto(txInfo = buildTransferTxInfo(transferInfo = buildTransferInfoERC20())),
            buildGateTransactionDto(txInfo = buildTransferTxInfo(transferInfo = buildTransferInfoEther())),
            buildGateTransactionDto(txInfo = buildTransferTxInfo(transferInfo = buildTransferInfoERC721()))
        )
        coEvery { gatewayApi.loadTransactions(any()) } returns Page(1, null, null, pagedResult)

        val actual = transactionRepository.getTransactions(defaultSafeAddress)

        assertEquals(3, actual.results.size)
        (0..2).forEach { i ->
            with(actual.results[i] as Transaction.Transfer) {
                assertEquals(pagedResult[i].executionInfo?.nonce, this.nonce)
                when ((pagedResult[i].txInfo as Transfer).transferInfo) {
                    is Erc20Transfer -> {
                        assertEquals(ServiceTokenInfo.TokenType.ERC20, this.tokenInfo?.type)
                        assertEquals(((pagedResult[i].txInfo as Transfer).transferInfo as Erc20Transfer).value, value.asDecimalString())
                    }
                    is Erc721Transfer -> {
                        assertEquals(ServiceTokenInfo.TokenType.ERC721, this.tokenInfo?.type)
                        assertEquals(1.toBigInteger(), value)
                    }
                    is EtherTransfer -> {
                        assertEquals(null, this.tokenInfo?.type)
                        assertEquals(((pagedResult[i].txInfo as Transfer).transferInfo as EtherTransfer).value, value.asDecimalString())
                    }
                }
            }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `getTransactions (unknown txInfo) should throw exception Transfers`() = runBlockingTest {
        val transactionDto = buildGateTransactionDto()
        val pagedResult = listOf(
            buildGateTransactionDto(txInfo = object : TransactionInfo {
                override val type: GateTransactionType
                    get() = GateTransactionType.Transfer
            })
        )
        coEvery { gatewayApi.loadTransactions(any()) } returns Page(1, null, null, pagedResult)

        val actual = transactionRepository.getTransactions(defaultSafeAddress)

    }

    @Test
    fun `getTransactions (unknown transferInfo) should have value 0`() = runBlockingTest {
        val transactionDto = buildGateTransactionDto()
        val pagedResult = listOf(
            buildGateTransactionDto(txInfo = buildTransferTxInfo(transferInfo = object : TransferInfo {
                override val type: GateTransferType
                    get() = GateTransferType.ETHER
            }))
        )
        coEvery { gatewayApi.loadTransactions(any()) } returns Page(1, null, null, pagedResult)

        val actual = transactionRepository.getTransactions(defaultSafeAddress)
        assertEquals(1, actual.results.size)
        with(actual.results[0] as Transaction.Transfer) {
            assertEquals(0.toBigInteger(), value)
        }
    }

    @Test
    fun `dataSizeBytes (one byte data) should return 1`() {
        assertEquals("0x0A".dataSizeBytes(), 1L)
    }

    @Test
    fun `dataSizeBytes (zero byte data) should return 0`() {
        assertEquals("0x".dataSizeBytes(), 0L)
    }

    @Test
    fun `dataSizeBytes (5 byte data) should return 5`() {
        assertEquals("0x0123456789".dataSizeBytes(), 5L)
    }

    @Test
    fun `hexStringNullOrEmpty (is 0x) should return true`() {
        assertTrue("0x".hexStringNullOrEmpty())
    }

    @Test
    fun `hexStringNullOrEmpty (is null) should return true`() {
        assertTrue(null.hexStringNullOrEmpty())
    }

    @Test
    fun `hexStringNullOrEmpty (is 0x1A) should return false`() {
        assertFalse("0x1A".hexStringNullOrEmpty())
    }

    @Test
    fun `getValueByName (single param) should return right value`() {
        val params = listOf(ParamsDto(name = "foo", type = "uint256", value = "12"))

        val result = params.getValueByName("foo")

        assertEquals("12", result)
    }

    @Test
    fun `getValueByName (several params) should return right value`() {
        val params = listOf(
            ParamsDto(name = "foo", type = "uint256", value = "1"),
            ParamsDto(name = "bar", type = "uint256", value = "2"),
            ParamsDto(name = "baz", type = "uint256", value = "3")
        )

        val result = params.getValueByName("bar")

        assertEquals("2", result)
    }

    @Test
    fun `getValueByName (unavailable name) should return null`() {
        val params = listOf(ParamsDto(name = "foo", type = "uint256", value = "12"))

        val result = params.getValueByName("bar")

        assertEquals(null, result)
    }

    private fun buildGateTransactionDto(
        id: String = "1234",
        status: TransactionStatus = TransactionStatus.SUCCESS,
        txInfo: TransactionInfo = buildTransferTxInfo()
    ): GateTransactionDto =
        GateTransactionDto(
            id = id,
            txStatus = status,
            txInfo = txInfo,
            executionInfo = ExecutionInfo(
                nonce = 1.toBigInteger(),
                confirmationsSubmitted = 2,
                confirmationsRequired = 2
            ),
            timestamp = 1L
        )

    private fun buildCustomTxInfo(
        value: String = "1",
        to: Solidity.Address = "0x1234".asEthereumAddress()!!,
        dataSize: String = "96"
    ): Custom =
        Custom(
            type = GateTransactionType.SettingsChange,
            value = value,
            to = to,
            dataSize = dataSize
        )

    private fun buildSettingsChangeTxInfo(
        dataDecoded: DataDecodedDto = DataDecodedDto("defaultMethod", listOf())
    ): SettingsChange =
        SettingsChange(
            type = GateTransactionType.SettingsChange,
            dataDecoded = dataDecoded
        )

    private fun buildTransferTxInfo(
        sender: Solidity.Address = defaultFromAddress,
        recipient: Solidity.Address = defaultToAddress,
        direction: TransactionDirection = TransactionDirection.OUTGOING,
        transferInfo: TransferInfo = buildTransferInfoERC20()
    ): Transfer =
        Transfer(
            type = GateTransactionType.Transfer,
            sender = sender,
            recipient = recipient,
            direction = direction,
            transferInfo = transferInfo
        )

    private fun buildTransferInfoERC20(
        value: String = "10000000",
        symbol: String = "WETH",
        name: String = "Wrapped Ether",
        address: Solidity.Address = "0x1234".asEthereumAddress()!!,
        uri: String = "https://www.erc20",
        decimals: Int? = 18
    ): TransferInfo =
        Erc20Transfer(
            type = GateTransferType.ERC20,
            value = value,
            tokenSymbol = symbol,
            tokenName = name,
            tokenAddress = address,
            logoUri = uri,
            decimals = decimals
        )

    private fun buildTransferInfoERC721(
        symbol: String = "CK",
        name: String = "Crypto Kitties",
        address: Solidity.Address = "0x123456".asEthereumAddress()!!,
        uri: String = "https://www.erc721",
        id: String = "id"
    ): TransferInfo =
        Erc721Transfer(
            type = GateTransferType.ERC721,
            tokenSymbol = symbol,
            tokenName = name,
            tokenAddress = address,
            logoUri = uri,
            tokenId = id
        )

    private fun buildTransferInfoEther(
        value: String = "0"
    ): TransferInfo = EtherTransfer(
        type = GateTransferType.ETHER,
        value = value
    )
}
