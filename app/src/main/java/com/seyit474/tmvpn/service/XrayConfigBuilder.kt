package com.seyit474.tmvpn.service

import com.seyit474.tmvpn.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * ServerConfig'i Xray-core'un anlayacağı JSON yapısına çevirir.
 *
 * SOCKS inbound 10808 + DNS inbound 10853 + outbound (vless/vmess/ss).
 * VpnService tarafından açılan tun arayüzü, tun2socks ile bu SOCKS portuna
 * yönlendirilir. Böylece tüm cihaz trafiği Xray üzerinden geçer.
 */
object XrayConfigBuilder {

    const val SOCKS_PORT = 10808
    const val DNS_PORT = 10853

    fun build(cfg: ServerConfig): String {
        val root = JSONObject().apply {
            put("log", JSONObject().put("loglevel", "warning"))
            put("inbounds", inbounds())
            put("outbounds", outbounds(cfg))
            put("routing", routing())
            put("dns", dns())
        }
        return root.toString(2)
    }

    private fun inbounds(): JSONArray = JSONArray().apply {
        // SOCKS — tun2socks bağlanacak
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
        // DNS in
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

    private fun outbounds(cfg: ServerConfig): JSONArray = JSONArray().apply {
        put(proxyOutbound(cfg))
        put(JSONObject().apply { put("tag", "direct"); put("protocol", "freedom") })
        put(JSONObject().apply { put("tag", "block"); put("protocol", "blackhole") })
    }

    private fun proxyOutbound(cfg: ServerConfig): JSONObject {
        return when (cfg.protocol) {
            ServerConfig.Protocol.VLESS -> vlessOutbound(cfg)
            ServerConfig.Protocol.VMESS -> vmessOutbound(cfg)
            ServerConfig.Protocol.SHADOWSOCKS -> ssOutbound(cfg)
        }
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
                cfg.path?.let { put("serviceName", it) }
            })
        }
    }

    private fun routing(): JSONObject = JSONObject().apply {
        put("domainStrategy", "IPIfNonMatch")
        put("rules", JSONArray().apply {
            // DNS trafiğini DNS outbound'a yönlendir (yoksa döngü olur)
            put(JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().put("dns-in"))
                put("outboundTag", "proxy")
            })
            // private IP'leri direct
            put(JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray().put("geoip:private"))
                put("outboundTag", "direct")
            })
            // reklam / kötü amaçlı engelle
            put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().put("geosite:category-ads-all"))
                put("outboundTag", "block")
            })
        })
    }

    private fun dns(): JSONObject = JSONObject().apply {
        put("servers", JSONArray().apply {
            put("1.1.1.1")
            put("8.8.8.8")
        })
    }
}
