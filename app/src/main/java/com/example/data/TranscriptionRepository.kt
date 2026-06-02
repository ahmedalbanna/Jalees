package com.example.data

import kotlinx.coroutines.flow.Flow

class TranscriptionRepository(private val dao: TranscriptionDao) {
    val allSessions: Flow<List<Session>> = dao.getAllSessions()

    fun getSession(id: Int): Flow<Session?> = dao.getSessionById(id)

    suspend fun getSessionSync(id: Int): Session? = dao.getSessionByIdSync(id)

    fun getTranscripts(sessionId: Int): Flow<List<Transcript>> = dao.getTranscriptsForSession(sessionId)

    suspend fun insertSession(session: Session): Long = dao.insertSession(session)

    suspend fun updateSession(session: Session) = dao.updateSession(session)

    suspend fun deleteSession(id: Int) = dao.deleteSessionById(id)

    suspend fun insertTranscript(transcript: Transcript) = dao.insertTranscript(transcript)

    fun searchSessions(query: String): Flow<List<Session>> {
        val formattedQuery = "%$query%"
        return dao.searchSessions(formattedQuery)
    }

    fun searchSessionsWithFts(query: String): Flow<List<Session>> {
        // For FTS MATCH, appending '*' allows prefix matching on the words
        val formattedQuery = "$query*"
        return dao.searchSessionsFts(formattedQuery)
    }

    fun searchTranscriptsWithFts(query: String): Flow<List<Transcript>> {
        val formattedQuery = "$query*"
        return dao.searchTranscriptsFts(formattedQuery)
    }
}
