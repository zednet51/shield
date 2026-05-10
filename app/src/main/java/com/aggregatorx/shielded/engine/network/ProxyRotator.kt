package com.aggregatorx.shielded.engine.network

import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class ProxyConfig(val host: String, val port: Int, val type: Proxy.Type = Proxy.Type.HTTP)

/**
 * Rotates through a configurable proxy pool.
 * Defaults to direct connection when pool is empty.
 * Supports HTTP and SOCKS5 proxies.
 */
@Singleton
class ProxyRotator @Inject constructor() {

    private val pool = mutableListOf<ProxyConfig>()
    private var currentIndex = 0

    fun addProxy(host: String, port: Int, type: Proxy.Type = Proxy.Type.HTTP) {
        pool.add(ProxyConfig(host, port, type))
    }

    fun clearPool() { pool.clear() }

    fun current(): Proxy {
        if (pool.isEmpty()) return Proxy.NO_PROXY
        val cfg = pool[currentIndex % pool.size]
        return Proxy(cfg.type, InetSocketAddress(cfg.host, cfg.port))
    }

    fun rotate(): Proxy {
        if (pool.isEmpty()) return Proxy.NO_PROXY
        currentIndex = (currentIndex + 1) % pool.size
        return current()
    }

    fun random(): Proxy {
        if (pool.isEmpty()) return Proxy.NO_PROXY
        val cfg = pool[Random.nextInt(pool.size)]
        return Proxy(cfg.type, InetSocketAddress(cfg.host, cfg.port))
    }

    fun isActive(): Boolean = pool.isNotEmpty()
    fun poolSize(): Int = pool.size
}
