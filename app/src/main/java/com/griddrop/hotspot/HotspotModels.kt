package com.griddrop.hotspot

data class HotspotInfo(
    val ssid: String,
    val passphrase: String?,
    
    val gatewayIp: String,
)

sealed interface HotspotStatus {
    data object Idle : HotspotStatus
    data object Starting : HotspotStatus
    data class Running(val info: HotspotInfo) : HotspotStatus

    
    data class Failed(val reason: Int) : HotspotStatus

    
    data object Unsupported : HotspotStatus
}
