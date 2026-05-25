package com.tmvpn.app.subscription

import android.util.Base64
import com.tmvpn.app.model.ServerConfig
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

object ConfigParser {

    fun parseSubscription(raw: String): List<ServerConfig> {
        val decoded = tryBase64Decode(raw.trim()) ?: raw
        return decoded.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { parseLink(it) }
            .toList()
    }

    fun parseLink(link: String): ServerConfig? = runCatching {
        when {
            link.startsWith("vless://", true) -> parseVless(link)
            link.startsWith("vmess://", true) -> parseVmess(link)
            link.startsWith("ss://", true) -> parseShadowsocks(link)
            else -> null
        }
    }.getOrNull()

    private fun parseVless(link: String): ServerConfig {
        val uri = URI(link)
        val uuid = uri.userInfo ?: error("VLESS uuid yok")
        val host = uri.host ?: error("VLESS host yok")
        val port = uri.port.takeIf { it > 0 } ?: error("VLESS port yok")
        val params = parseQuery(uri.rawQuery ?: "")
        val remark = decodeFragment(uri.fragment) ?: "$host:$port"

        val (fragEnabled, fragPackets, fragLength, fragInterval) = parseFragment(params["fragment"])

        return ServerConfig(
            id = stableId(link),
            remark = remark,
            protocol = ServerConfig.Protocol.VLESS,
            address = host,
            port = port,
            uuid = uuid,
            network = params["type"] ?: "tcp",
            security = params["security"] ?: "none",
            sni = params["sni"],
            fingerprint = params["fp"],
            publicKey = params["pbk"],
            shortId = params["sid"],
            flow = params["flow"],
            path = params["path"],
            host = params["host"],
            alpn = params["alpn"],
            fragmentEnabled = fragEnabled,
            fragmentPackets = fragPackets,
            fragmentLength = fragLength,
            fragmentInterval = fragInterval,
            xhttpMode = params["mode"],
            xhttpExtra = params["extra"],
            raw = link
        )
    }

    private fun parseVmess(link: String): ServerConfig {
        val b64 = link.removePrefix("vmess://").substringBefore("#")
        val json = JSONObject(String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE)))
        val host = json.getString("add")
        val port = json.getString("port").toInt()
        return ServerConfig(
            id = stableId(link),
            remark = json.optString("ps").ifEmpty { "$host:$port" },
            protocol = ServerConfig.Protocol.VMESS,
            address = host,
            port = port,
            uuid = json.getString("id"),
            network = json.optString("net", "tcp"),
            security = if (json.optString("tls").isNotEmpty()) "tls" else "none",
            sni = json.optString("sni").ifEmpty { null },
            host = json.optString("host").ifEmpty { null },
            path = json.optString("path").ifEmpty { null },
            raw = link
        )
    }

    private fun parseShadowsocks(link: String): ServerConfig {
        val body = link.removePrefix("ss://")
        val (remarkRaw, main) = if (body.contains("#")) {
            val parts = body.split("#", limit = 2)
            decodeFragment(parts[1]) to parts[0]
        } else null to body

        val (userInfo, hostPort) = if (main.contains("@")) {
            val parts = main.split("@", limit = 2)
            val ui = runCatching {
                String(Base64.decode(parts[0], Base64.DEFAULT or Base64.URL_SAFE))
            }.getOrDefault(parts[0])
            ui to parts[1]
        } else {
            val decoded = String(Base64.decode(main, Base64.DEFAULT or Base64.URL_SAFE))
            val parts = decoded.split("@", limit = 2)
            parts[0] to parts[1]
        }

        val (method, password) = userInfo.split(":", limit = 2).let { it[0] to it[1] }
        val (host, portStr) = hostPort.split(":", limit = 2).let { it[0] to it[1].substringBefore("/") }
        val port = portStr.toInt()

        return ServerConfig(
            id = stableId(link),
            remark = remarkRaw ?: "$host:$port",
            protocol = ServerConfig.Protocol.SHADOWSOCKS,
            address = host,
            port = port,
            method = method,
            password = password,
            raw = link
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").mapNotNull {
            val idx = it.indexOf('=')
            if (idx < 0) null
            else URLDecoder.decode(it.substring(0, idx), "UTF-8") to
                URLDecoder.decode(it.substring(idx + 1), "UTF-8")
        }.toMap()
    }

    private fun decodeFragment(f: String?): String? =
        f?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    private fun tryBase64Decode(s: String): String? {
        val flags = intArrayOf(Base64.DEFAULT, Base64.URL_SAFE, Base64.DEFAULT or Base64.URL_SAFE)
        for (f in flags) {
            runCatching {
                String(Base64.decode(s.trim(), f)).takeIf { it.contains("://") }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun stableId(s: String): String =
        s.hashCode().toUInt().toString(16)

    // fragment param format: "length,interval,packets"  e.g. "1-3,1-1,tlshello"
    private data class FragInfo(
        val enabled: Boolean,
        val packets: String,
        val length: String,
        val interval: String,
    )

    private fun parseFragment(raw: String?): FragInfo {
        if (raw.isNullOrBlank()) return FragInfo(false, "tlshello", "1-3", "1-1")
        val parts = raw.split(",").map { it.trim() }
        return when (parts.size) {
            3 -> FragInfo(true, parts[2], parts[0], parts[1])
            2 -> FragInfo(true, "tlshello", parts[0], parts[1])
            else -> FragInfo(true, "tlshello", "1-3", "1-1")
        }
    }
}
