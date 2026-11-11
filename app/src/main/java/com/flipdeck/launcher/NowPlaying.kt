package com.flipdeck.launcher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/**
 * Whatever is playing in the background (music, podcasts, video audio), read through the phone's
 * media sessions. This needs notice access, which the Notices screen already asks for.
 */
object NowPlaying {

    class Info(
        val controller: MediaController,
        val title: String,
        val artist: String,
        val app: String,
        val playing: Boolean
    ) {
        /** Changes whenever something visible on the widget changes. */
        val signature: String get() = "$app|$title|$artist|$playing"

        fun playPause() {
            if (playing) controller.transportControls.pause() else controller.transportControls.play()
        }

        fun next() = controller.transportControls.skipToNext()

        fun previous() = controller.transportControls.skipToPrevious()
    }

    /** The session that is playing now, or the most recent paused one. Null if there is none. */
    fun current(context: Context): Info? {
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(context, NoticeService::class.java))
                .filter { it.packageName != Ui.BLOCKED_PACKAGE }

            val playing = sessions.firstOrNull {
                val s = it.playbackState?.state
                s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
            }
            val chosen = playing ?: sessions.firstOrNull { it.metadata != null } ?: return null

            val meta = chosen.metadata
            val app = appLabel(context, chosen.packageName)
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = (meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()

            Info(chosen, title.ifEmpty { app }, artist, app, playing != null)
        } catch (e: Exception) {
            // No notice access yet, or the phone refused: just don't show the widget.
            null
        }
    }

    private fun appLabel(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }
}
