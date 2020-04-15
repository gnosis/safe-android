package io.gnosis.safe.di.modules

import dagger.Module
import dagger.Provides
import io.gnosis.data.db.daos.SafeDao
import io.gnosis.data.repositories.SafeRepository
import javax.inject.Singleton

@Module
class RepositoryModule {

    @Provides
    @Singleton
    fun provideSafeRepository(safeDao: SafeDao) = SafeRepository(safeDao)
}
