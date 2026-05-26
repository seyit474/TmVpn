package com.tmvpn.app.model

data class ServerConfig(
    val id: String,
    val remark: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val method: String? = null,
    val network: String = "tcp",
    val security: String = "none",
    val sni: String? = null,
    val fingerprint: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val flow: String? = null,
    val path: String? = null,
    val host: String? = null,
    val alpn: String? = null,
    val fragmentEnabled: Boolean = false,
    val fragmentPackets: String = "tlshello",
    val fragmentLength: String = "1-3",
    val fragmentInterval: String = "1-1",
    val xhttpMode: String? = null,
    val xhttpExtra: String? = null,
    val raw: String
) {
    enum class Protocol { VLESS, VMESS, SHADOWSOCKS }
}
