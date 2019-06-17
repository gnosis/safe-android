package pm.gnosis.heimdall.ui.safe.create

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import pm.gnosis.crypto.utils.Sha3Utils
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.GnosisSafe
import pm.gnosis.heimdall.data.remote.RelayServiceApi
import pm.gnosis.heimdall.data.remote.models.RelaySafeCreation
import pm.gnosis.heimdall.data.remote.models.RelaySafeCreationParams
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.model.Solidity
import pm.gnosis.model.SolidityBase
import pm.gnosis.heimdall.data.repositories.AccountsRepository
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.hexToByteArray
import pm.gnosis.utils.toBytes
import java.math.BigInteger
import javax.inject.Inject

class CreateSafeConfirmRecoveryPhraseViewModel @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val relayServiceApi: RelayServiceApi,
    private val gnosisSafeRepository: GnosisSafeRepository,
    private val tokenRepository: TokenRepository
) : CreateSafeConfirmRecoveryPhraseContract() {

    private var browserExtensionAddress: Solidity.Address? = null
    private var safeOwner: AccountsRepository.SafeOwner? = null

    private val threshold get() = browserExtensionAddress?.let { 2 } ?: 1

    override fun setup(browserExtensionAddress: Solidity.Address?, safeOwner: AccountsRepository.SafeOwner?) {
        this.browserExtensionAddress = browserExtensionAddress
        this.safeOwner = safeOwner
    }

    override fun createSafe(): Single<Solidity.Address> =
        (safeOwner?.let { Single.just(it) } ?: accountsRepository.createOwner())
            .flatMap { owner ->
                accountsRepository.createOwnersFromPhrase(getRecoveryPhrase(), listOf(0, 1))
                    .map { accounts ->
                        OwnerData(
                            owner,
                            listOfNotNull(
                                owner.address,
                                browserExtensionAddress,
                                accounts[0].address,
                                accounts[1].address
                            )
                        )
                    }
            }
            .zipWith(
                tokenRepository.loadPaymentToken(),
                BiFunction<OwnerData, ERC20Token, Pair<OwnerData, ERC20Token>> { ownerData, token: ERC20Token -> ownerData to token })
            .map { (ownerData, paymentToken) ->

                ownerData to RelaySafeCreationParams(
                    owners = ownerData.fullListOfOwners,
                    threshold = threshold,
                    saltNonce = System.nanoTime(),
                    paymentToken = paymentToken.address
                )
            }
            .flatMap { (ownerData, request) -> relayServiceApi.safeCreation(request).map { Triple(ownerData, request, it) } }
            .flatMap { (ownerData, request, response) ->
                assertResponse(request, response)
                gnosisSafeRepository.addPendingSafe(response.safe, null, response.payment, response.paymentToken)
                    .andThen(gnosisSafeRepository.saveOwner(response.safe, ownerData.owner))
                    .andThen(Single.just(response.safe))
            }
            .subscribeOn(Schedulers.io())

    private fun assertResponse(request: RelaySafeCreationParams, response: RelaySafeCreation) {
        val paymentToken = response.paymentToken
        if (request.paymentToken != paymentToken)
            throw IllegalStateException("Unexpected payment token returned")
        if (response.masterCopy != MASTER_COPY_ADDRESS)
            throw IllegalStateException("Unexpected master copy returned")
        if (response.proxyFactory != PROXY_FACTORY_ADDRESS)
            throw IllegalStateException("Unexpected proxy factory returned")
        if (response.paymentReceiver != TX_ORIGIN_ADDRESS && response.paymentReceiver != FUNDER_ADDRESS)
            throw IllegalStateException("Unexpected payment receiver returned")

        // Web3py seems to add an empty body for empty data, I would say this is a bug
        // see https://github.com/gnosis/bivrost-kotlin/issues/49
        val setupData = GnosisSafe.Setup.encode(
            _owners = SolidityBase.Vector(request.owners),
            _threshold = Solidity.UInt256(request.threshold.toBigInteger()),
            to = Solidity.Address(BigInteger.ZERO),
            data = Solidity.Bytes(byteArrayOf()),
            payment = Solidity.UInt256(response.payment),
            paymentToken = response.paymentToken,
            paymentReceiver = response.paymentReceiver
        ) + "0000000000000000000000000000000000000000000000000000000000000000"

        if (setupData != response.setupData)
            throw IllegalStateException("Unexpected setup data returned")

        val setupDataHash = Sha3Utils.keccak(setupData.hexToByteArray())
        val salt = Sha3Utils.keccak(setupDataHash + Solidity.UInt256(request.saltNonce.toBigInteger()).encode().hexToByteArray())


        val deploymentCode = PROXY_CODE + MASTER_COPY_ADDRESS.encode()
        val codeHash = Sha3Utils.keccak(deploymentCode.hexToByteArray())
        val create2Hash = Sha3Utils.keccak(byteArrayOf(0xff.toByte()) + PROXY_FACTORY_ADDRESS.value.toBytes(20) + salt + codeHash)
        val address = Solidity.Address(BigInteger(1, create2Hash.copyOfRange(12, 32)))

        if (address != response.safe)
            throw IllegalStateException("Unexpected safe address returned")
    }

    private data class OwnerData(
        val owner: AccountsRepository.SafeOwner,
        val fullListOfOwners: List<Solidity.Address>
    )

    companion object {
        private val MASTER_COPY_ADDRESS = BuildConfig.CURRENT_SAFE_MASTER_COPY_ADDRESS.asEthereumAddress()!!
        private val PROXY_FACTORY_ADDRESS = BuildConfig.PROXY_FACTORY_ADDRESS.asEthereumAddress()!!
        private val FUNDER_ADDRESS = BuildConfig.SAFE_CREATION_FUNDER.asEthereumAddress()!!
        private val TX_ORIGIN_ADDRESS = "0x0".asEthereumAddress()!!
        private const val PROXY_CODE =
            "0x608060405234801561001057600080fd5b506040516020806101a88339810180604052602081101561003057600080fd5b8101908080519060200190929190505050600073ffffffffffffffffffffffffffffffffffffffff168173ffffffffffffffffffffffffffffffffffffffff1614156100c7576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004018080602001828103825260248152602001806101846024913960400191505060405180910390fd5b806000806101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff16021790555050606e806101166000396000f3fe608060405273ffffffffffffffffffffffffffffffffffffffff600054163660008037600080366000845af43d6000803e6000811415603d573d6000fd5b3d6000f3fea165627a7a723058201e7d648b83cfac072cbccefc2ffc62a6999d4a050ee87a721942de1da9670db80029496e76616c6964206d617374657220636f707920616464726573732070726f7669646564"
    }
}
