package com.example.service

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

object WavWriter {
    @Throws(IOException::class)
    fun writeWavHeader(file: File, sampleRate: Int, channelCount: Int, bitsPerSample: Int) {
        val randomAccessFile = RandomAccessFile(file, "rw")
        randomAccessFile.seek(0)

        val totalDataLen = file.length() - 44
        val byteRate = sampleRate * channelCount * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        val totalFileSize = totalDataLen + 36
        header[4] = (totalFileSize and 0xff).toByte()
        header[5] = ((totalFileSize shr 8) and 0xff).toByte()
        header[6] = ((totalFileSize shr 16) and 0xff).toByte()
        header[7] = ((totalFileSize shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16 // Subchunk1Size
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // AudioFormat (PCM)
        header[21] = 0
        
        header[22] = channelCount.toByte()
        header[23] = 0
        
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = (channelCount * bitsPerSample / 8).toByte() // BlockAlign
        header[33] = 0
        
        header[34] = bitsPerSample.toByte() // BitsPerSample
        header[35] = 0

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        header[40] = (totalDataLen and 0xff).toByte()
        header[41] = ((totalDataLen shr 8) and 0xff).toByte()
        header[42] = ((totalDataLen shr 16) and 0xff).toByte()
        header[43] = ((totalDataLen shr 24) and 0xff).toByte()

        randomAccessFile.write(header, 0, 44)
        randomAccessFile.close()
    }
}
