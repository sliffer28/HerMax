package com.hermes.client.ui

import androidx.lifecycle.ViewModel
import com.hermes.client.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {
    val themeMode: StateFlow<String> = secureStorage.themeMode
}
