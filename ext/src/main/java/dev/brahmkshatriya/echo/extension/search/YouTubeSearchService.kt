package dev.brahmkshatriya.echo.extension.search

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.endpoints.EchoSearchEndpoint
import dev.brahmkshatriya.echo.extension.toAlbum
import dev.brahmkshatriya.echo.extension.toArtist
import dev.brahmkshatriya.echo.extension.toPlaylist
import dev.brahmkshatriya.echo.extension.toTrack
import dev.toastbits.ytmkt.endpoint.SearchResults
import dev.toastbits.ytmkt.endpoint.SearchType
import dev.toastbits.ytmkt.impl.youtubei.YoutubeiApi
import dev.toastbits.ytmkt.model.external.ThumbnailProvider
import dev.toastbits.ytmkt.model.external.mediaitem.YtmArtist
import dev.toastbits.ytmkt.model.external.mediaitem.YtmPlaylist
import dev.toastbits.ytmkt.model.external.mediaitem.YtmSong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope


class YouTubeSearchService(
    private val api: YoutubeiApi,
    private val searchEndpoint: EchoSearchEndpoint
) {
    data class CategorySearchResult(
        val type: SearchType,
        val shelves: List<Shelf>,
        val error: Exception? = null
    )

    // Perform a single broad search for "All" category to avoid duplicates
    suspend fun searchAllCategories(
        query: String,
        thumbnailQuality: ThumbnailProvider.Quality
    ): List<CategorySearchResult> {
        return listOf(searchCategory(query, SearchType.VIDEO, thumbnailQuality, isBroad = true))
    }

    private fun getYoutubeSearchParams(searchType: SearchType): String? {
        return when (searchType) {
            SearchType.VIDEO -> "EgIQAQ%3D%3D"
            SearchType.ARTIST -> "EgIQAg%3D%3D" // Channel filter
            SearchType.PLAYLIST -> "EgIQAw%3D%3D"
            SearchType.ALBUM -> "EgIQAw%3D%3D"
            SearchType.SONG -> "EgIQAQ%3D%3D"
        }
    }

    suspend fun searchCategory(
        query: String,
        searchType: SearchType,
        thumbnailQuality: ThumbnailProvider.Quality,
        isBroad: Boolean = false
    ): CategorySearchResult {
        return try {
            val params = if (isBroad) null else getYoutubeSearchParams(searchType)
            val searchResult = searchEndpoint.search(
                query,
                params = params,
                nonMusic = true
            ).getOrThrow()
            
            val shelves = convertSearchResultsToShelves(searchResult, thumbnailQuality, isBroad)
            CategorySearchResult(searchType, shelves)
        } catch (e: Exception) {
            println("${searchType.name} search failed: ${e.message}")
            CategorySearchResult(searchType, emptyList(), e)
        }
    }
    
    private suspend fun convertSearchResultsToShelves(
        searchResults: SearchResults,
        thumbnailQuality: ThumbnailProvider.Quality,
        isBroad: Boolean
    ): List<Shelf> {
        val shelves = mutableListOf<Shelf>()
        
        for ((layout, _) in searchResults.categories) {
            val title = layout.title?.getString("en") ?: "Results"
            val items = layout.items
            
            if (items.isEmpty()) continue
            
            val echoItems = items.mapNotNull { item ->
                convertMediaItem(item, thumbnailQuality)
            }
            
            if (echoItems.isEmpty()) continue
            
            // If it's a broad search and the title is generic, split items into categories
            if (isBroad && (title == "Results" || title == "Resultados")) {
                val channels = echoItems.filterIsInstance<Artist>()
                val videos = echoItems.filterIsInstance<Track>()
                val albums = echoItems.filterIsInstance<Album>()
                val playlists = echoItems.filterIsInstance<Playlist>()

                if (channels.isNotEmpty()) {
                    shelves.add(Shelf.Lists.Items("search_channels_${System.currentTimeMillis()}", "Channels", channels))
                }
                if (videos.isNotEmpty()) {
                    shelves.add(Shelf.Lists.Tracks("search_videos_${System.currentTimeMillis()}", "Videos", videos))
                }
                if (albums.isNotEmpty()) {
                    shelves.add(Shelf.Lists.Items("search_albums_${System.currentTimeMillis()}", "Albums", albums))
                }
                if (playlists.isNotEmpty()) {
                    shelves.add(Shelf.Lists.Items("search_playlists_${System.currentTimeMillis()}", "Playlists", playlists))
                }
            } else {
                val shelf = createShelfFromItems(title, echoItems)
                shelves.add(shelf)
            }
        }
        
        return shelves
    }

    private fun convertMediaItem(
        item: Any,
        thumbnailQuality: ThumbnailProvider.Quality
    ): EchoMediaItem? {
        return when (item) {
            is YtmSong -> item.toTrack(thumbnailQuality)
            is YtmArtist -> item.toArtist(thumbnailQuality)
            is YtmPlaylist -> {
                if (item.type == YtmPlaylist.Type.ALBUM) {
                    item.toAlbum(false, thumbnailQuality)
                } else {
                    item.toPlaylist(thumbnailQuality)
                }
            }
            else -> null
        }
    }
    
    private fun createShelfFromItems(title: String, items: List<EchoMediaItem>): Shelf {
        val shelfId = "search_${title.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}"
        
        return if (items.all { it is Track }) {
            Shelf.Lists.Tracks(
                id = shelfId,
                title = title,
                list = items.filterIsInstance<Track>()
            )
        } else {
            Shelf.Lists.Items(
                id = shelfId,
                title = title,
                list = items
            )
        }
    }

    fun mergeCategoryResults(results: List<CategorySearchResult>): List<Shelf> {
        return results
            .filter { it.error == null && it.shelves.isNotEmpty() }
            .flatMap { it.shelves }
    }
}
