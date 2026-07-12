package dev.twosec.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.twosec.app.data.BlocklistStore
import dev.twosec.app.platform.InstalledApp
import dev.twosec.app.platform.InstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConfigUiState(
    val masterOn: Boolean = false,
    val installedApps: List<InstalledApp> = emptyList(),
    val blocklist: Set<String> = emptySet(),
    val isLoading: Boolean = true,
)

class ConfigViewModel(
    private val store: BlocklistStore,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private val installedAppsState = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val isLoadingState = MutableStateFlow(true)

    val state: StateFlow<ConfigUiState> = combine(
        store.masterEnabled(),
        store.blocklist(),
        installedAppsState,
        isLoadingState,
    ) { masterOn, blocklist, apps, isLoading ->
        ConfigUiState(
            masterOn = masterOn,
            installedApps = apps,
            blocklist = blocklist,
            isLoading = isLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ConfigUiState(),
    )

    init {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                installedAppsProvider.listUserLaunchableApps()
            }
            installedAppsState.value = apps
            isLoadingState.value = false
        }
    }

    fun onMasterToggle(enabled: Boolean) {
        viewModelScope.launch { store.setMasterEnabled(enabled) }
    }

    fun onAppChecked(packageName: String, checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                store.addToBlocklist(packageName)
            } else {
                store.removeFromBlocklist(packageName)
            }
        }
    }

    companion object {
        fun factory(
            store: BlocklistStore,
            installedAppsProvider: InstalledAppsProvider,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ConfigViewModel(store, installedAppsProvider) as T
            }
        }
    }
}
