package com.example.lura.data

import com.example.lura.data.remote.BackendApiProvider
import com.example.lura.data.remote.BackendSoundRepository

object SoundRepositoryProvider {
    @Volatile
    private var repository: SoundRepository? = null

    fun get(): SoundRepository =
        repository ?: synchronized(this) {
            repository ?: BackendSoundRepository(
                api = BackendApiProvider.luraBackendApi
            ).also { repository = it }
        }
}
