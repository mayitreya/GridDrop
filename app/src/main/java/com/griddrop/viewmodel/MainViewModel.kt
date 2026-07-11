package com.griddrop.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.griddrop.GridDropApplication
import com.griddrop.Role
import com.griddrop.SERVER_PORT
import com.griddrop.hotspot.HotspotStatus
import com.griddrop.net.GridDropService
import com.griddrop.net.ReceivedFile
import com.griddrop.net.SharedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class Step { CHOOSE_ROLE, CONNECTING, JOIN_WIFI, OPEN_PAGE, TRANSFER, TROUBLESHOOT, ERROR }

data class UiState(
    val role: Role? = null,
    val step: Step = Step.CHOOSE_ROLE,
    val hotspot: HotspotStatus = HotspotStatus.Idle,
    val sharedFiles: List<SharedFile> = emptyList(),
    val received: List<ReceivedFile> = emptyList(),
    val busy: Boolean = false,
    val confirmEnd: Boolean = false,
    val showStats: Boolean = false,
    val message: String? = null,
) {
    val ssid: String? get() = (hotspot as? HotspotStatus.Running)?.info?.ssid
    val passphrase: String? get() = (hotspot as? HotspotStatus.Running)?.info?.passphrase

    
    val ipUrl: String?
        get() = (hotspot as? HotspotStatus.Running)?.info?.gatewayIp?.let { "http://$it:$SERVER_PORT" }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app as GridDropApplication
    private val hotspot = graph.hotspot
    private val repo = graph.repository

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    
    val canForce5GHz: Boolean get() = hotspot.canForceBand5

    private var stepBeforeTroubleshoot = Step.JOIN_WIFI

    init {
        viewModelScope.launch {
            hotspot.status.collect { status ->
                _state.update { it.copy(hotspot = status) }
                when (status) {
                    is HotspotStatus.Running -> onHotspotUp()
                    is HotspotStatus.Failed, HotspotStatus.Unsupported ->
                        _state.update { if (it.step == Step.CONNECTING) it.copy(step = Step.ERROR) else it }
                    else -> {}
                }
            }
        }
        
        
        viewModelScope.launch {
            repo.sharedFlow.collect { files -> _state.update { it.copy(sharedFiles = files) } }
        }
        viewModelScope.launch {
            repo.receivedFlow.collect { files -> _state.update { it.copy(received = files) } }
        }
    }

    

    fun chooseRole(role: Role) {
        _state.update { it.copy(role = role, step = Step.CONNECTING) }
        hotspot.start()
    }

    private fun onHotspotUp() {
        val role = _state.value.role ?: return
        val info = (_state.value.hotspot as? HotspotStatus.Running)?.info
        GridDropService.start(getApplication(), role, info?.gatewayIp)
        _state.update { if (it.step == Step.CONNECTING) it.copy(step = Step.JOIN_WIFI) else it }
    }

    fun next() = _state.update {
        it.copy(
            step = when (it.step) {
                Step.JOIN_WIFI -> Step.OPEN_PAGE
                Step.OPEN_PAGE -> Step.TRANSFER
                else -> it.step
            },
        )
    }

    fun back() = _state.update {
        it.copy(
            step = when (it.step) {
                Step.OPEN_PAGE -> Step.JOIN_WIFI
                Step.TRANSFER -> Step.OPEN_PAGE
                else -> it.step
            },
        )
    }

    fun showTroubleshoot() {
        stepBeforeTroubleshoot = _state.value.step
        _state.update { it.copy(step = Step.TROUBLESHOOT) }
    }

    fun closeTroubleshoot() = _state.update { it.copy(step = stepBeforeTroubleshoot) }

    
    fun tryCompatibleNetwork() {
        if (!hotspot.canForceBand5) return
        _state.update { it.copy(step = Step.CONNECTING) }
        hotspot.startForced5GHz()
    }

    fun retry() {
        _state.update { it.copy(step = Step.CONNECTING) }
        hotspot.start()
    }

    fun goHome() {
        teardown()
        _state.update { UiState() }
    }

    
    fun requestEndSession() = _state.update { it.copy(confirmEnd = true) }

    fun dismissEndSession() = _state.update { it.copy(confirmEnd = false) }

    
    fun endSession() {
        teardown()
        _state.update {
            UiState(message = "Disconnected. The iPhone will rejoin its usual Wi-Fi.")
        }
    }

    
    private fun teardown() {
        hotspot.stop()
        GridDropService.stop(getApplication())
    }

    

    fun addFilesToSend(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            withContext(Dispatchers.IO) {
                uris.forEach { uri -> runCatching { repo.importForSending(uri) } }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    fun removeShared(id: String) = repo.removeShared(id)

    fun dismissMessage() = _state.update { it.copy(message = null) }

    

    fun openStats() = _state.update { it.copy(showStats = true) }

    fun closeStats() = _state.update { it.copy(showStats = false) }
}
