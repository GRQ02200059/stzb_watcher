package com.example.myapplication

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File

interface Tun2SocksBridge {
    fun start(
        context: Context,
        vpnFd: ParcelFileDescriptor,
        config: TunnelConfig,
    ): Boolean

    fun stop()
}

class HevSocks5TunnelBridge : Tun2SocksBridge {

    private var started = false

    override fun start(
        context: Context,
        vpnFd: ParcelFileDescriptor,
        config: TunnelConfig,
    ): Boolean {
        if (config.socksHost.isBlank() || config.socksPort <= 0) {
            PacketLogStore.add("开源桥接启动失败：SOCKS5 地址或端口无效")
            return false
        }

        val confFile = writeHevConfig(context, config)
        PacketLogStore.add(
            "已切换到开源桥接模式：hev-socks5-tunnel，配置文件=${confFile.absolutePath}"
        )
        PacketLogStore.add(
            "上游 SOCKS5 = ${config.socksHost}:${config.socksPort}，fd=${vpnFd.fd}"
        )

        return try {
            NativeHevBridge.start(vpnFd.fd, confFile.absolutePath)
            started = true
            PacketLogStore.add("已调用 native 开源桥接入口")
            true
        } catch (e: UnsatisfiedLinkError) {
            PacketLogStore.add(
                "尚未打包 native 库，当前先完成了 Java 侧开源桥接接入点：${e.message}"
            )
            false
        } catch (e: Throwable) {
            PacketLogStore.add("开源桥接启动异常：${e.message}")
            false
        }
    }

    override fun stop() {
        if (!started) return
        try {
            NativeHevBridge.stop()
            PacketLogStore.add("已停止 native 开源桥接")
        } catch (e: Throwable) {
            PacketLogStore.add("停止开源桥接异常：${e.message}")
        } finally {
            started = false
        }
    }

    private fun writeHevConfig(context: Context, config: TunnelConfig): File {
        val dir = File(context.filesDir, "hev")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "main.yml")
        file.writeText(
            """
tunnel:
  name: tun0
  mtu: 1500
  ipv4: 10.0.0.2

socks5:
  address: ${config.socksHost}
  port: ${config.socksPort}
  udp: 'udp'

misc:
  log-file: stderr
  log-level: info
            """.trimIndent()
        )
        return file
    }
}

private object NativeHevBridge {
    fun start(vpnFd: Int, configPath: String) {
        // 这里故意只定义接入点，不在当前仓库里硬塞大块 native 代码。
        // 后续接入 sockstun / hev-socks5-tunnel 时，把 JNI 和 .so 对上即可。
        throw UnsatisfiedLinkError(
            "missing native hev-socks5-tunnel bridge, fd=$vpnFd, config=$configPath"
        )
    }

    fun stop() {
        // no-op placeholder
    }
}
