package pm.gnosis.heimdall.ui.safe.mnemonic

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import pm.gnosis.crypto.KeyPair
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.GnosisSafePersonalEdition
import pm.gnosis.heimdall.MultiSend
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.SafeInfo
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.mnemonic.Bip39ValidationResult
import pm.gnosis.model.Solidity
import pm.gnosis.model.SolidityBase
import pm.gnosis.models.Transaction
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.accounts.base.repositories.AccountsRepository
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.ErrorResult
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.hexStringToByteArray
import pm.gnosis.utils.nullOnThrow
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

private typealias SigningAccounts = Pair<Pair<Solidity.Address, ByteArray>, Pair<Solidity.Address, ByteArray>>

interface RecoverSafeOwnersHelper {
    fun process(input: InputRecoveryPhraseContract.Input, safeAddress: Solidity.Address, extensionAddress: Solidity.Address):
            Observable<InputRecoveryPhraseContract.ViewUpdate>
}

@Singleton
class DefaultRecoverSafeOwnersHelper @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val bip39: Bip39,
    private val executionRepository: TransactionExecutionRepository,
    private val safeRepository: GnosisSafeRepository
) : RecoverSafeOwnersHelper {
    override fun process(
        input: InputRecoveryPhraseContract.Input,
        safeAddress: Solidity.Address,
        extensionAddress: Solidity.Address
    ): Observable<InputRecoveryPhraseContract.ViewUpdate> =
        input.retry.startWith(Unit)
            .switchMap {
                safeRepository.loadInfo(safeAddress).mapToResult()
            }
            .flatMap {
                when (it) {
                    is ErrorResult -> Observable.just(
                        InputRecoveryPhraseContract.ViewUpdate.SafeInfoError(
                            it.error
                        )
                    )
                    is DataResult ->
                        // We have the safe info so lets show the mnemonic input
                        Observable.just<InputRecoveryPhraseContract.ViewUpdate>(InputRecoveryPhraseContract.ViewUpdate.InputMnemonic)
                            .concatWith(processRecoveryPhrase(input, it.data, extensionAddress))
                }
            }

    private fun processRecoveryPhrase(
        input: InputRecoveryPhraseContract.Input,
        safeInfo: SafeInfo,
        extensionAddress: Solidity.Address
    ): Observable<InputRecoveryPhraseContract.ViewUpdate> =
        input.phrase
            .flatMapSingle {
                Single.fromCallable { bip39.mnemonicToSeed(bip39.validateMnemonic(it.toString())) }
                    .subscribeOn(Schedulers.computation())
                    .flatMap {
                        Single.zip(
                            accountsRepository.accountFromMnemonicSeed(it, 0),
                            accountsRepository.accountFromMnemonicSeed(it, 1),
                            BiFunction { account0: Pair<Solidity.Address, ByteArray>, account1: Pair<Solidity.Address, ByteArray> ->
                                // We expect that the safe has 4 owners (app, extension, 2 recovery addresses)
                                if (safeInfo.owners.size != 4) throw IllegalStateException()
                                // Check that the accounts generated by the mnemonic are owners
                                if (!safeInfo.owners.contains(account0.first) ||
                                    !safeInfo.owners.contains(account1.first)
                                ) throw IllegalArgumentException()

                                SigningAccounts(account0, account1)
                            }
                        )
                    }
                    .flatMap { signingAccounts -> accountsRepository.loadActiveAccount().map { it to signingAccounts } }
                    .map { (appAccount, signingAccounts) ->
                        buildRecoverTransaction(safeInfo, signingAccounts, appAccount.address, extensionAddress) to signingAccounts
                    }
                    .mapToResult()
            }
            .flatMap {
                when (it) {
                // If errors are thrown while parsing the mnemonic or building the data, we should map them here
                    is ErrorResult -> Observable.just(
                        when (it.error) {
                            is Bip39ValidationResult -> InputRecoveryPhraseContract.ViewUpdate.InvalidMnemonic
                            is NoNeedToRecoverSafeException -> InputRecoveryPhraseContract.ViewUpdate.NoRecoveryNecessary(safeInfo.address)
                            else -> InputRecoveryPhraseContract.ViewUpdate.WrongMnemonic
                        }
                    )
                // We successfully parsed the mnemonic and build the data, now we can show the create button and if pressed pull the data
                    is DataResult -> {
                        val (transaction, signingAccounts) = it.data
                        input.create
                            .subscribeOn(AndroidSchedulers.mainThread())
                            .switchMapSingle {
                                prepareTransaction(safeInfo, transaction, signingAccounts)
                            }.startWith(InputRecoveryPhraseContract.ViewUpdate.ValidMnemonic)
                    }
                }
            }

    private fun buildRecoverTransaction(
        safeInfo: SafeInfo,
        signingAccounts: SigningAccounts,
        appAddress: Solidity.Address,
        extensionAddress: Solidity.Address
    ): SafeTransaction {
        val newAddresses = mutableListOf<Solidity.Address>()
        val addressesToReplace = safeInfo.owners.toMutableList().apply {
            // We should not remove the recovery phrase addresses and they need to be present
            if (!remove(signingAccounts.first.first)) throw IllegalStateException()
            if (!remove(signingAccounts.second.first)) throw IllegalStateException()

            // If the extension address is not present we should add it
            if (!remove(extensionAddress)) newAddresses.add(extensionAddress)
            // If the app address is not present we should add it
            if (!remove(appAddress)) newAddresses.add(appAddress)
        }

        // We have no new addresses, nothing to do here
        if (newAddresses.isEmpty()) throw NoNeedToRecoverSafeException(safeInfo.address)
        // We should have enough addresses that can be replaced
        if (addressesToReplace.size < newAddresses.size) throw IllegalStateException()

        // We start replacing in reverse order, to avoid having conflicts when combining the transactions via multisend
        val payloads = newAddresses.map {
            val nextAddressToReplace = addressesToReplace.removeAt(addressesToReplace.size - 1)
            val pointerAddress = nullOnThrow { safeInfo.owners[safeInfo.owners.indexOf(nextAddressToReplace) - 1] } ?: SENTINEL
            val payload = GnosisSafePersonalEdition.SwapOwner.encode(pointerAddress, nextAddressToReplace, it)
            payload
        }

        return when (payloads.size) {
            1 -> SafeTransaction(
                Transaction(safeInfo.address, data = payloads.first()),
                TransactionExecutionRepository.Operation.CALL
            )
            else ->
                SafeTransaction(
                    Transaction(
                        BuildConfig.MULTI_SEND_ADDRESS.asEthereumAddress()!!, data = MultiSend.MultiSend.encode(
                            Solidity.Bytes(
                                payloads.joinToString(separator = "") {
                                    SolidityBase.encodeFunctionArguments(
                                        Solidity.UInt8(BigInteger.ZERO),
                                        safeInfo.address,
                                        Solidity.UInt256(BigInteger.ZERO),
                                        Solidity.Bytes(it.hexStringToByteArray())
                                    )
                                }.hexStringToByteArray()
                            )
                        )
                    ), TransactionExecutionRepository.Operation.DELEGATE_CALL
                )
        }
    }

    private fun prepareTransaction(safeInfo: SafeInfo, transaction: SafeTransaction, signingAccounts: SigningAccounts) =
        executionRepository.loadExecuteInformation(safeInfo.address, transaction)
            .flatMap { executionInfo ->
                executionRepository.calculateHash(safeInfo.address, executionInfo.transaction, executionInfo.txGas, executionInfo.dataGas, executionInfo.gasPrice)
                    .map { it to executionInfo }
            }
            .map<InputRecoveryPhraseContract.ViewUpdate> { (hash, executionInfo) ->
                InputRecoveryPhraseContract.ViewUpdate.RecoverData(
                    executionInfo,
                    listOf(signHash(signingAccounts.first.second, hash), signHash(signingAccounts.second.second, hash))
                )
            }
            .onErrorReturn { InputRecoveryPhraseContract.ViewUpdate.RecoverDataError(it) }

    private fun signHash(privKey: ByteArray, hash: ByteArray) =
        KeyPair.fromPrivate(privKey).sign(hash).let { Signature(it.r, it.s, it.v) }

    data class NoNeedToRecoverSafeException(val safeAddress: Solidity.Address): IllegalStateException("Safe is already in expected state!")

    companion object {
        private val SENTINEL = "0x01".asEthereumAddress()!!
    }
}
