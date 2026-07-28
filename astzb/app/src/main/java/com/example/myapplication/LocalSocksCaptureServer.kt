package com.example.myapplication

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object LocalSocksCaptureServer {
    private const val DEFAULT_PORT = 10808
    private const val GAME_PORT = 8001

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    @JvmStatic
    fun start(port: Int = DEFAULT_PORT): Int {
        if (running.get()) return port
        val socket = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        running.set(true)
        PacketLogStore.add("本机 SOCKS5 捕获器已启动：127.0.0.1:$port")
        thread(name = "local-socks-capture", isDaemon = true) {
            acceptLoop(socket)
        }
        return port
    }

    @JvmStatic
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        PacketLogStore.add("本机 SOCKS5 捕获器已停止")
    }

    @JvmStatic
    fun isRunning(): Boolean = running.get()

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            thread(name = "socks-client", isDaemon = true) {
                handleClient(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { c ->
            c.soTimeout = 15_000
            val input = BufferedInputStream(c.getInputStream())
            val output = BufferedOutputStream(c.getOutputStream())

            val version = input.read()
            if (version != 0x05) return
            val methods = input.read()
            if (methods <= 0) return
            repeat(methods) { input.read() }
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val reqVersion = input.read()
            val cmd = input.read()
            input.read() // rsv
            val atyp = input.read()
            if (reqVersion != 0x05 || cmd != 0x01) {
                writeSocksReply(output, 0x07)
                return
            }

            val host = readAddress(input, atyp)
            val port = readPort(input)
            val remote = runCatching { Socket(host, port) }.getOrElse {
                PacketLogStore.add("SOCKS 连接失败：$host:$port ${it.message}")
                writeSocksReply(output, 0x05)
                return
            }

            writeSocksReply(output, 0x00)
            PacketLogStore.add("SOCKS 转发：$host:$port")

            remote.use { r ->
                c.soTimeout = 0
                r.soTimeout = 0
                val upParser = if (port == GAME_PORT) StzbStreamParser("client->$host:$port") else null
                val downParser = if (port == GAME_PORT) StzbStreamParser("$host:$port->client") else null
                val up = pipe(c, r, upParser)
                val down = pipe(r, c, downParser)
                up.join()
                down.join()
            }
        }
    }

    private fun pipe(from: Socket, to: Socket, parser: StzbStreamParser?): Thread {
        return thread(name = "socks-pipe", isDaemon = true) {
            val buf = ByteArray(16 * 1024)
            runCatching {
                val input = from.getInputStream()
                val output = to.getOutputStream()
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    parser?.append(buf, n)
                    output.write(buf, 0, n)
                    output.flush()
                }
            }
            runCatching { to.shutdownOutput() }
        }
    }

    private fun readAddress(input: BufferedInputStream, atyp: Int): String {
        return when (atyp) {
            0x01 -> ByteArray(4).also { input.readFully(it) }.joinToString(".") { (it.toInt() and 0xff).toString() }
            0x03 -> {
                val len = input.read()
                val data = ByteArray(len)
                input.readFully(data)
                String(data, Charsets.UTF_8)
            }
            0x04 -> {
                val data = ByteArray(16)
                input.readFully(data)
                InetAddress.getByAddress(data).hostAddress.orEmpty()
            }
            else -> error("unsupported atyp=$atyp")
        }
    }

    private fun readPort(input: BufferedInputStream): Int {
        val hi = input.read()
        val lo = input.read()
        return ((hi and 0xff) shl 8) or (lo and 0xff)
    }

    private fun writeSocksReply(output: BufferedOutputStream, code: Int) {
        output.write(byteArrayOf(0x05, code.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun BufferedInputStream.readFully(buffer: ByteArray) {
        var off = 0
        while (off < buffer.size) {
            val n = read(buffer, off, buffer.size - off)
            if (n < 0) error("unexpected eof")
            off += n
        }
    }
}
