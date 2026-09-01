package com.sspd.servicemgmt.feature.video

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.VideoDTO
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VideoListViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            if (prefs.authToken.isBlank()) {
                _uiState.update { it.copy(loading = false, error = "ပြန်လည် login ဝင်ပါ") }
                return@launch
            }
            try {
                val token = ApiClient.bearer(prefs.authToken)
                val res = ApiClient.service.getVideoCatalog(token)
                if (res.isSuccessful) {
                    _uiState.update {
                        it.copy(videos = sanitize(res.body()?.data), loading = false, error = null)
                    }
                } else {
                    val message = when (res.code()) {
                        401, 403 -> "Video ကြည့်ရန် ခွင့်ပြုချက်မရှိပါ"
                        else -> res.body()?.message?.takeIf { it.isNotBlank() } ?: "Video စာရင်း မရရှိပါ"
                    }
                    _uiState.update { it.copy(loading = false, error = message) }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = "ချိတ်ဆက်မှု မအောင်မြင်ပါ") }
            }
        }
    }

    fun setSearch(query: String) = _uiState.update { it.copy(search = query) }

    data class UiState(
        val videos: List<VideoDTO> = emptyList(),
        val search: String = "",
        val loading: Boolean = false,
        val error: String? = null
    ) {
        val filtered: List<VideoDTO>
            get() {
                val q = search.trim().lowercase()
                if (q.isBlank()) return videos
                return videos.filter { video ->
                    video.title.orEmpty().lowercase().contains(q)
                        || video.category.orEmpty().lowercase().contains(q)
                        || video.description.orEmpty().lowercase().contains(q)
                }
            }
    }

    private fun sanitize(raw: List<VideoDTO>?): List<VideoDTO> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw
            .filter { !it.title.isNullOrBlank() || !it.providerVideoId.isNullOrBlank() }
            .distinctBy { if (it.id > 0) "id-${it.id}" else "url-${it.sourceUrl}-${it.providerVideoId}-${it.title}" }
    }
}
