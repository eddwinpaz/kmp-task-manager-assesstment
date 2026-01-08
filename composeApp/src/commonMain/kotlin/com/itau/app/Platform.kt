package com.itau.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform