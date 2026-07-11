package com.griddrop

import android.app.Application
import com.griddrop.hotspot.HotspotController
import com.griddrop.net.TransferRepository


class GridDropApplication : Application() {
    val repository: TransferRepository by lazy { TransferRepository(this) }
    val hotspot: HotspotController by lazy { HotspotController(this) }
}
