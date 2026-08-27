package com.sspd.servicemgmt.core.realtime

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Subscribes to DataEventBus and calls [action] whenever an event whose
 * entity name contains any of the given [keywords] (case-insensitive) arrives.
 *
 * Example:
 *   init {
 *       load()
 *       onDataEvent("Sale") { load() }
 *   }
 */
fun AndroidViewModel.onDataEvent(
    vararg keywords: String,
    debounceMs: Long = 600L,
    action: () -> Unit,
) {
    viewModelScope.launch {
        DataEventBus.events
            .filter { event ->
                keywords.any { event.entity.contains(it, ignoreCase = true) }
            }
            .debounce(debounceMs)
            .collect { action() }
    }
}
