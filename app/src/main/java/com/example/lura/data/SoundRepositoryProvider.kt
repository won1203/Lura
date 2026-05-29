package com.example.lura.data

import com.example.lura.data.remote.BackendApiProvider
import com.example.lura.data.remote.BackendSoundRepository

object SoundRepositoryProvider {
    @Volatile
    private var repository: SoundRepository? = null

    fun get(): SoundRepository =
        repository ?: synchronized(this) {
            repository ?: CatalogBackedSoundRepository(
                catalog = DefaultSoundRepository,
                remote = BackendSoundRepository(
                    apis = BackendApiProvider.luraBackendApis
                )
            ).also { repository = it }
        }
}
