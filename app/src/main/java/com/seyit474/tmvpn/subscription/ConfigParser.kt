package com.seyit474.tmvpn.subscription

import com.seyit474.tmvpn.model.ServerConfig
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * VLESS/VMess/Shadowsocks linklerini ServerConfig'e dönüştürür.
 *
 * Desteklenen formatlar:
 *  - vless://uuid@host:port?type=tcp&security=reality&pbk=...&sid=...&fp=chrome&sni=...&flow=xtls-rprx-vision#remark
 *  - vmess://BASE64({"v":"2","ps":"...","add":"...","port":"...","id":"...","aid":"0","net":"ws","type":"none","host":"...","path":"/","tls":"tls"})
 *  - ss://BASE64(method:password)@host:port#remark   (modern format)
 *  - ss://BASE64(method:password@host:port)#remark   (eski format)
 *
 * Base64 için Kotlin stdlib kullanılır (android.util.Base64 değil) —
 * böylece parser saf JVM unit testleriyle doğrulanabilir.
 */
object ConfigParser {

    /** Subscription cevabını parse eder. Tek satır, base64 blok ya da düz liste olabilir. */
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

    // ---------- VLESS ----------
    private fun parseVless(link: String): ServerConfig {
        // vless://uuid@host:port?params#remark
        val uri = URI(link)
        val uuid = uri.userInfo ?: error("VLESS uuid yok")
        val host = uri.host?.removeSurrounding("[", "]") ?: error("VLESS host yok")
        val port = uri.port.takeIf { it > 0 } ?: error("VLESS port yok")
        val params = parseQuery(uri.rawQuery ?: "")
        val remark = decodeFragment(uri.fragment) ?: "$host:$port"

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
            raw = link
        )
    }

    // ---------- VMess ----------
    private fun parseVmess(link: String): ServerConfig {
        val b64 = link.removePrefix("vmess://").substringBefore("#")
        val json = JSONObject(decodeBase64ToString(b64))
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

    // ---------- Shadowsocks ----------
    private fun parseShadowsocks(link: String): ServerConfig {
        val body = link.removePrefix("ss://")
        val (remarkRaw, main) = if (body.contains("#")) {
            val parts = body.split("#", limit = 2)
            decodeFragment(parts[1]) to parts[0]
        } else null to body

        // Modern format: BASE64(method:password)@host:port
        // Eski format:   BASE64(method:password@host:port)
        val (userInfo, hostPort) = if (main.contains("@")) {
            val parts = main.split("@", limit = 2)
            val ui = runCatching { decodeBase64ToString(parts[0]) }.getOrDefault(parts[0])
            ui to parts[1]
        } else {
            val decoded = decodeBase64ToString(main)
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

    // ---------- yardımcılar ----------
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

    /**
     * URL-safe / padding'siz varyantları da kabul eden esnek base64 decode.
     * Subscription üreticileri bu konuda tutarsız olabiliyor.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64ToString(s: String): String {
        val normalized = s.trim()
            .replace('-', '+')
            .replace('_', '/')
            .replace("\r", "")
            .replace("\n", "")
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(padded).decodeToString()
    }

    private fun tryBase64Decode(s: String): String? = runCatching {
        // Subscription'lar genelde tüm cevabı base64'ler
        decodeBase64ToString(s).takeIf { it.contains("://") }
    }.getOrNull()

    private fun stableId(s: String): String =
        s.hashCode().toUInt().toString(16)
}
