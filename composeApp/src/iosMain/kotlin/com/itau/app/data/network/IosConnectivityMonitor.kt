package com.itau.app.data.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

class IosConnectivityMonitor : ConnectivityMonitor {
    private val monitor = nw_path_monitor_create()
    private val _connectivity = MutableStateFlow(true)

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            _connectivity.value = status == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    override fun observeConnectivity(): Flow<Boolean> = _connectivity

    override fun isConnected(): Boolean = _connectivity.value
}
