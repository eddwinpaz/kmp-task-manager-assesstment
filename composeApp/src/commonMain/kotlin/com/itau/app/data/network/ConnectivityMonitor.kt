package com.itau.app.data.network

import kotlinx.coroutines.flow.Flow

interface ConnectivityMonitor {
    fun observeConnectivity(): Flow<Boolean>
    fun isConnected(): Boolean
}
