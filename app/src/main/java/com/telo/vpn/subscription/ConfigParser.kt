package com.telo.vpn.subscription

import android.util.Base64
import com.telo.vpn.model.ServerConfig
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

    fun parseSubscriptionWithHwid(raw: String, hwidUuid: String): List<ServerConfig> =
        parseSubscription(raw).map { config ->
            when (config.protocol) {
                ServerConfig.Protocol.VLESS, ServerConfig.Protocol.VMESS -> replaceUuid(config, hwidUuid)
                else -> config
            }
        }

    private fun replaceUuid(config: ServerConfig, uuid: String): ServerConfig {
        val newRaw = when (config.protocol) {
            ServerConfig.Protocol.VLESS ->
                config.raw.replaceFirst(Regex("(?<=vless://)[^@]+(?=@)"), uuid)
            ServerConfig.Protocol.VMESS -> {
                val b64 = config.raw.removePrefix("vmess://").substringBefore("#")
                val remark = config.raw.substringAfter("#", "")
                val json = JSONObject(String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE)))
                json.put("id", uuid)
                val newB64 = Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)
                "vmess://$newB64" + if (remark.isNotEmpty()) "#$remark" else ""
            }
            else -> config.raw
        }
        return config.copy(uuid = uuid, raw = newRaw)
    }

    fun parseLink(link: String): ServerConfig? = runCatching {
        when {
            link.startsWith("vless://", true)   -> parseVless(link)
            link.startsWith("vmess://", true)   -> parseVmess(link)
            link.startsWith("ss://", true)      -> parseShadowsocks(link)
            link.startsWith("trojan://", true)  -> parseTrojan(link)
            else -> null
        }
    }.getOrNull()

    // ─── VLESS ───────────────────────────────────────────────────────────────
    private fun parseVless(link: String): ServerConfig {
        val uri = URI(link)
        val uuid = uri.userInfo ?: error("VLESS uuid yok")
        val host = uri.host ?: error("VLESS host yok")
        val port = uri.port.takeIf { it > 0 } ?: error("VLESS port yok")
        val params = parseQuery(uri.rawQuery ?: "")
        val remark = decodeFragment(uri.fragment) ?: "$host:$port"
        val network = params["type"] ?: "tcp"

        return ServerConfig(
            id = stableId(link), remark = remark,
            protocol = ServerConfig.Protocol.VLESS,
            address = host, port = port, uuid = uuid,
            network = network,
            security = params["security"] ?: "none",
            sni = params["sni"], fingerprint = params["fp"],
            publicKey = params["pbk"], shortId = params["sid"],
            flow = params["flow"],
            path = params["path"],
            host = params["host"],
            serviceName = params["serviceName"],
            alpn = params["alpn"],
            xhttpMode = if (network == "xhttp") params["mode"] else null,
            xhttpExtra = if (network == "xhttp") params["extra"] else null,
            raw = link
        )
    }

    // ─── VMess ───────────────────────────────────────────────────────────────
    private fun parseVmess(link: String): ServerConfig {
        val b64 = link.removePrefix("vmess://").substringBefore("#")
        val json = JSONObject(String(Base64.decode(b64, Base64.DEFAULT or Base64.URL_SAFE)))
        val host = json.getString("add")
        val port = json.getString("port").toInt()
        val network = json.optString("net", "tcp")
        return ServerConfig(
            id = stableId(link),
            remark = json.optString("ps").ifEmpty { "$host:$port" },
            protocol = ServerConfig.Protocol.VMESS,
            address = host, port = port, uuid = json.getString("id"),
            network = network,
            security = if (json.optString("tls").isNotEmpty()) "tls" else "none",
            sni = json.optString("sni").ifEmpty { null },
            host = json.optString("host").ifEmpty { null },
            path = json.optString("path").ifEmpty { null },
            serviceName = json.optString("path").ifEmpty { null }.takeIf { network == "grpc" },
            raw = link
        )
    }

    // ─── Shadowsocks ─────────────────────────────────────────────────────────
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
        val (host, portStr) = hostPort.split(":", limit = 2).let {
            it[0] to it[1].substringBefore("/").substringBefore("?")
        }
        return ServerConfig(
            id = stableId(link),
            remark = remarkRaw ?: "$host:${portStr.toInt()}",
            protocol = ServerConfig.Protocol.SHADOWSOCKS,
            address = host, port = portStr.toInt(),
            method = method, password = password,
            raw = link
        )
    }

    // ─── Trojan ──────────────────────────────────────────────────────────────
    private fun parseTrojan(link: String): ServerConfig {
        val uri = URI(link)
        val password = uri.userInfo ?: error("Trojan password yok")
        val host = uri.host ?: error("Trojan host yok")
        val port = uri.port.takeIf { it > 0 } ?: error("Trojan port yok")
        val params = parseQuery(uri.rawQuery ?: "")
        val remark = decodeFragment(uri.fragment) ?: "$host:$port"
        val network = params["type"] ?: "tcp"
        return ServerConfig(
            id = stableId(link), remark = remark,
            protocol = ServerConfig.Protocol.TROJAN,
            address = host, port = port,
            password = password,
            network = network,
            security = params["security"] ?: "tls",
            sni = params["sni"], fingerprint = params["fp"],
            path = params["path"], host = params["host"],
            alpn = params["alpn"],
            xhttpMode = if (network == "xhttp") params["mode"] else null,
            raw = link
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
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

    private fun tryBase64Decode(s: String): String? = runCatching {
        String(Base64.decode(s, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP))
            .takeIf { it.contains("://") }
    }.getOrNull()

    private fun stableId(s: String): String = s.hashCode().toUInt().toString(16)
}
