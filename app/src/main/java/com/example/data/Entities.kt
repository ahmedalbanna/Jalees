package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Fts4

@Entity(tableName = "sessions_table")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "start_timestamp") val startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp") val endTimestamp: Long
)

@Entity(
    tableName = "transcripts_table",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Transcript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "session_id", index = true) val sessionId: Int,
    @ColumnInfo(name = "speaker_tag") val speakerTag: String,
    @ColumnInfo(name = "text_content") val textContent: String,
    @ColumnInfo(name = "relative_time") val relativeTime: Long // in milliseconds from session beginning
)

@Entity(tableName = "transcripts_fts_table")
@Fts4(contentEntity = Transcript::class)
data class TranscriptFts(
    @ColumnInfo(name = "text_content") val textContent: String
)
