package com.pokewidgets.app.widget

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.pokewidgets.app.data.SpriteSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Plays a Pokémon's cry when the widget is tapped.
 *
 * Deliberately well-behaved: it stays quiet when the phone is silenced, takes only
 * transient audio focus so it ducks whatever is playing rather than stopping it, and
 * releases everything before returning. A home-screen widget that talks over music or
 * blares in a meeting is a widget that gets uninstalled.
 */
object CryPlayer {

    private const val TAG = "CryPlayer"
    private const val MAX_WAIT_MS = 6_000L

    /**
     * @param respectSilentMode skip playback entirely when the phone is on silent or
     *   vibrate. True for widget taps: a home-screen widget that blares in a meeting is a
     *   widget that gets uninstalled. False for taps inside the app, where the sound is
     *   the point of the gesture — the ringer setting governs ringtones and
     *   notifications, not media an app plays because the user asked it to, which is why
     *   a video still has audio on a silenced phone.
     */
    suspend fun play(
        context: Context,
        pokemonId: Int,
        legacy: Boolean,
        respectSilentMode: Boolean = true,
    ) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        if (respectSilentMode && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            Log.d(TAG, "ringer is silent/vibrate — skipping cry")
            return
        }

        val file = SpriteSource(context).cryFile(pokemonId, legacy)
            ?: run {
                Log.d(TAG, "no cry available for $pokemonId (legacy=$legacy)")
                return
            }

        val attributes = AudioAttributes.Builder()
            .setUsage(
                // Sonification is right for a widget: it is a system-surface blip. In the
                // app the cry is content the user asked for, so it belongs on the media
                // stream, which is also the one their volume keys are already adjusting.
                if (respectSilentMode) {
                    AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                } else {
                    AudioAttributes.USAGE_MEDIA
                },
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()

        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "audio focus denied — skipping cry")
            return
        }

        try {
            // A stuck MediaPlayer must not hold the broadcast receiver open until the
            // system kills it, so cap the wait well inside the ~10 s a receiver gets.
            kotlinx.coroutines.withTimeoutOrNull(MAX_WAIT_MS) {
            suspendCancellableCoroutine { continuation ->
                val player = MediaPlayer()
                var finished = false
                val finish = {
                    if (!finished) {
                        finished = true
                        runCatching { player.release() }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                try {
                    player.setAudioAttributes(attributes)
                    player.setDataSource(file.absolutePath)
                    player.setOnCompletionListener { finish() }
                    player.setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "MediaPlayer error $what/$extra")
                        finish()
                        true
                    }
                    player.setOnPreparedListener { it.start() }
                    player.prepareAsync()
                } catch (e: Exception) {
                    Log.w(TAG, "could not play cry", e)
                    finish()
                }
                continuation.invokeOnCancellation { finish() }
            }
            }
        } finally {
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }
}
