package com.griddrop.util

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

object NetUtils {

    
    fun findHotspotAddress(): InetAddress? {
        val candidates = mutableListOf<InetAddress>()
        for (nif in NetworkInterface.getNetworkInterfaces()) {
            if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                    
                    val host = addr.hostAddress ?: continue
                    if (host.startsWith("192.168.") || host.startsWith("172.")) {
                        candidates.add(0, addr)
                    } else {
                        candidates.add(addr)
                    }
                }
            }
        }
        return candidates.firstOrNull()
    }

    fun findHotspotAddressString(): String? = findHotspotAddress()?.hostAddress
}
