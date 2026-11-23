package be.lasarriette.atchou

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Camera : NavKey

@Serializable data object History : NavKey

@Serializable data class Send(val uri: String, val drawing: Boolean = false) : NavKey
