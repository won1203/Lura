package com.won1203.lura.data

object SoundRepositoryProvider {
    @Volatile
    private var repository: SoundRepository? = null

    fun get(): SoundRepository =
        repository ?: synchronized(this) {
            repository ?: LocalSoundRepository()
                .also { repository = it }
        }
}
