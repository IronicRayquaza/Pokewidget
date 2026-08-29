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
 * Plays a Pokémon's cry, from the widget or from inside the app.
 *
 * Both callers now sound identical, and both go out over the **media** stream. That is a
 * deliberate reversal. The widget used to ask for USAGE_ASSISTANCE_SONIFICATION and to
 * bail out whenever the ringer was not RINGER_MODE_NORMAL, which made it inaudible in two
 * separate ways at once: sonification is routed to STREAM_SYSTEM, a stream most phones
 * keep near zero and mute outright on vibrate, and the ringer check silenced the cry even
 * when that stream was turned up. The app, which asked for USAGE_MEDIA and ignored the
 * ringer, was audible the whole time — which is exactly the difference that got reported.
 *
 * A cry is not a notification. It only ever plays because someone deliberately tapped the
 * Pokémon, so it belongs on the stream the volume keys are already adjusting, under the
 * same rule a video follows: a silenced ringer does not mute media the user asked for.
 *
 * What politeness remains is the part that actually matters. Focus is taken as
 * GAIN_TRANSIENT_MAY_DUCK, so a cry ducks whatever is playing instead of stopping it;
 * playback is skipped when media volume is genuinely at zero; and everything is released
 * before returning.
 */
object CryPlayer {

    private const val TAG = "CryPlayer"
    private const val MAX_WAIT_MS = 6_000L

    suspend fun play(context: Context, pokemonId: Int, legacy: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        // The only volume that can silence this now is the one the cry actually plays on.
        // Checked rather than assumed, so a muted phone still costs nothing to tap.
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Log.d(TAG, "media volume is zero — skipping cry")
            return
        }

        val file = SpriteSource(context).cryFile(pokemonId, legacy)
            ?: run {
                Log.d(TAG, "no cry available for $pokemonId (legacy=$legacy)")
                return
            }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
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
