package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.User


object ModelTypeHelper {
    private const val CONVERTED_FROM_USER_KEY = "convertedFromUser"
    private const val CONVERTED_FROM_USER_VALUE = "true"
    
    fun userToArtist(user: User): Artist {
        return Artist(
            id = user.id,
            name = user.name,
            cover = user.cover,
            subtitle = user.subtitle,
            extras = user.extras + (CONVERTED_FROM_USER_KEY to CONVERTED_FROM_USER_VALUE)
        )
    }
    
    fun safeArtistListConversion(list: List<Any>): List<Artist> {
        return list.mapNotNull { item -> 
            when (item) {
                is Artist -> item
                is User -> userToArtist(item)
                else -> {
                    println("Unexpected type in artist list: ${item::class.simpleName}")
                    null
                }
            }
        }
    }

    /**
     * Task 3: Modificar las validaciones para permitir que objetos de tipo "Video" sean aceptados,
     * no solo aquellos marcados específicamente como "MusicVideo".
     */
    fun isAcceptableVideoType(musicVideoType: String?): Boolean {
        // En YouTube Universal aceptamos cualquier tipo de video (null o cualquier string),
        // permitiendo que el catálogo global de YouTube sea accesible.
        return true
    }
}