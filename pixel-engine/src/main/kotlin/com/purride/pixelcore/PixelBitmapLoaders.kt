package com.purride.pixelcore

import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.BitmapFactory

public class PixelBitmapLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

public class PixelBitmapAssetLoader(
    private val assets: AssetManager,
) {
    public fun load(path: String): PixelBitmap {
        return try {
            assets.open(path).use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                    ?: throw PixelBitmapLoadException("Failed to decode PNG asset '$path': decoder returned null")
                PixelBitmap.fromAndroidBitmap(bitmap)
            }
        } catch (error: PixelBitmapLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelBitmapLoadException("Failed to decode PNG asset '$path': ${error.message}", error)
        }
    }
}

public class PixelBitmapResourceLoader(
    private val resources: Resources,
) {
    public fun load(resourceId: Int): PixelBitmap {
        return try {
            val bitmap = BitmapFactory.decodeResource(resources, resourceId)
                ?: throw PixelBitmapLoadException("Failed to decode PNG resource '$resourceId': decoder returned null")
            PixelBitmap.fromAndroidBitmap(bitmap)
        } catch (error: PixelBitmapLoadException) {
            throw error
        } catch (error: Throwable) {
            throw PixelBitmapLoadException("Failed to decode PNG resource '$resourceId': ${error.message}", error)
        }
    }
}
