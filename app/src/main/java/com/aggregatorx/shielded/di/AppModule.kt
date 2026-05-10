package com.aggregatorx.shielded.di

import android.content.Context
import androidx.room.Room
import com.aggregatorx.shielded.data.db.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ShieldDatabase =
        Room.databaseBuilder(ctx, ShieldDatabase::class.java, "shield.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideProviderDao(db: ShieldDatabase): ProviderDao = db.providerDao()
    @Provides fun provideResultDao(db: ShieldDatabase): ResultDao = db.resultDao()
    @Provides fun provideAuditDao(db: ShieldDatabase): AuditDao = db.auditDao()
    @Provides fun provideTokenDao(db: ShieldDatabase): TokenDao = db.tokenDao()
}
