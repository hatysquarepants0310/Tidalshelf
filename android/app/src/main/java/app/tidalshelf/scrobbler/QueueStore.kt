package app.tidalshelf.scrobbler

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingPlay(
    val id: Long,
    val artist: String,
    val title: String,
    val album: String,
    val durationSec: Int,
    val timestamp: Long, // epoch segundos en que EMPEZÓ la reproducción
    val lastfmState: Int,
    val ytState: Int,
    val attempts: Int,
)

/**
 * Cola persistente de reproducciones detectadas, para que nada se pierda si no
 * hay red o si el proceso muere. Cada fila lleva el estado de sus dos destinos.
 */
class QueueStore(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "queue.db", null, 1) {

    companion object {
        const val STATE_PENDING = 0
        const val STATE_DONE = 1
        const val STATE_MISS = 2      // sin match en YT Music: no reintentar
        const val STATE_DISABLED = 3  // destino apagado cuando se detectó la escucha
        const val MAX_ATTEMPTS = 25

        @Volatile private var instance: QueueStore? = null
        fun get(ctx: Context): QueueStore =
            instance ?: synchronized(this) {
                instance ?: QueueStore(ctx).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE plays (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                artist TEXT NOT NULL,
                title TEXT NOT NULL,
                album TEXT NOT NULL,
                duration_sec INTEGER NOT NULL,
                ts INTEGER NOT NULL,
                lastfm_state INTEGER NOT NULL,
                yt_state INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun enqueue(
        artist: String, title: String, album: String, durationSec: Int, timestamp: Long,
        lastfmEnabled: Boolean, ytEnabled: Boolean,
    ) {
        val values = ContentValues().apply {
            put("artist", artist)
            put("title", title)
            put("album", album)
            put("duration_sec", durationSec)
            put("ts", timestamp)
            put("lastfm_state", if (lastfmEnabled) STATE_PENDING else STATE_DISABLED)
            put("yt_state", if (ytEnabled) STATE_PENDING else STATE_DISABLED)
        }
        writableDatabase.insert("plays", null, values)
    }

    private fun rowsWhere(where: String): List<PendingPlay> {
        val list = mutableListOf<PendingPlay>()
        readableDatabase.rawQuery(
            "SELECT id, artist, title, album, duration_sec, ts, lastfm_state, yt_state, attempts " +
                "FROM plays WHERE $where ORDER BY ts ASC LIMIT 50", null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    PendingPlay(
                        id = c.getLong(0), artist = c.getString(1), title = c.getString(2),
                        album = c.getString(3), durationSec = c.getInt(4), timestamp = c.getLong(5),
                        lastfmState = c.getInt(6), ytState = c.getInt(7), attempts = c.getInt(8),
                    )
                )
            }
        }
        return list
    }

    fun pendingLastfm(): List<PendingPlay> = rowsWhere("lastfm_state = $STATE_PENDING")
    fun pendingYt(): List<PendingPlay> = rowsWhere("yt_state = $STATE_PENDING")

    fun setLastfmState(id: Long, state: Int) =
        writableDatabase.execSQL("UPDATE plays SET lastfm_state = ? WHERE id = ?", arrayOf(state, id))

    fun setYtState(id: Long, state: Int) =
        writableDatabase.execSQL("UPDATE plays SET yt_state = ? WHERE id = ?", arrayOf(state, id))

    fun bumpAttempts(id: Long) {
        writableDatabase.execSQL("UPDATE plays SET attempts = attempts + 1 WHERE id = ?", arrayOf(id))
        // rendirse tras demasiados intentos para no acumular basura eterna
        writableDatabase.execSQL(
            "UPDATE plays SET lastfm_state = CASE WHEN lastfm_state = $STATE_PENDING THEN $STATE_MISS ELSE lastfm_state END, " +
                "yt_state = CASE WHEN yt_state = $STATE_PENDING THEN $STATE_MISS ELSE yt_state END " +
                "WHERE id = ? AND attempts >= $MAX_ATTEMPTS", arrayOf(id)
        )
    }

    fun countWhere(where: String): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM plays WHERE $where", null).use { c ->
            c.moveToFirst()
            return c.getLong(0)
        }
    }

    fun recentMisses(): List<PendingPlay> =
        rowsWhere("yt_state = $STATE_MISS OR lastfm_state = $STATE_MISS")

    fun prune() {
        // conservar solo lo aún pendiente y un historial corto de terminados
        writableDatabase.execSQL(
            "DELETE FROM plays WHERE lastfm_state != $STATE_PENDING AND yt_state != $STATE_PENDING " +
                "AND id NOT IN (SELECT id FROM plays ORDER BY id DESC LIMIT 200)"
        )
    }
}
