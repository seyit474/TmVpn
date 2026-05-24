package com.seyit474.tmvpn.service

import android.content.Context
import android.util.Log

object XrayCoreProxy {
    private const val TAG = "XrayCoreProxy"

    // Holds the running V2RayPoint instance (libv2ray API)
    private var v2RayPoint: Any? = null

    fun start(configJson: String, ctx: Context): Boolean {
        stop()
        return try {
            // libv2ray.aar uses class "libv2ray.LibV2ray"
            val libCls  = Class.forName("libv2ray.LibV2ray")
            val pointCls = Class.forName("libv2ray.V2RayPoint")
            val supCls   = Class.forName("libv2ray.V2RayVPNServiceSupportsSet")

            // initV2Env(env_dir, dat_dir)  — both set to files dir
            runCatching {
                libCls.getMethod("initV2Env", String::class.java, String::class.java)
                    .invoke(null, ctx.filesDir.absolutePath, ctx.filesDir.absolutePath)
            }

            // newV2RayPoint(handler, isVPN=false)
            // handler implements V2RayVPNServiceSupportsSet — ctx is XrayVpnService (VpnService)
            val point = libCls.getMethod("newV2RayPoint", supCls, Boolean::class.java)
                .invoke(null, ctx, false)

            // point.configureFileContent(json)
            pointCls.getMethod("configureFileContent", String::class.java).invoke(point, configJson)

            // point.runLoop(false)
            pointCls.getMethod("runLoop", Boolean::class.java).invoke(point, false)

            v2RayPoint = point
            Log.i(TAG, "V2Ray/Xray started OK")
            true
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "libv2ray.LibV2ray not found, trying libXray fallback")
            startLegacy(configJson, ctx)
        } catch (e: Exception) {
            Log.e(TAG, "start error: ${e.message}", e)
            false
        }
    }

    // Fallback for libv2ray.aar builds whose Go package is named libXray
    private fun startLegacy(configJson: String, ctx: Context): Boolean {
        return try {
            val cls = Class.forName("libXray.LibXray")
            // Try initXray if present
            runCatching {
                cls.getMethod("initXray", String::class.java, Context::class.java, Long::class.java)
                    .invoke(null, ctx.filesDir.absolutePath, ctx, 50L * 1024 * 1024)
            }
            val result = cls.getMethod("startXray", String::class.java).invoke(null, configJson)
            (result as? Boolean ?: result?.toString() == "true").also {
                if (it) Log.i(TAG, "Xray (libXray) started OK")
                else Log.w(TAG, "startXray returned false/null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "legacy start error: ${e.message}", e)
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
            Log.i(TAG, "V2Ray/Xray stopped")
            return
        }
        // Fallback stop for libXray
        runCatching {
            Class.forName("libXray.LibXray").getMethod("stopXray").invoke(null)
        }
    }
}
