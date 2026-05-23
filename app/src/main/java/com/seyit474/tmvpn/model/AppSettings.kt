package com.seyit474.tmvpn.model

data class AppSettings(
    val fragmentEnabled:  Boolean = false,
    val fragmentPackets:  String  = "tlshello",
    val fragmentLength:   String  = "1-3",
    val fragmentInterval: String  = "1-1",
    val muxEnabled:       Boolean = true,
    val quicMux:          String  = "reject",   // reject | disable
    val blockUdp443:      Boolean = true,
    val forceGoogleProxy: Boolean = true,
)
