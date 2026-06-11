package com.ew.handscript.data.local

import android.content.Context
import androidx.room.Room
import com.ew.handscript.core.render.GlyphCache
import com.ew.handscript.core.render.GlyphCacheImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FontDatabase {
        return Room.databaseBuilder(
            context,
            FontDatabase::class.java,
            FontDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideGlyphDao(database: FontDatabase): GlyphDao {
        return database.glyphDao()
    }

    @Provides
    @Singleton
    fun provideSourceDocumentDao(database: FontDatabase): SourceDocumentDao {
        return database.sourceDocumentDao()
    }

    @Provides
    @Singleton
    fun provideExportHistoryDao(database: FontDatabase): ExportHistoryDao {
        return database.exportHistoryDao()
    }

    @Provides
    @Singleton
    fun provideGlyphCache(): GlyphCache {
        return GlyphCacheImpl()
    }
}
