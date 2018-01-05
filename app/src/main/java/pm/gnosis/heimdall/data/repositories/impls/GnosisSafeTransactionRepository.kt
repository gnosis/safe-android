package pm.gnosis.heimdall.data.repositories.impls

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.heimdall.GnosisSafe
import pm.gnosis.heimdall.accounts.base.models.Signature
import pm.gnosis.heimdall.accounts.base.repositories.AccountsRepository
import pm.gnosis.heimdall.data.db.GnosisAuthenticatorDb
import pm.gnosis.heimdall.data.db.models.TransactionDescriptionDb
import pm.gnosis.heimdall.data.db.models.TransactionPublishStatusDb
import pm.gnosis.heimdall.data.remote.BulkRequest
import pm.gnosis.heimdall.data.remote.EthereumJsonRpcRepository
import pm.gnosis.heimdall.data.remote.models.TransactionCallParams
import pm.gnosis.heimdall.data.repositories.TransactionRepository
import pm.gnosis.heimdall.data.repositories.TransactionRepository.PublishStatus
import pm.gnosis.heimdall.data.repositories.models.GasEstimate
import pm.gnosis.model.Solidity
import pm.gnosis.model.SolidityBase
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.utils.*
import java.math.BigInteger
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GnosisSafeTransactionRepository @Inject constructor(
        authenticatorDb: GnosisAuthenticatorDb,
        private val accountsRepository: AccountsRepository,
        private val ethereumJsonRpcRepository: EthereumJsonRpcRepository
) : TransactionRepository {

    private val descriptionsDao = authenticatorDb.descriptionsDao()

    override fun calculateHash(safeAddress: BigInteger, transaction: Transaction): Single<ByteArray> =
            Single.fromCallable {
                val to = transaction.address.asEthereumAddressString().removeHexPrefix()
                val value = transaction.value?.value.paddedHexString()
                val data = transaction.data?.removeHexPrefix() ?: ""
                val operation = BigInteger.ZERO.paddedHexString(2) // Call
                val nonce = (transaction.nonce ?: DEFAULT_NONCE).paddedHexString()
                hash(safeAddress, to, value, data, operation, nonce)
            }.subscribeOn(Schedulers.computation())

    private fun BigInteger?.paddedHexString(padding: Int = 64): String {
        return (this?.toString(16) ?: "").padStart(padding, '0')
    }

    private fun hash(safeAddress: BigInteger, vararg parts: String): ByteArray {
        val initial = StringBuilder().append(ERC191_BYTE).append(safeAddress.asEthereumAddressString().removeHexPrefix())
        return Sha3Utils.keccak(parts.fold(initial, { acc, part -> acc.append(part) }).toString().hexToByteArray())
    }

    override fun loadExecuteInformation(safeAddress: BigInteger, transaction: Transaction): Single<TransactionRepository.ExecuteInformation> =
            accountsRepository.loadActiveAccount()
                    .flatMap { account -> calculateHash(safeAddress, transaction).map { account to it } }
                    .flatMap { (account, txHash) ->
                        val request = TransactionInfoRequest(
                                BulkRequest.SubRequest(TransactionCallParams(
                                        to = safeAddress.asEthereumAddressString(),
                                        data = GnosisSafe.IsOwner.encode(Solidity.Address(account.address))).callRequest(0),
                                        { GnosisSafe.IsOwner.decode(it.checkedResult()).param0.value }
                                ),
                                BulkRequest.SubRequest(TransactionCallParams(
                                        to = safeAddress.asEthereumAddressString(),
                                        data = GnosisSafe.Threshold.encode()).callRequest(1),
                                        { GnosisSafe.Threshold.decode(it.checkedResult()).param0.value.toInt() }
                                ),
                                BulkRequest.SubRequest(TransactionCallParams(
                                        to = safeAddress.asEthereumAddressString(),
                                        data = GnosisSafe.Nonce.encode()).callRequest(2),
                                        { GnosisSafe.Nonce.decode(it.checkedResult()).param0.value }
                                ),
                                BulkRequest.SubRequest(TransactionCallParams(
                                        to = safeAddress.asEthereumAddressString(),
                                        data = GnosisSafe.GetOwners.encode()).callRequest(3),
                                        { GnosisSafe.GetOwners.decode(it.checkedResult()).param0.items.map { it.value } }
                                )
                        )
                        ethereumJsonRpcRepository.bulk(request)
                                .map {
                                    TransactionRepository.ExecuteInformation(
                                            txHash.toHexString(),
                                            it.isOwner.value!!,
                                            it.requiredConfirmation.value!!,
                                            it.nonce.value!!,
                                            it.owners.value!!
                                    )
                                }
                                .singleOrError()
                    }

    override fun sign(safeAddress: BigInteger, transaction: Transaction): Single<Signature> =
            calculateHash(safeAddress, transaction).flatMap {
                accountsRepository.sign(it)
            }

    override fun parseSignature(safeAddress: BigInteger, transaction: Transaction, encodedSignature: String): Single<Pair<BigInteger, Signature>> =
            calculateHash(safeAddress, transaction).flatMap {
                val signature = Signature.from(encodedSignature)
                accountsRepository.recover(it, signature).map { it to signature }
            }

    override fun estimateFees(safeAddress: BigInteger, transaction: Transaction, signatures: Map<BigInteger, Signature>): Single<GasEstimate> =
            buildExecuteTransaction(safeAddress, transaction, signatures)
                    .flatMap { confirmAndExecuteTransaction ->
                        accountsRepository.loadActiveAccount().map { it to confirmAndExecuteTransaction }
                    }
                    .flatMap { (account, confirmAndExecuteTransaction) ->
                        ethereumJsonRpcRepository.getTransactionParameters(account.address,
                                TransactionCallParams(
                                        to = confirmAndExecuteTransaction.address.asEthereumAddressString(),
                                        data = confirmAndExecuteTransaction.data))
                                .map { GasEstimate(it.gas, Wei(it.gasPrice)) }
                                .singleOrError()
                    }

    override fun submit(safeAddress: BigInteger, transaction: Transaction, signatures: Map<BigInteger, Signature>, overrideGasPrice: Wei?): Completable =
            buildExecuteTransaction(safeAddress, transaction, signatures)
                    .flatMapObservable { submitSignedTransaction(it, overrideGasPrice) }
                    .flatMapSingle { addLocalTransaction(safeAddress, transaction, it) }
                    .ignoreElements()

    private fun buildExecuteTransaction(safeAddress: BigInteger, innerTransaction: Transaction, signatures: Map<BigInteger, Signature>): Single<Transaction> =
            accountsRepository.loadActiveAccount().flatMap { account ->
                val sortedAddresses = setOf(account.address).plus(signatures.keys).sorted()
                val senderIndex = sortedAddresses.indexOf(account.address)
                val vList = mutableListOf<Solidity.UInt8>()
                val rList = mutableListOf<Solidity.Bytes32>()
                val sList = mutableListOf<Solidity.Bytes32>()
                sortedAddresses.forEach {
                    signatures[it]?.let {
                        vList.add(Solidity.UInt8(BigInteger.valueOf(it.v.toLong())))
                        rList.add(Solidity.Bytes32(it.r.toBytes(32)))
                        sList.add(Solidity.Bytes32(it.s.toBytes(32)))
                    }
                }
                Single.fromCallable {
                    val to = Solidity.Address(innerTransaction.address)
                    val value = Solidity.UInt256(innerTransaction.value?.value ?: BigInteger.ZERO)
                    val data = Solidity.Bytes(innerTransaction.data?.hexStringToByteArrayOrNull() ?: ByteArray(0))
                    val operation = Solidity.UInt8(DEFAULT_OPERATION)
                    val confirmData = GnosisSafe.ExecuteTransaction.encode(to, value, data, operation,
                            SolidityBase.Vector(vList), SolidityBase.Vector(rList), SolidityBase.Vector(sList),
                            SolidityBase.Vector(listOf(Solidity.Address(account.address))), SolidityBase.Vector(listOf(Solidity.UInt256(BigInteger.valueOf(senderIndex.toLong())))))
                    Transaction(safeAddress, data = confirmData)
                }
            }

    private fun submitSignedTransaction(transaction: Transaction, overrideGasPrice: Wei? = null): Observable<String> =
            accountsRepository.loadActiveAccount()
                    .flatMapObservable {
                        ethereumJsonRpcRepository.getTransactionParameters(it.address,
                                TransactionCallParams(
                                        to = transaction.address.asEthereumAddressString(),
                                        data = transaction.data))
                    }
                    .flatMapSingle {
                        accountsRepository.signTransaction(transaction.copy(nonce = it.nonce, gas = it.gas, gasPrice = overrideGasPrice?.value ?: it.gasPrice))
                    }
                    .flatMap { ethereumJsonRpcRepository.sendRawTransaction(it) }

    private fun addLocalTransaction(safeAddress: BigInteger, transaction: Transaction, txChainHash: String): Single<String> =
            calculateHash(safeAddress, transaction).flatMap {
                Single.fromCallable {
                    val transactionUuid = UUID.randomUUID().toString()
                    descriptionsDao.insert(
                            TransactionDescriptionDb(transactionUuid, safeAddress, transaction.address,
                                    transaction.value?.value ?: BigInteger.ZERO,
                                    transaction.data ?: "", DEFAULT_OPERATION,
                                    transaction.nonce ?: DEFAULT_NONCE, System.currentTimeMillis(), null, it.toHexString())
                    )
                    descriptionsDao.insert(
                            TransactionPublishStatusDb(transactionUuid, txChainHash, null)
                    )
                    transactionUuid
                }
            }

    override fun observePublishStatus(id: String): Observable<PublishStatus> =
            descriptionsDao.observeStatus(id)
                    .toObservable()
                    .switchMap { status ->
                        status.success?.let {
                            Observable.just(it)
                        } ?: ethereumJsonRpcRepository.getTransactionReceipt(status.transactionId)
                                .flatMap {
                                    it.status?.let {
                                        Observable.just(it == BigInteger.ONE)
                                    } ?: Observable.error<Boolean>(IllegalStateException())
                                }
                                .retryWhen {
                                    it.delay(20, TimeUnit.SECONDS)
                                }
                                .map {
                                    descriptionsDao.update(status.apply { success = it })
                                    it
                                }
                    }
                    .map { if (it) PublishStatus.SUCCESS else PublishStatus.FAILED }
                    .startWith(PublishStatus.PENDING)
                    .onErrorReturnItem(PublishStatus.UNKNOWN)

    private class TransactionInfoRequest(
            val isOwner: SubRequest<Boolean>,
            val requiredConfirmation: SubRequest<Int>,
            val nonce: SubRequest<BigInteger>,
            val owners: SubRequest<List<BigInteger>>
    ) : BulkRequest(isOwner, requiredConfirmation, nonce, owners)

    companion object {
        private const val ERC191_BYTE = "19"
        private val DEFAULT_OPERATION = BigInteger.ZERO // Call
        private val DEFAULT_NONCE = BigInteger.ZERO
    }

}