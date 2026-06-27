package com.purride.pixelcore

import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.BitmapFactory

/** Android bitmap 资源解码失败。 */
public class PixelBitmapLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** 从 Android assets 加载 PNG/JPEG 等 bitmap 并转换成 [PixelBitmap]。 */
public class PixelBitmapAssetLoader(
    private val assets: AssetManager,
) {
    /** 读取指定 asset 路径并返回可被 pixel-engine 绘制的 bitmap。 */
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

/** 从 Android resource id 加载 bitmap 并转换成 [PixelBitmap]。 */
public class PixelBitmapResourceLoader(
    private val resources: Resources,
) {
    /** 读取指定 drawable/mipmap resource id。 */
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
