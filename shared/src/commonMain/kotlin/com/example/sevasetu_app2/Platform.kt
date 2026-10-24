package com.example.sevasetu_app2

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform