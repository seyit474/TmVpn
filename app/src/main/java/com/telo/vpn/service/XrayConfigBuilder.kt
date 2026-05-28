package com.telo.vpn.service

import com.telo.vpn.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {

    const val SOCKS_PORT = 10808
    const val DNS_PORT = 10853

    /**
     * [tunMode=true]  → sadece outbound + routing. Go core tun fd'yi doğrudan yönetir.
     * [tunMode=false] → SOCKS inbound 10808 eklenir (test/debug için).
     */
    fun build(cfg: ServerConfig, tunMode: Boolean = true): String {
        return JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("stats", JSONObject())
            put("api", JSONObject().apply {
                put("tag", "api")
                put("services", JSONArray().apply {
                    put("HandlerService")
                    put("StatsService")
                })
            })
            put("inbounds", inbounds(tunMode))
            put("outbounds", outbounds(cfg))
            put("routing", routing())
            put("dns", dns())
            put("policy", policy())
        }.toString(2)
    }

    private fun inbounds(tunMode: Boolean) = JSONArray().apply {
        if (!tunMode) {
            // SOCKS inbound — sadece tün olmadan (debug) kullanılır
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
                    put("destOverride", JSONArray().apply {
                        put("http"); put("tls"); put("quic")
                    })
                })
            })
        }
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
        put(JSONObject().apply {
            put("tag", "api-in")
            put("port", 10085)
            put("listen", "127.0.0.1")
            put("protocol", "dokodemo-door")
            put("settings", JSONObject().apply {
                put("address", "127.0.0.1")
            })
        })
    }

    private fun outbounds(cfg: ServerConfig) = JSONArray().apply {
        put(proxyOutbound(cfg))
        put(JSONObject().apply { put("tag", "direct"); put("protocol", "freedom") })
        put(JSONObject().apply { put("tag", "block"); put("protocol", "blackhole") })
    }

    private fun proxyOutbound(cfg: ServerConfig) = when (cfg.protocol) {
        ServerConfig.Protocol.VLESS       -> vlessOutbound(cfg)
        ServerConfig.Protocol.VMESS       -> vmessOutbound(cfg)
        ServerConfig.Protocol.SHADOWSOCKS -> ssOutbound(cfg)
        ServerConfig.Protocol.TROJAN      -> trojanOutbound(cfg)
    }

    private fun vlessOutbound(cfg: ServerConfig) = JSONObject().apply {
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
        put("streamSettings", streamSettings(cfg))
        put("mux", JSONObject().put("enabled", false))
        cfg.fragmentPackets?.let {
            put("fragment", JSONObject().apply {
                put("packets", it)
                cfg.fragmentLength?.let { l -> put("length", l) }
                cfg.fragmentInterval?.let { i -> put("interval", i) }
            })
        }
    }

    private fun vmessOutbound(cfg: ServerConfig) = JSONObject().apply {
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
        put("streamSettings", streamSettings(cfg))
        put("mux", JSONObject().put("enabled", true).put("concurrency", 8))
        cfg.fragmentPackets?.let {
            put("fragment", JSONObject().apply {
                put("packets", it)
                cfg.fragmentLength?.let { l -> put("length", l) }
                cfg.fragmentInterval?.let { i -> put("interval", i) }
            })
        }
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

    private fun trojanOutbound(cfg: ServerConfig) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "trojan")
        put("settings", JSONObject().apply {
            put("servers", JSONArray().put(JSONObject().apply {
                put("address", cfg.address)
                put("port", cfg.port)
                put("password", cfg.password)
            }))
        })
        put("streamSettings", streamSettings(cfg))
        cfg.fragmentPackets?.let {
            put("fragment", JSONObject().apply {
                put("packets", it)
                cfg.fragmentLength?.let { l -> put("length", l) }
                cfg.fragmentInterval?.let { i -> put("interval", i) }
            })
        }
    }

    private fun streamSettings(cfg: ServerConfig) = JSONObject().apply {
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
            "ws" -> put("wsSettings", JSONObject().apply {
                cfg.path?.let { put("path", it) }
                cfg.host?.let { put("headers", JSONObject().put("Host", it)) }
            })
            "grpc" -> put("grpcSettings", JSONObject().apply {
                val svcName = cfg.serviceName ?: cfg.path ?: ""
                put("serviceName", svcName)
            })
            "h2" -> put("httpSettings", JSONObject().apply {
                cfg.host?.let { put("host", JSONArray().put(it)) }
                cfg.path?.let { put("path", it) }
            })
            "xhttp" -> put("xhttpSettings", JSONObject().apply {
                cfg.path?.let { put("path", it) }
                cfg.host?.let { put("host", it) }
                cfg.xhttpMode?.let { put("mode", it) }
                cfg.xhttpExtra?.takeIf { it.isNotBlank() }?.let {
                    runCatching { put("extra", JSONObject(it)) }
                }
            })
        }
    }

    private fun routing() = JSONObject().apply {
        put("domainStrategy", "IPIfNonMatch")
        put("rules", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().put("api-in"))
                put("outboundTag", "api")
            })
            put(JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().put("dns-in"))
                put("outboundTag", "proxy")
            })
            put(JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray().put("geoip:private"))
                put("outboundTag", "direct")
            })
            put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().put("geosite:category-ads-all"))
                put("outboundTag", "block")
            })
        })
    }

    private fun dns() = JSONObject().apply {
        put("servers", JSONArray().apply {
            put(JSONObject().apply {
                put("address", "https://1.1.1.1/dns-query")
                put("domains", JSONArray().put("geosite:geolocation-!cn"))
            })
            put("1.1.1.1")
            put("8.8.8.8")
        })
        put("queryStrategy", "UseIP")
    }

    private fun policy() = JSONObject().apply {
        put("system", JSONObject().apply {
            put("statsOutboundUplink", true)
            put("statsOutboundDownlink", true)
        })
    }
}
