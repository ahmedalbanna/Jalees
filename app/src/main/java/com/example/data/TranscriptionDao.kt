package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM sessions_table ORDER BY start_timestamp DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions_table WHERE id = :id")
    fun getSessionById(id: Int): Flow<Session?>

    @Query("SELECT * FROM sessions_table WHERE id = :id")
    suspend fun getSessionByIdSync(id: Int): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Update
    suspend fun updateSession(session: Session)

    @Query("DELETE FROM sessions_table WHERE id = :id")
    suspend fun deleteSessionById(id: Int)

    @Query("SELECT * FROM transcripts_table WHERE session_id = :sessionId ORDER BY relative_time ASC")
    fun getTranscriptsForSession(sessionId: Int): Flow<List<Transcript>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: Transcript)

    @Query("SELECT DISTINCT s.* FROM sessions_table s LEFT JOIN transcripts_table t ON s.id = t.session_id WHERE s.title LIKE :query OR t.text_content LIKE :query ORDER BY s.start_timestamp DESC")
    fun searchSessions(query: String): Flow<List<Session>>
}
