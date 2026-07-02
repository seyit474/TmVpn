package com.seyit474.tmvpn.subscription

import com.seyit474.tmvpn.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ConfigParserTest {

    // ---------- VLESS ----------

    @Test
    fun `vless reality linki tum parametrelerle parse edilir`() {
        val link = "vless://uuid-1234@1.2.3.4:443" +
            "?type=tcp&security=reality&pbk=PUBKEY&sid=abcd&fp=chrome" +
            "&sni=www.example.com&flow=xtls-rprx-vision#TM-1"

        val cfg = ConfigParser.parseLink(link)!!

        assertEquals(ServerConfig.Protocol.VLESS, cfg.protocol)
        assertEquals("uuid-1234", cfg.uuid)
        assertEquals("1.2.3.4", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("reality", cfg.security)
        assertEquals("PUBKEY", cfg.publicKey)
        assertEquals("abcd", cfg.shortId)
        assertEquals("chrome", cfg.fingerprint)
        assertEquals("www.example.com", cfg.sni)
        assertEquals("xtls-rprx-vision", cfg.flow)
        assertEquals("TM-1", cfg.remark)
    }

    @Test
    fun `vless remark URL-encoded ise cozulur`() {
        val link = "vless://u@h.example.com:8443?security=tls#TM%20Sunucu%201"
        assertEquals("TM Sunucu 1", ConfigParser.parseLink(link)!!.remark)
    }

    // ---------- VMess ----------

    @Test
    fun `vmess base64 json parse edilir`() {
        val json = """{"v":"2","ps":"TM-WS","add":"vpn.example.com","port":"443",
            "id":"uuid-xyz","aid":"0","net":"ws","host":"cdn.example.com",
            "path":"/ws","tls":"tls"}""".trimIndent()
        val link = "vmess://" + Base64.getEncoder().encodeToString(json.toByteArray())

        val cfg = ConfigParser.parseLink(link)!!

        assertEquals(ServerConfig.Protocol.VMESS, cfg.protocol)
        assertEquals("TM-WS", cfg.remark)
        assertEquals("vpn.example.com", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("uuid-xyz", cfg.uuid)
        assertEquals("ws", cfg.network)
        assertEquals("tls", cfg.security)
        assertEquals("cdn.example.com", cfg.host)
        assertEquals("/ws", cfg.path)
    }

    @Test
    fun `vmess padding'siz base64 de kabul edilir`() {
        val json = """{"ps":"NP","add":"h.example.com","port":"80","id":"u1"}"""
        val b64 = Base64.getEncoder().withoutPadding()
            .encodeToString(json.toByteArray())

        val cfg = ConfigParser.parseLink("vmess://$b64")!!
        assertEquals("NP", cfg.remark)
        assertEquals(80, cfg.port)
    }

    // ---------- Shadowsocks ----------

    @Test
    fun `ss modern format parse edilir`() {
        val userInfo = Base64.getEncoder()
            .encodeToString("chacha20-ietf-poly1305:sifre123".toByteArray())
        val link = "ss://$userInfo@ss.example.com:8388#SS-1"

        val cfg = ConfigParser.parseLink(link)!!

        assertEquals(ServerConfig.Protocol.SHADOWSOCKS, cfg.protocol)
        assertEquals("chacha20-ietf-poly1305", cfg.method)
        assertEquals("sifre123", cfg.password)
        assertEquals("ss.example.com", cfg.address)
        assertEquals(8388, cfg.port)
        assertEquals("SS-1", cfg.remark)
    }

    @Test
    fun `ss eski format parse edilir`() {
        val payload = Base64.getEncoder()
            .encodeToString("aes-256-gcm:pw@legacy.example.com:443".toByteArray())
        val cfg = ConfigParser.parseLink("ss://$payload#Eski")!!

        assertEquals("aes-256-gcm", cfg.method)
        assertEquals("pw", cfg.password)
        assertEquals("legacy.example.com", cfg.address)
        assertEquals(443, cfg.port)
        assertEquals("Eski", cfg.remark)
    }

    // ---------- Subscription ----------

    @Test
    fun `duz metin subscription satirlari parse edilir`() {
        val raw = """
            # yorum satiri atlanir
            vless://u1@a.example.com:443?security=tls#S1

            vless://u2@b.example.com:443?security=tls#S2
        """.trimIndent()

        val list = ConfigParser.parseSubscription(raw)
        assertEquals(2, list.size)
        assertEquals("S1", list[0].remark)
        assertEquals("S2", list[1].remark)
    }

    @Test
    fun `base64 kodlu subscription otomatik cozulur`() {
        val plain = "vless://u1@a.example.com:443?security=tls#S1\n" +
            "vless://u2@b.example.com:443?security=tls#S2"
        val raw = Base64.getEncoder().encodeToString(plain.toByteArray())

        val list = ConfigParser.parseSubscription(raw)
        assertEquals(2, list.size)
    }

    @Test
    fun `bilinmeyen scheme ve bozuk linkler sessizce atlanir`() {
        assertNull(ConfigParser.parseLink("http://example.com"))
        assertNull(ConfigParser.parseLink("vmess://%%%bozuk%%%"))

        val list = ConfigParser.parseSubscription(
            "trojan://x@h:1#skip\nvless://u@ok.example.com:443?security=tls#OK"
        )
        assertEquals(1, list.size)
        assertEquals("OK", list[0].remark)
    }

    @Test
    fun `ayni link ayni id yi uretir`() {
        val link = "vless://u@h.example.com:443?security=tls#X"
        val a = ConfigParser.parseLink(link)!!
        val b = ConfigParser.parseLink(link)!!
        assertEquals(a.id, b.id)
        assertTrue(a.id.isNotBlank())
    }
}
