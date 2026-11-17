package io.github.carvalhoxx.gecko.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Game::class],
    version = 1,
    exportSchema = true
)
abstract class GeckoDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}
