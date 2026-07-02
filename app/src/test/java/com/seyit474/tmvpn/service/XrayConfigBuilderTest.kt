package com.seyit474.tmvpn.service

import com.seyit474.tmvpn.model.ServerConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    private fun vlessReality() = ServerConfig(
        id = "1",
        remark = "TM-1",
        protocol = ServerConfig.Protocol.VLESS,
        address = "1.2.3.4",
        port = 443,
        uuid = "uuid-1",
        security = "reality",
        sni = "www.example.com",
        fingerprint = "chrome",
        publicKey = "PBK",
        shortId = "SID",
        flow = "xtls-rprx-vision",
        raw = "vless://..."
    )

    @Test
    fun `vless reality config gecerli json uretir`() {
        val json = JSONObject(XrayConfigBuilder.build(vlessReality()))

        val outbound = json.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("vless", outbound.getString("protocol"))
        assertEquals("proxy", outbound.getString("tag"))

        val user = outbound.getJSONObject("settings")
            .getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0)
        assertEquals("uuid-1", user.getString("id"))
        assertEquals("xtls-rprx-vision", user.getString("flow"))

        val stream = outbound.getJSONObject("streamSettings")
        assertEquals("reality", stream.getString("security"))
        val reality = stream.getJSONObject("realitySettings")
        assertEquals("PBK", reality.getString("publicKey"))
        assertEquals("SID", reality.getString("shortId"))
    }

    @Test
    fun `socks inbound dogru portta acilir`() {
        val json = JSONObject(XrayConfigBuilder.build(vlessReality()))
        val socks = json.getJSONArray("inbounds").getJSONObject(0)
        assertEquals(XrayConfigBuilder.SOCKS_PORT, socks.getInt("port"))
        assertEquals("socks", socks.getString("protocol"))
        assertEquals("127.0.0.1", socks.getString("listen"))
    }

    @Test
    fun `shadowsocks outbound method ve password icerir`() {
        val cfg = ServerConfig(
            id = "2", remark = "SS", protocol = ServerConfig.Protocol.SHADOWSOCKS,
            address = "5.6.7.8", port = 8388,
            method = "chacha20-ietf-poly1305", password = "pw", raw = "ss://..."
        )
        val json = JSONObject(XrayConfigBuilder.build(cfg))
        val server = json.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("chacha20-ietf-poly1305", server.getString("method"))
        assertEquals("pw", server.getString("password"))
    }

    @Test
    fun `routing dns dongusunu engeller`() {
        val json = JSONObject(XrayConfigBuilder.build(vlessReality()))
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        val dnsRule = (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .first { it.has("inboundTag") }
        assertTrue(dnsRule.getJSONArray("inboundTag").getString(0) == "dns-in")
        assertEquals("proxy", dnsRule.getString("outboundTag"))
    }
}
