package com.seyit474.tmvpn.service

import android.net.VpnService
import android.util.Log
import java.lang.reflect.Proxy

object XrayCoreProxy {
    private const val TAG = "XrayCoreProxy"

    private var v2RayPoint: Any? = null

    fun start(configJson: String, vpnService: VpnService): Boolean {
        stop()
        return startV2Ray(configJson, vpnService) || startXrayLegacy(configJson, vpnService)
    }

    // libv2ray.aar (2dust/AndroidLibV2ray) API
    private fun startV2Ray(configJson: String, vpnService: VpnService): Boolean {
        return try {
            val libCls   = Class.forName("libv2ray.LibV2ray")
            val pointCls = Class.forName("libv2ray.V2RayPoint")
            val supCls   = Class.forName("libv2ray.V2RayVPNServiceSupportsSet")

            // initV2Env — ignore if method signature differs
            runCatching {
                libCls.getMethod("initV2Env", String::class.java, String::class.java)
                    .invoke(null,
                        vpnService.filesDir.absolutePath,
                        vpnService.filesDir.absolutePath)
            }

            // Dynamic proxy so we don't need to implement the interface at compile time.
            // protect() delegates to VpnService.protect() for socket protection.
            val handler = Proxy.newProxyInstance(supCls.classLoader, arrayOf(supCls)) { _, method, args ->
                when (method.name) {
                    "protect"       -> vpnService.protect((args[0] as Int))
                    "onEmitStatus"  -> 0L
                    else            -> null
                }
            }

            val point = libCls.getMethod("newV2RayPoint", supCls, Boolean::class.java)
                .invoke(null, handler, false)

            pointCls.getMethod("configureFileContent", String::class.java).invoke(point, configJson)
            pointCls.getMethod("runLoop", Boolean::class.java).invoke(point, false)

            v2RayPoint = point
            Log.i(TAG, "V2Ray started (libv2ray API)")
            true
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "libv2ray.LibV2ray not found, trying libXray fallback")
            false
        } catch (e: Exception) {
            Log.w(TAG, "libv2ray start error: ${e.message}")
            false
        }
    }

    // libXray.aar (AndroidLibXrayLite) fallback
    private fun startXrayLegacy(configJson: String, vpnService: VpnService): Boolean {
        return try {
            val cls = Class.forName("libXray.LibXray")
            runCatching {
                cls.getMethod("initXray", String::class.java,
                    android.content.Context::class.java, Long::class.java)
                    .invoke(null, vpnService.filesDir.absolutePath, vpnService, 50L * 1024 * 1024)
            }
            val result = cls.getMethod("startXray", String::class.java).invoke(null, configJson)
            val ok = result as? Boolean ?: (result?.toString() == "true")
            if (ok) Log.i(TAG, "Xray started (libXray API)")
            else Log.w(TAG, "startXray returned false/null")
            ok
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Neither libv2ray nor libXray found in app/libs/")
            false
        } catch (e: Exception) {
            Log.w(TAG, "libXray start error: ${e.message}")
            false
        }
    }

    fun stop() {
        val point = v2RayPoint
        if (point != null) {
            runCatching {
                Class.forName("libv2ray.V2RayPoint").getMethod("stopLoop").invoke(point)
            }
            v2RayPoint = null
            return
        }
        runCatching {
            Class.forName("libXray.LibXray").getMethod("stopXray").invoke(null)
        }
    }
}
