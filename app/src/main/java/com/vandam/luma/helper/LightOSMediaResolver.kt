package com.vandam.luma.helper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LightOSMediaResolver {
    private const val TAG = "LightOSMediaResolver"
    private const val PODCAST_PLAYLIST_ID = "1"

    private val playlistIdRegex = Regex("""playlistId=(\d+)(?:\.0)?""")

    suspend fun isPodcastPlaying(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val process =
                    ProcessBuilder("logcat", "-d", "-t", "500", "-v", "brief")
                        .redirectErrorStream(true)
                        .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                parseOutput(output)
            } catch (e: Exception) {
                Log.w(TAG, "Logcat unavailable", e)
                false
            }
        }

    private fun parseOutput(output: String): Boolean {
        val lines = output.lines().asReversed()
        for (line in lines) {
            if (!line.contains("LightOSAudioPlayerService:stateDidChange")) continue
            val match = playlistIdRegex.find(line) ?: continue
            return match.groupValues[1] == PODCAST_PLAYLIST_ID
        }
        return false
    }
}
