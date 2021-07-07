package io.gnosis.data.repositories

import com.unstoppabledomains.config.network.model.Network
import com.unstoppabledomains.resolution.DomainResolution
import com.unstoppabledomains.resolution.Resolution
import com.unstoppabledomains.resolution.naming.service.NamingServiceType
import io.gnosis.data.BuildConfig.INFURA_API_KEY
import io.gnosis.data.models.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress

class UnstoppableDomainsRepository(private val resolution: DomainResolution = DummyDomainResolution()) {

    suspend fun resolve(domain: String, chainId: Int): Solidity.Address {
        val address = withContext(Dispatchers.IO) {
            providesDomainResolutionLibrary(chainId)?.getAddress(domain, "eth")
        }
        return address?.asEthereumAddress()!!
    }

    fun canResolve(chain: Chain): Boolean = providesDomainResolutionLibrary(chain.chainId) != null

    private fun providesDomainResolutionLibrary(chainId: Int): DomainResolution? {
        if (chainId != 1 && chainId != 4) {
            return null
        }
        return if (resolution is DummyDomainResolution) {
            val network = Network.getNetwork(chainId)
            try {
                Resolution.builder()
                    .chainId(NamingServiceType.CNS, network)
                    .infura(NamingServiceType.CNS, INFURA_API_KEY)
                    .build()
            } catch (throwable: Throwable) {
                DummyDomainResolution()
            }
        } else {
            resolution
        }
    }
}
