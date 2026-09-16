package com.pokewidgets.app.widget

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import com.pokewidgets.app.data.SpriteSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * A cry that has been asked for.
 *
 * @param started completes the moment sound begins — or the moment it is clear none will,
 *   so awaiting it can never hang. The widget's receiver holds itself open for this and not
 *   for the whole cry; see `PokemonWidgetProvider.runTap`.
 */
class CryHandle(val job: Job, val started: Deferred<Unit>)

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
 * **There is exactly one cry at a time, process-wide.** [play] cancels whichever cry was
 * still sounding, so tapping a widget three times means one cry — the last one.
 *
 * **Placed widgets' cries sit decoded in a [SoundPool].** `SoundPool.play` on a loaded
 * sample starts in a few milliseconds; a fresh `MediaPlayer` spends a decoder spin-up and a
 * prepare on every single tap, which is the "sometimes it's late" people could hear. The
 * renderer calls [preload] for every widget it draws, so by the time anyone taps, the sample
 * is usually waiting. When it is not — a cold process, a cry downloaded a moment ago — the
 * tap falls back to `MediaPlayer` and loads the sample for next time.
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
     * and the next tap is instant.
     */
    private const val FETCH_WAIT_MS = 3_000L

    /** Home screens rarely hold more; each decoded cry is a few hundred KB of PCM. */
    private const val MAX_SAMPLES = 12

    /** Covers the tail SoundPool reports no end for, when metadata has no duration. */
    private const val FALLBACK_DURATION_MS = 2_000L

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Outlives any one tap on purpose: a broadcast receiver is torn down as soon as it
     * returns, and a download started by one tap has to survive to serve the next.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var playing: Job? = null

    /** Downloads in flight, keyed by Pokémon and flavour, so N taps cause one fetch. */
    private val fetches = HashMap<String, Deferred<File?>>()

    // ---- Sample pool -----------------------------------------------------------

    private class Sample(val soundId: Int, val durationMs: Long) {
        @Volatile var ready = false
    }

    private val pool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()
            .apply {
                setOnLoadCompleteListener { _, soundId, status ->
                    synchronized(this@CryPlayer) {
                        val entry = samples.entries.firstOrNull { it.value.soundId == soundId }
                            ?: return@synchronized
                        if (status == 0) {
                            entry.value.ready = true
                        } else {
                            // Some OEM builds cannot load a given file into SoundPool. That
                            // is permanent for the file, so stop trying and let MediaPlayer
                            // own it.
                            Log.w(TAG, "SoundPool could not load ${entry.key} ($status)")
                            samples.remove(entry.key)
                            unloadable.add(entry.key)
                            unload(soundId)
                        }
                    }
                }
            }
    }

    /** Keyed by absolute path, in access order so the eldest is the one evicted. */
    private val samples = object : LinkedHashMap<String, Sample>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Sample>): Boolean {
            if (size <= MAX_SAMPLES) return false
            pool.unload(eldest.value.soundId)
            return true
        }
    }

    private val unloadable = HashSet<String>()

    @Volatile private var streamId = 0

    // ---- API -------------------------------------------------------------------

    /** Starts a cry, cancelling whichever one was still sounding. */
    @Synchronized
    fun play(context: Context, pokemonId: Int, legacy: Boolean): CryHandle {
        playing?.cancel()
        val appContext = context.applicationContext
        val started = CompletableDeferred<Unit>()
        val askedAt = android.os.SystemClock.elapsedRealtime()
        started.invokeOnCompletion {
            Log.d(TAG, "cry $pokemonId: ${android.os.SystemClock.elapsedRealtime() - askedAt} ms from play() to start")
        }
        val job = scope.launch {
            try {
                sound(appContext, pokemonId, legacy, started)
            } finally {
                // Whatever happened, nobody waiting on the start may hang.
                started.complete(Unit)
            }
        }
        playing = job
        return CryHandle(job, started)
    }

    /**
     * Makes sure this cry is on disk and decoded, so the next tap is instant.
     *
     * Suspends until the download is done, so a caller inside a receiver's window — the
     * renderer — keeps the process alive for it. Loading into the pool finishes on its own.
     */
    suspend fun preload(context: Context, pokemonId: Int, legacy: Boolean) {
        val appContext = context.applicationContext
        val file = SpriteSource(appContext).cachedCry(pokemonId, legacy)
            ?: fetch(appContext, pokemonId, legacy).await()
            ?: return
        load(file)
    }

    // ---- Playback --------------------------------------------------------------

    private suspend fun sound(
        context: Context,
        pokemonId: Int,
        legacy: Boolean,
        started: CompletableDeferred<Unit>,
    ) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        // The only volume that can silence this is the one the cry actually plays on.
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Log.d(TAG, "media volume is zero — skipping cry")
            return
        }

        val cached = SpriteSource(context).cachedCry(pokemonId, legacy)
        val sample = cached?.let { readySample(it) }
        val file = cached ?: cry(context, pokemonId, legacy) ?: return

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()

        // GAIN_TRANSIENT_MAY_DUCK, so a cry ducks whatever is playing instead of stopping it.
        if (audio.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "audio focus denied — skipping cry")
            return
        }

        try {
            if (sample != null) {
                playSample(sample, started)
            } else {
                // Not decoded yet: pay for MediaPlayer this once, and decode for next time.
                scope.launch { load(file) }
                playFile(file, started)
            }
        } finally {
            withContext(NonCancellable) { audio.abandonAudioFocusRequest(focusRequest) }
        }
    }

    private suspend fun playSample(sample: Sample, started: CompletableDeferred<Unit>) {
        // maxStreams = 1 would evict the old stream anyway; stopping it explicitly makes the
        // "one cry at a time" rule independent of that setting.
        pool.stop(streamId)
        val id = pool.play(sample.soundId, 1f, 1f, 1, 0, 1f)
        if (id == 0) {
            Log.w(TAG, "SoundPool refused to play")
            return
        }
        streamId = id
        Log.d(TAG, "playing from the decoded pool")
        started.complete(Unit)
        try {
            delay(sample.durationMs)
        } finally {
            // A cancelled job means a newer cry took over; cut this one off.
            pool.stop(id)
        }
    }

    private suspend fun playFile(file: File, started: CompletableDeferred<Unit>) {
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
                    player.setOnPreparedListener {
                        it.start()
                        Log.d(TAG, "playing through MediaPlayer (not decoded yet)")
                        started.complete(Unit)
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    Log.w(TAG, "could not play cry", e)
                    finish()
                }
                continuation.invokeOnCancellation { finish() }
            }
        }
    }

    // ---- Loading ---------------------------------------------------------------

    @Synchronized
    private fun readySample(file: File): Sample? = samples[file.absolutePath]?.takeIf { it.ready }

    private fun load(file: File) {
        val key = file.absolutePath
        synchronized(this) {
            if (key in samples || key in unloadable) return
        }
        // Read outside the lock: metadata extraction touches the file and can take a moment.
        val duration = durationOf(file)
        synchronized(this) {
            if (key in samples) return
            val soundId = runCatching { pool.load(key, 1) }.getOrDefault(0)
            if (soundId == 0) {
                unloadable.add(key)
                return
            }
            samples[key] = Sample(soundId, duration)
        }
    }

    private fun durationOf(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 } ?: FALLBACK_DURATION_MS
        } catch (e: Exception) {
            FALLBACK_DURATION_MS
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * The cry file: from disk if it is there, otherwise a short wait on a download that
     * carries on without us. Null means "not this time", never "not ever".
     */
    private suspend fun cry(context: Context, pokemonId: Int, legacy: Boolean): File? {
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
                ?.also { load(it) }
        }
        fetches[key] = deferred
        deferred.invokeOnCompletion {
            synchronized(this) { if (fetches[key] === deferred) fetches.remove(key) }
        }
        return deferred
    }
}
