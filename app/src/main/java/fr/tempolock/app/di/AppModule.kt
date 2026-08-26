package fr.tempolock.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.tempolock.app.data.SecureSessionStore
import fr.tempolock.app.domain.LockPolicy
import fr.tempolock.app.domain.SessionStore
import fr.tempolock.app.domain.TrustedClock
import fr.tempolock.app.domain.UnlockScheduler
import fr.tempolock.app.platform.AndroidTrustedClock
import fr.tempolock.app.platform.AndroidUnlockScheduler
import fr.tempolock.app.platform.DeviceOwnerPolicy
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindSessionStore(implementation: SecureSessionStore): SessionStore

    @Binds
    @Singleton
    abstract fun bindTrustedClock(implementation: AndroidTrustedClock): TrustedClock

    @Binds
    @Singleton
    abstract fun bindLockPolicy(implementation: DeviceOwnerPolicy): LockPolicy

    @Binds
    @Singleton
    abstract fun bindUnlockScheduler(implementation: AndroidUnlockScheduler): UnlockScheduler
}
