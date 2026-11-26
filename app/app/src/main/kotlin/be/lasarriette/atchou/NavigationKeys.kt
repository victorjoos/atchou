package be.lasarriette.atchou

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Camera : NavKey

@Serializable data object History : NavKey

@Serializable data class Crop(val uri: String) : NavKey

/** [crop] is a normalized "x,y,w,h" box in the image once rotated by [rotate] degrees. */
@Serializable data class Send(val uri: String, val drawing: Boolean = false, val rotate: Int = 0, val crop: String? = null) : NavKey
