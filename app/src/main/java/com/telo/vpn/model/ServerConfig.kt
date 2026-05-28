package com.telo.vpn.model

data class ServerConfig(
    val id: String,
    val remark: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    val uuid: String? = null,
    val password: String? = null,
    val method: String? = null,
    // Transport: tcp, ws, grpc, kcp, xhttp, h2
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
    val serviceName: String? = null,  // gRPC service name
    // XHTTP / SplitHTTP
    val xhttpMode: String? = null,    // "auto" | "packet-up" | "stream-up" | "stream-down"
    val xhttpExtra: String? = null,   // JSON extra config
    // Fragment (tüm outbound'a uygulanır)
    val fragmentPackets: String? = null,  // "1-1" | "tlshello" | "1-3"
    val fragmentLength: String? = null,   // "100-200"
    val fragmentInterval: String? = null, // "10-20"
    val raw: String
) {
    enum class Protocol { VLESS, VMESS, SHADOWSOCKS, TROJAN }
}
