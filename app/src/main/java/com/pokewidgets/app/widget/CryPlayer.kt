package com.pokewidgets.app.widget

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.pokewidgets.app.data.SpriteSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Plays a Pokémon's cry, from the widget or from inside the app.
 *
 * Both callers sound identical, and both go out over the **media** stream. That is a
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
 * **There is exactly one cry at a time, process-wide.** [play] cancels whichever cry was
 * still sounding, so tapping a widget three times means one cry — the last one — rather
 * than three overlapping players, three audio focus requests and three `MediaPlayer`s
 * finishing together a few seconds later. The widget tap handler had no such rule and the
 * app's had its own private one; keeping it here means neither can drift from the other.
 */
object CryPlayer {

    private const val TAG = "CryPlayer"

    /** A cry is about a second. Anything still preparing after this is not going to play. */
    private const val MAX_WAIT_MS = 5_000L

    /**
     * How long a tap waits for a cry that is not cached yet.
     *
     * Deliberately short. The fetch runs on [scope] rather than as a child of the playback
     * job, so exceeding this abandons the *wait*, not the *download* — the file still lands
     * and the next tap is instant. Waiting for the whole fetch instead is what produced the
     * ANR: [SpriteSource.cryFile] tries two flavours across two hosts, four calls bounded at
     * 25 s each, inside a broadcast receiver window Android grants roughly ten.
     */
    private const val FETCH_WAIT_MS = 3_000L

    /**
     * Outlives any one tap on purpose: a broadcast receiver is torn down as soon as it
     * returns, and a download started by one tap has to survive to serve the next.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var playing: Job? = null

    /** Downloads in flight, keyed by Pokémon and flavour, so N taps cause one fetch. */
    private val fetches = HashMap<String, Deferred<File?>>()

    /**
     * Starts a cry, cancelling whichever one was still sounding.
     *
     * Returns the job so a caller that must stay alive for the sound — the widget's
     * broadcast receiver — can join it. Joining is optional and bounded by the caller; the
     * job runs on [scope] either way, so abandoning the join does not stop the cry.
     */
    @Synchronized
    fun play(context: Context, pokemonId: Int, legacy: Boolean): Job {
        playing?.cancel()
        val appContext = context.applicationContext
        return scope.launch { sound(appContext, pokemonId, legacy) }.also { playing = it }
    }

    private suspend fun sound(context: Context, pokemonId: Int, legacy: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        // The only volume that can silence this now is the one the cry actually plays on.
        // Checked rather than assumed, so a muted phone still costs nothing to tap.
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Log.d(TAG, "media volume is zero — skipping cry")
            return
        }

        val file = cry(context, pokemonId, legacy) ?: return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()

        // GAIN_TRANSIENT_MAY_DUCK, so a cry ducks whatever is playing instead of stopping it.
        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "audio focus denied — skipping cry")
            return
        }

        try {
            withTimeoutOrNull(MAX_WAIT_MS) {
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

    /**
     * The cry file: from disk if it is there, otherwise a short wait on a download that
     * carries on without us. Null means "not this time", never "not ever".
     */
    private suspend fun cry(context: Context, pokemonId: Int, legacy: Boolean): File? {
        SpriteSource(context).cachedCry(pokemonId, legacy)?.let { return it }
        val fetched = withTimeoutOrNull(FETCH_WAIT_MS) { fetch(context, pokemonId, legacy).await() }
        if (fetched == null) {
            Log.d(TAG, "cry for $pokemonId not cached yet — downloading for the next tap")
        }
        return fetched
    }

    @Synchronized
    private fun fetch(context: Context, pokemonId: Int, legacy: Boolean): Deferred<File?> {
        val key = "$pokemonId/$legacy"
        fetches[key]?.takeIf { it.isActive }?.let { return it }
        // runCatching inside, not around await(): a failure here is an ordinary miss, and an
        // exception parked in the Deferred would surface at whichever tap happened to wait.
        val deferred = scope.async {
            runCatching { SpriteSource(context).cryFile(pokemonId, legacy) }
                .onFailure { Log.d(TAG, "could not fetch a cry for $pokemonId", it) }
                .getOrNull()
        }
        fetches[key] = deferred
        deferred.invokeOnCompletion {
            synchronized(this) { if (fetches[key] === deferred) fetches.remove(key) }
        }
        return deferred
    }
}
