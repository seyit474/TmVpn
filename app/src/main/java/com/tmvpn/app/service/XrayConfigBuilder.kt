package com.tmvpn.app.service

import com.tmvpn.app.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    const val SOCKS_PORT = 10808
    const val DNS_PORT   = 10853

    fun build(
        cfg: ServerConfig,
        enableFragment:   Boolean = false,
        hwidUuid:         String? = null,
        fragmentPackets:  String  = "tlshello",
        fragmentLength:   String  = "1-3",
        fragmentInterval: String  = "1-1",
        fragmentMaxSplit: String? = null,
        noisesEnabled:    Boolean = false,
        noiseType:        String  = "rand",
        noisePacket:      String  = "10-50",
        noiseDelay:       String  = "5-20",
        preferIpType:     String  = "auto",
        muxEnabled:       Boolean = true,
        muxConcurrency:   Int     = 8,
        muxXudpQuic:      String  = "reject",
        blockUdp443:      Boolean = true,
        proxyGoogle:      Boolean = true,
        bypassLan:        Boolean = true,
        sniffingEnabled:  Boolean = true,
        logLevel:         String  = "warning",
        remoteDns:        String  = "1.1.1.1",
    ): String = JSONObject().apply {
        put("log",       JSONObject().put("loglevel", logLevel))
        put("inbounds",  inbounds(sniffingEnabled))
        put("outbounds", outbounds(cfg, hwidUuid, enableFragment, fragmentPackets, fragmentLength,
            fragmentInterval, fragmentMaxSplit, noisesEnabled, noiseType, noisePacket, noiseDelay,
            muxEnabled, muxConcurrency, muxXudpQuic))
        put("routing",   routing(blockUdp443, proxyGoogle, bypassLan, preferIpType))
        put("dns",       dns(remoteDns))
    }.toString(2)

    private fun inbounds(sniffingEnabled: Boolean) = JSONArray().apply {
        put(JSONObject().apply {
            put("tag", "socks-in"); put("port", SOCKS_PORT)
            put("listen", "127.0.0.1"); put("protocol", "socks")
            put("settings", JSONObject().apply { put("auth", "noauth"); put("udp", true) })
            if (sniffingEnabled) put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply { put("http"); put("tls"); put("quic") })
            })
        })
        put(JSONObject().apply {
            put("tag", "dns-in"); put("port", DNS_PORT)
            put("listen", "127.0.0.1"); put("protocol", "dokodemo-door")
            put("settings", JSONObject().apply {
                put("address", "1.1.1.1"); put("port", 53); put("network", "tcp,udp")
            })
        })
    }

    private fun outbounds(
        cfg: ServerConfig, hwidUuid: String?,
        enableFragment: Boolean, fragmentPackets: String, fragmentLength: String,
        fragmentInterval: String, fragmentMaxSplit: String?,
        noisesEnabled: Boolean, noiseType: String, noisePacket: String, noiseDelay: String,
        muxEnabled: Boolean, muxConcurrency: Int, muxXudpQuic: String,
    ) = JSONArray().apply {
        put(proxyOut(cfg, hwidUuid, enableFragment, fragmentPackets, fragmentLength,
            fragmentInterval, fragmentMaxSplit, noisesEnabled, noiseType, noisePacket, noiseDelay,
            muxEnabled, muxConcurrency, muxXudpQuic))
        put(JSONObject().apply { put("tag", "direct"); put("protocol", "freedom") })
        put(JSONObject().apply { put("tag", "block");  put("protocol", "blackhole") })
    }

    private fun proxyOut(
        cfg: ServerConfig, hwidUuid: String?,
        enableFragment: Boolean, fragmentPackets: String, fragmentLength: String,
        fragmentInterval: String, fragmentMaxSplit: String?,
        noisesEnabled: Boolean, noiseType: String, noisePacket: String, noiseDelay: String,
        muxEnabled: Boolean, muxConcurrency: Int, muxXudpQuic: String,
    ): JSONObject {
        val effectiveUuid = hwidUuid ?: cfg.uuid ?: ""
        val stream = streamSettings(cfg, enableFragment, fragmentPackets, fragmentLength,
            fragmentInterval, fragmentMaxSplit)
        val mux = if (muxEnabled) muxObj(muxConcurrency, muxXudpQuic) else null

        return when (cfg.protocol) {
            ServerConfig.Protocol.VLESS -> JSONObject().apply {
                put("tag", "proxy"); put("protocol", "vless")
                put("settings", JSONObject().apply {
                    put("vnext", JSONArray().put(JSONObject().apply {
                        put("address", cfg.address); put("port", cfg.port)
                        put("users", JSONArray().put(JSONObject().apply {
                            put("id", effectiveUuid); put("encryption", "none")
                            cfg.flow?.let { put("flow", it) }
                        }))
                    }))
                })
                put("streamSettings", stream)
                if (mux != null && cfg.flow.isNullOrEmpty()) put("mux", mux)
                if (noisesEnabled) put("noises", noiseArray(noiseType, noisePacket, noiseDelay))
            }
            ServerConfig.Protocol.VMESS -> JSONObject().apply {
                put("tag", "proxy"); put("protocol", "vmess")
                put("settings", JSONObject().apply {
                    put("vnext", JSONArray().put(JSONObject().apply {
                        put("address", cfg.address); put("port", cfg.port)
                        put("users", JSONArray().put(JSONObject().apply {
                            put("id", effectiveUuid); put("alterId", 0); put("security", "auto")
                        }))
                    }))
                })
                put("streamSettings", stream)
                if (mux != null) put("mux", mux)
                if (noisesEnabled) put("noises", noiseArray(noiseType, noisePacket, noiseDelay))
            }
            ServerConfig.Protocol.SHADOWSOCKS -> JSONObject().apply {
                put("tag", "proxy"); put("protocol", "shadowsocks")
                put("settings", JSONObject().apply {
                    put("servers", JSONArray().put(JSONObject().apply {
                        put("address", cfg.address); put("port", cfg.port)
                        put("method", cfg.method)
                        put("password", hwidUuid ?: cfg.password ?: "")
                    }))
                })
            }
        }
    }

    private fun streamSettings(
        cfg: ServerConfig,
        enableFragment: Boolean, fragmentPackets: String, fragmentLength: String,
        fragmentInterval: String, fragmentMaxSplit: String?,
    ) = JSONObject().apply {
        put("network",  cfg.network)
        put("security", cfg.security)

        when (cfg.security) {
            "reality" -> put("realitySettings", JSONObject().apply {
                cfg.sni?.let         { put("serverName", it) }
                cfg.fingerprint?.let { put("fingerprint", it) }
                cfg.publicKey?.let   { put("publicKey", it) }
                cfg.shortId?.let     { put("shortId", it) }
                put("show", false)
            })
            "tls" -> put("tlsSettings", JSONObject().apply {
                cfg.sni?.let         { put("serverName", it) }
                cfg.fingerprint?.let { put("fingerprint", it) }
                cfg.alpn?.let { put("alpn", JSONArray().apply { it.split(",").forEach { a -> put(a.trim()) } }) }
            })
        }

        when (cfg.network) {
            "ws"          -> put("wsSettings", JSONObject().apply {
                cfg.path?.let { put("path", it) }
                cfg.host?.let { put("headers", JSONObject().put("Host", it)) }
            })
            "grpc"        -> put("grpcSettings", JSONObject().apply {
                cfg.path?.let { put("serviceName", it) }
            })
            "xhttp",
            "httpupgrade" -> put("xhttpSettings", JSONObject().apply {
                cfg.path?.let { put("path", it) }
                (cfg.host ?: cfg.sni)?.let { put("host", it) }
                put("mode", cfg.xhttpMode ?: "auto")
                cfg.xhttpExtra?.let { extra ->
                    runCatching { put("extra", JSONObject(extra)) }
                }
            })
            "h2", "http"  -> put("httpSettings", JSONObject().apply {
                cfg.path?.let { put("path", it) }
                cfg.host?.let { put("host", JSONArray().put(it)) }
            })
        }

        if (enableFragment) {
            put("sockopt", JSONObject().apply {
                put("fragment", JSONObject().apply {
                    put("packets",  fragmentPackets)
                    put("length",   fragmentLength)
                    put("interval", fragmentInterval)
                    fragmentMaxSplit?.let { put("maxSplit", it) }
                })
            })
        }
    }

    private fun muxObj(concurrency: Int, xudpQuic: String) = JSONObject().apply {
        put("enabled", true)
        put("concurrency", concurrency)
        put("xudpConcurrency", concurrency * 2)
        put("xudpProxyUDP443", xudpQuic)
    }

    private fun noiseArray(type: String, packet: String, delay: String) = JSONArray().apply {
        put(JSONObject().apply {
            put("type",   type)
            put("packet", packet)
            put("delay",  delay)
        })
    }

    private fun routing(
        blockUdp443: Boolean,
        proxyGoogle: Boolean,
        bypassLan:   Boolean,
        preferIpType: String,
    ) = JSONObject().apply {
        put("domainStrategy", "IPIfNonMatch")
        put("rules", JSONArray().apply {
            put(rule(inboundTag = "dns-in", outboundTag = "proxy"))
            if (bypassLan) put(rule(
                ips = listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
                             "127.0.0.0/8", "169.254.0.0/16", "::1/128", "fc00::/7"),
                outboundTag = "direct",
            ))
            if (blockUdp443) put(JSONObject().apply {
                put("type", "field"); put("network", "udp"); put("port", 443); put("outboundTag", "block")
            })
        })
    }

    private fun rule(
        inboundTag: String? = null,
        ip:         String? = null,
        ips:        List<String>? = null,
        domain:     String? = null,
        outboundTag: String,
    ) = JSONObject().apply {
        put("type", "field")
        inboundTag?.let { put("inboundTag", JSONArray().put(it)) }
        ip?.let          { put("ip", JSONArray().put(it)) }
        ips?.let         { put("ip", JSONArray().apply { it.forEach { c -> put(c) } }) }
        domain?.let      { put("domain", JSONArray().put(it)) }
        put("outboundTag", outboundTag)
    }

    private fun dns(remoteDns: String) = JSONObject().apply {
        put("servers", JSONArray().apply { put(remoteDns); put("8.8.8.8") })
    }
}
