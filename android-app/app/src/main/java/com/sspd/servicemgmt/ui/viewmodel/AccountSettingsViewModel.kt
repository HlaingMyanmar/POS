package com.sspd.servicemgmt.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sspd.servicemgmt.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager(application)

    private val _uiState = MutableStateFlow(AccountSettingsUiState(
        username    = prefs.username,
        displayName = prefs.displayName.ifEmpty { prefs.username }
    ))
    val uiState: StateFlow<AccountSettingsUiState> = _uiState.asStateFlow()

    data class AccountSettingsUiState(
        val username:    String = "",
        val displayName: String = ""
    )
}
