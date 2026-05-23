package com.seyit474.tmvpn.service

import com.seyit474.tmvpn.model.AppSettings
import com.seyit474.tmvpn.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    private const val SOCKS_PORT = 10808
    private const val DNS_PORT   = 10853

    fun build(cfg: ServerConfig, settings: AppSettings = AppSettings()): String =
        JSONObject().apply {
            put("log",       JSONObject().put("loglevel", "warning"))
            put("inbounds",  inbounds())
            put("outbounds", outbounds(cfg, settings))
            put("routing",   routing(settings))
            put("dns",       dns())
        }.toString(2)

    // ─── inbounds ────────────────────────────────────────────────────────────

    private fun inbounds(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", SOCKS_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply { put("http"); put("tls"); put("quic") })
            })
        })
        put(JSONObject().apply {
            put("tag", "dns-in")
            put("port", DNS_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "dokodemo-door")
            put("settings", JSONObject().apply {
                put("address", "1.1.1.1")
                put("port", 53)
                put("network", "tcp,udp")
            })
        })
    }

    // ─── outbounds ───────────────────────────────────────────────────────────

    private fun outbounds(cfg: ServerConfig, settings: AppSettings): JSONArray = JSONArray().apply {
        put(proxyOutbound(cfg, settings))
        put(JSONObject().apply { put("tag", "direct"); put("protocol", "freedom") })
        put(JSONObject().apply { put("tag", "block");  put("protocol", "blackhole") })
    }

    private fun proxyOutbound(cfg: ServerConfig, settings: AppSettings): JSONObject =
        when (cfg.protocol) {
            ServerConfig.Protocol.VLESS       -> vlessOutbound(cfg, settings)
            ServerConfig.Protocol.VMESS       -> vmessOutbound(cfg, settings)
            ServerConfig.Protocol.SHADOWSOCKS -> ssOutbound(cfg)
        }

    private fun vlessOutbound(cfg: ServerConfig, settings: AppSettings) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "vless")
        put("settings", JSONObject().apply {
            put("vnext", JSONArray().put(JSONObject().apply {
                put("address", cfg.address)
                put("port", cfg.port)
                put("users", JSONArray().put(JSONObject().apply {
                    put("id", cfg.uuid)
                    put("encryption", "none")
                    cfg.flow?.let { put("flow", it) }
                }))
            }))
        })
        put("streamSettings", streamSettings(cfg, settings))
        if (settings.muxEnabled && cfg.flow.isNullOrEmpty()) {
            put("mux", muxObject(settings))
        }
    }

    private fun vmessOutbound(cfg: ServerConfig, settings: AppSettings) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "vmess")
        put("settings", JSONObject().apply {
            put("vnext", JSONArray().put(JSONObject().apply {
                put("address", cfg.address)
                put("port", cfg.port)
                put("users", JSONArray().put(JSONObject().apply {
                    put("id", cfg.uuid)
                    put("alterId", 0)
                    put("security", "auto")
                }))
            }))
        })
        put("streamSettings", streamSettings(cfg, settings))
        if (settings.muxEnabled) put("mux", muxObject(settings))
    }

    private fun ssOutbound(cfg: ServerConfig) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "shadowsocks")
        put("settings", JSONObject().apply {
            put("servers", JSONArray().put(JSONObject().apply {
                put("address", cfg.address)
                put("port", cfg.port)
                put("method", cfg.method)
                put("password", cfg.password)
            }))
        })
    }

    // ─── stream settings (TLS / REALITY / WS / gRPC) + fragment ─────────────

    private fun streamSettings(cfg: ServerConfig, settings: AppSettings) = JSONObject().apply {
        put("network", cfg.network)
        put("security", cfg.security)

        when (cfg.security) {
            "reality" -> put("realitySettings", JSONObject().apply {
                cfg.sni?.let { put("serverName", it) }
                cfg.fingerprint?.let { put("fingerprint", it) }
                cfg.publicKey?.let { put("publicKey", it) }
                cfg.shortId?.let { put("shortId", it) }
                put("show", false)
            })
            "tls" -> put("tlsSettings", JSONObject().apply {
                cfg.sni?.let { put("serverName", it) }
                cfg.fingerprint?.let { put("fingerprint", it) }
                cfg.alpn?.let {
                    put("alpn", JSONArray().apply { it.split(",").forEach { a -> put(a.trim()) } })
                }
            })
        }

        when (cfg.network) {
            "ws"   -> put("wsSettings", JSONObject().apply {
                cfg.path?.let { put("path", it) }
                cfg.host?.let { put("headers", JSONObject().put("Host", it)) }
            })
            "grpc" -> put("grpcSettings", JSONObject().apply {
                cfg.path?.let { put("serviceName", it) }
            })
        }

        // Fragment — DPI atlatma (Türkmenistan için tlshello)
        if (settings.fragmentEnabled) {
            put("sockopt", JSONObject().apply {
                put("fragment", JSONObject().apply {
                    put("packets",  settings.fragmentPackets)
                    put("length",   settings.fragmentLength)
                    put("interval", settings.fragmentInterval)
                })
            })
        }
    }

    // ─── mux ─────────────────────────────────────────────────────────────────

    private fun muxObject(settings: AppSettings) = JSONObject().apply {
        put("enabled",          true)
        put("concurrency",      8)
        put("xudpConcurrency",  8)
        put("xudpProxyUDP443",  settings.quicMux)  // "reject" | "disable"
    }

    // ─── routing ─────────────────────────────────────────────────────────────

    private fun routing(settings: AppSettings): JSONObject = JSONObject().apply {
        put("domainStrategy", "IPIfNonMatch")
        put("rules", JSONArray().apply {
            // DNS trafiğini proxy'ye yönlendir (döngü önleme)
            put(rule(inboundTag = "dns-in", outboundTag = "proxy"))
            // Private IP'ler doğrudan
            put(rule(ip = "geoip:private", outboundTag = "direct"))
            // Reklam engelle
            put(rule(domain = "geosite:category-ads-all", outboundTag = "block"))
            // UDP 443 engelle (QUIC → TikTok vs TCP'ye düşsün)
            if (settings.blockUdp443) {
                put(JSONObject().apply {
                    put("type", "field")
                    put("network", "udp")
                    put("port", 443)
                    put("outboundTag", "block")
                })
            }
            // Google → proxy (Türkmenistan'da erişim için)
            if (settings.forceGoogleProxy) {
                put(rule(domain = "geosite:google", outboundTag = "proxy"))
            }
        })
    }

    private fun rule(
        inboundTag: String? = null,
        ip: String? = null,
        domain: String? = null,
        outboundTag: String,
    ) = JSONObject().apply {
        put("type", "field")
        inboundTag?.let { put("inboundTag", JSONArray().put(it)) }
        ip?.let         { put("ip",         JSONArray().put(it)) }
        domain?.let     { put("domain",     JSONArray().put(it)) }
        put("outboundTag", outboundTag)
    }

    // ─── dns ─────────────────────────────────────────────────────────────────

    private fun dns(): JSONObject = JSONObject().apply {
        put("servers", JSONArray().apply { put("1.1.1.1"); put("8.8.8.8") })
    }
}
