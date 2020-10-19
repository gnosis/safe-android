package io.gnosis.data.utils

import io.gnosis.data.models.DetailedExecutionInfo
import io.gnosis.data.models.TransactionDetails
import io.gnosis.data.models.TransactionInfo
import io.gnosis.data.models.TransferInfo
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.model.Solidity
import pm.gnosis.utils.hexToByteArray
import pm.gnosis.utils.toHex
import java.math.BigInteger

private const val ERC191_BYTE = "19"
private const val ERC191_VERSION = "01"

fun calculateSafeTxHash(
    safeAddress: Solidity.Address, transaction: TransactionDetails, executionInfo: DetailedExecutionInfo.MultisigExecutionDetails
): ByteArray? {

    val to = when (val txInfo = transaction.txInfo) {
        is TransactionInfo.Transfer -> {
            when (val transferInfo = txInfo.transferInfo) {
                is TransferInfo.Erc20Transfer -> {
                    transferInfo.tokenAddress
                }
                is TransferInfo.Erc721Transfer -> {
                    transferInfo.tokenAddress
                }
                is TransferInfo.EtherTransfer -> {
                    txInfo.recipient
                }
            }
        }
        is TransactionInfo.Custom -> {
            txInfo.to
        }
        is TransactionInfo.SettingsChange -> {
            safeAddress
        }
        else -> {
            throw UnsupportedTransactionType(transaction::javaClass.name)
        }
    }.value.paddedHexString()

    val value = transaction.txData?.value.paddedHexString()
    val data = Sha3Utils.keccak(transaction.txData?.hexData?.hexToByteArray() ?: ByteArray(0)).toHex().padStart(64, '0')
    val operationString = (transaction.txData?.operation?.id?.toBigInteger() ?: BigInteger.ZERO).paddedHexString()
    val gasPriceString = executionInfo.gasPrice.paddedHexString()
    val txGasString = executionInfo.safeTxGas.paddedHexString()
    val dataGasString = executionInfo.baseGas.paddedHexString()
    val gasTokenString = executionInfo.gasToken.value.paddedHexString()
    val refundReceiverString = BigInteger.ZERO.paddedHexString()
    val nonce = executionInfo.nonce.paddedHexString()

    return hash(
        safeAddress,
        to,
        value,
        data,
        operationString,
        txGasString,
        dataGasString,
        gasPriceString,
        gasTokenString,
        refundReceiverString,
        nonce
    )
}

private fun hash(safeAddress: Solidity.Address, vararg parts: String): ByteArray {
    val initial = StringBuilder().append(ERC191_BYTE).append(ERC191_VERSION).append(
        domainHash(safeAddress)
    ).append(valuesHash(parts))
    return Sha3Utils.keccak(initial.toString().hexToByteArray())
}

private fun domainHash(safeAddress: Solidity.Address) =
    Sha3Utils.keccak(
        "0x035aff83d86937d35b32e04f0ddc6ff469290eef2f1b692d8a815c89404d4749${safeAddress.value.paddedHexString()}".hexToByteArray()
    ).toHex()

private fun valuesHash(parts: Array<out String>) =
    parts.fold(StringBuilder().append(getTypeHash())) { acc, part ->
        acc.append(part)
    }.toString().run {
        Sha3Utils.keccak(hexToByteArray()).toHex()
    }

private fun getTypeHash() = "0xbb8310d486368db6bd6f849402fdd73ad53d316b5a4b2644ad6efe0f941286d8"

private fun BigInteger?.paddedHexString(padding: Int = 64): String {
    return (this?.toString(16) ?: "").padStart(padding, '0')
}

class UnsupportedTransactionType(message: String? = null) : Throwable(message)