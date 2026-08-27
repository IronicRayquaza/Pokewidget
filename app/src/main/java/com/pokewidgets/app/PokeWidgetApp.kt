package com.pokewidgets.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PokeWidgetApp : Application(), SingletonImageLoader.Factory {

    /**
     * The picker previews sprites as live GIFs, so the shared loader needs an animated
     * decoder. Crossfade is off: these are 40x40 pixel-art thumbnails, and fading them
     * in just makes the grid feel mushy.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .connectTimeout(15, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .build()
                        },
                    ),
                )
            }
            .crossfade(false)
            .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
            .build()
}
