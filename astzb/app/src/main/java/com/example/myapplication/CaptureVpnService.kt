package com.example.myapplication

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.net.InetAddress
import kotlin.math.min
import java.util.concurrent.atomic.AtomicBoolean

class CaptureVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    private val running = AtomicBoolean(false)
    private var activeBridge: Tun2SocksBridge? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val socksHost = intent?.getStringExtra(EXTRA_SOCKS_HOST).orEmpty()
        val socksPort = intent?.getIntExtra(EXTRA_SOCKS_PORT, 1080) ?: 1080
        when (intent?.action) {
            ACTION_START, null -> {
                if (!running.get()) {
                    startCapture(targetPackage)
                } else {
                    PacketLogStore.add("抓取服务已在运行")
                }
            }
            ACTION_START_BRIDGE -> {
                if (!running.get()) {
                    startOpenSourceBridge(
                        TunnelConfig(
                            targetPackage = targetPackage,
                            socksHost = socksHost,
                            socksPort = socksPort,
                        )
                    )
                } else {
                    PacketLogStore.add("服务已在运行，请先停止当前模式")
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onRevoke() {
        PacketLogStore.add("VPN 权限被系统撤销")
        stopSelf()
        super.onRevoke()
    }

    private fun startCapture(targetPackage: String) {
        try {
            vpnInterface = buildVpn(targetPackage)
            if (vpnInterface == null) {
                PacketLogStore.add("建立 VPN 失败：builder.establish() 返回 null")
                return
            }

            running.set(true)
            isRunning = true
            PacketLogStore.add("VPN 已建立，开始读取 TUN 原始 IP 包")
            startReader(vpnInterface!!)
        } catch (e: Exception) {
            PacketLogStore.add("启动抓取失败：${e.message}")
            stopCapture()
        }
    }

    private fun startOpenSourceBridge(config: TunnelConfig) {
        try {
            vpnInterface = buildVpn(config.targetPackage)
            if (vpnInterface == null) {
                PacketLogStore.add("建立 VPN 失败：builder.establish() 返回 null")
                return
            }

            running.set(true)
            isRunning = true
            val bridge = HevSocks5TunnelBridge()
            val ok = bridge.start(this, vpnInterface!!, config)
            if (ok) {
                activeBridge = bridge
                PacketLogStore.add("开源桥接模式已启动")
            } else {
                PacketLogStore.add("开源桥接模式未完全启动，回退停止")
                stopCapture()
            }
        } catch (e: Exception) {
            PacketLogStore.add("启动开源桥接失败：${e.message}")
            stopCapture()
        }
    }

    private fun buildVpn(targetPackage: String): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("astzb-capture")
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        if (targetPackage.isNotBlank()) {
            try {
                builder.addAllowedApplication(targetPackage)
                PacketLogStore.add("已限制仅抓取包名：$targetPackage")
            } catch (e: Exception) {
                PacketLogStore.add("添加目标包名失败，改为默认流量：${e.message}")
            }
        }

        return builder.establish()
    }

    private fun startReader(fd: ParcelFileDescriptor) {
        readerThread = Thread({
            FileInputStream(fd.fileDescriptor).use { input ->
                val buffer = ByteArray(32767)
                while (running.get()) {
                    val length = input.read(buffer)
                    if (length > 0) {
                        val result = parsePacket(buffer, length)
                        result.record?.let { PacketCaptureStore.add(it) }
                        PacketLogStore.add(result.summary)
                    }
                }
            }
        }, "tun-reader").apply {
            isDaemon = true
            start()
        }
    }

    private fun stopCapture() {
        running.set(false)
        isRunning = false
        activeBridge?.stop()
        activeBridge = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        readerThread = null
        PacketLogStore.add("抓取服务已停止")
    }

    private fun parsePacket(buffer: ByteArray, length: Int): PacketParseResult {
        if (length < 20) {
            return PacketParseResult("收到过短数据包 len=$length")
        }

        val version = buffer[0].toInt().ushr(4)
        if (version != 4) {
            return PacketParseResult("收到非 IPv4 数据包 version=$version len=$length")
        }

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (length < ihl || ihl < 20) {
            return PacketParseResult("IPv4 头长度异常 ihl=$ihl len=$length")
        }

        val protocol = buffer[9].toInt() and 0xFF
        val srcIp = ipv4ToString(buffer, 12)
        val dstIp = ipv4ToString(buffer, 16)

        val srcPort = if (length >= ihl + 4) {
            ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        } else {
            -1
        }
        val dstPort = if (length >= ihl + 4) {
            ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
        } else {
            -1
        }

        val protoName = when (protocol) {
            6 -> "TCP"
            17 -> "UDP"
            else -> "P$protocol"
        }

        val transportHeaderLength = when (protocol) {
            6 -> {
                if (length >= ihl + 13) {
                    ((buffer[ihl + 12].toInt() ushr 4) and 0x0F) * 4
                } else {
                    0
                }
            }
            17 -> 8
            else -> 0
        }
        val payloadOffset = (ihl + transportHeaderLength).coerceAtMost(length)
        val payloadLength = (length - payloadOffset).coerceAtLeast(0)
        val hexPreview = toHex(buffer, length, min(length, 24))
        val payloadHexPreview = if (payloadLength > 0) {
            toHex(buffer.copyOfRange(payloadOffset, length), payloadLength, min(payloadLength, 32))
        } else {
            ""
        }

        val summary = "[$protoName] $srcIp:$srcPort -> $dstIp:$dstPort len=$length hex=$hexPreview"
        val record = PacketRecord(
            timestampMillis = System.currentTimeMillis(),
            protocolName = protoName,
            srcIp = srcIp,
            srcPort = srcPort,
            dstIp = dstIp,
            dstPort = dstPort,
            length = length,
            ipHeaderLength = ihl,
            transportHeaderLength = transportHeaderLength,
            payloadLength = payloadLength,
            summary = summary,
            hexDump = toHex(buffer, length, length),
            payloadHexPreview = payloadHexPreview,
        )

        return PacketParseResult(summary, record)
    }

    private fun ipv4ToString(buffer: ByteArray, offset: Int): String {
        return try {
            InetAddress.getByAddress(buffer.copyOfRange(offset, offset + 4)).hostAddress.orEmpty()
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }

    companion object {
        const val ACTION_START = "com.example.myapplication.action.START_CAPTURE"
        const val ACTION_START_BRIDGE = "com.example.myapplication.action.START_BRIDGE"
        const val ACTION_STOP = "com.example.myapplication.action.STOP_CAPTURE"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_SOCKS_HOST = "socks_host"
        const val EXTRA_SOCKS_PORT = "socks_port"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

private data class PacketParseResult(
    val summary: String,
    val record: PacketRecord? = null,
)

private fun toHex(buffer: ByteArray, validLength: Int, maxBytes: Int): String {
    val end = min(validLength, maxBytes)
    val sb = StringBuilder(end * 3)
    for (i in 0 until end) {
        sb.append(String.format("%02X", buffer[i].toInt() and 0xFF))
        if (i != end - 1) sb.append(' ')
    }
    if (validLength > end) sb.append(" ...")
    return sb.toString()
}
