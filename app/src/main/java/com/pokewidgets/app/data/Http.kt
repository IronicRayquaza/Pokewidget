package com.pokewidgets.app.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app's one HTTP stack.
 *
 * Every caller here used to build its own client. That is easy to write and wrong in a way that
 * only shows up under load: an `OkHttpClient` owns a [okhttp3.ConnectionPool] and a
 * [okhttp3.Dispatcher], and both [SpriteSource] and [WeatherSource] are constructed *fresh on
 * every widget tap and every render*. Each one opened its own pool, kept its own idle connections
 * alive for five minutes, and reused nothing — so a widget tapped repeatedly accumulated pools
 * and sockets for no benefit, and every fetch paid for a new TLS handshake to a host we had just
 * finished talking to.
 *
 * The clients below are derived with `newBuilder()`, which is the point: a derived client
 * **shares the parent's pool and dispatcher** and differs only in the settings it overrides. So
 * there is one pool in the process, and the two profiles below are just timeout policies on top
 * of it.
 */
object Http {

    /** Holds the single connection pool and dispatcher everything else borrows. */
    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Sprites and cries: whole files, so the read window is generous, but the call as a whole is
     * bounded because renders and taps run inside a broadcast receiver's `goAsync()` window.
     */
    val files: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Weather: two small JSON documents, and a nicety rather than a requirement. A reading that
     * has not arrived in ten seconds is worth less than the render waiting on it.
     */
    val json: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
