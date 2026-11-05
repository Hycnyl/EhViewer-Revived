package com.hippo.ehviewer.benchmark

import android.content.Context
import androidx.startup.Initializer
import coil3.SingletonImageLoader
import com.hippo.ehviewer.EhApplication

/** Keep the real Coil pipeline and local fixture mapping across benchmark process restarts. */
class BenchmarkInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        SingletonImageLoader.setSafe { imageContext ->
            (imageContext.applicationContext as EhApplication).newImageLoader(imageContext).newBuilder()
                .components { add(OfflineThumbs(imageContext.cacheDir)) }
                .build()
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
