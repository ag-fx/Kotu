package kr.entree.kotu.ui.room

import io.ktor.http.URLBuilder
import io.ktor.http.cio.websocket.Frame
import javafx.collections.ObservableList
import javafx.scene.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.entree.kotu.codec.Codec
import kr.entree.kotu.network.Connection
import kr.entree.kotu.packet.Packet
import kr.entree.kotu.startWebSocket
import kr.entree.kotu.ui.component.PlayerCard
import kr.entree.kotu.ui.data.GamePlayer
import kr.entree.kotu.ui.data.GameRoom
import kr.entree.kotu.ui.data.User
import tornadofx.Controller
import tornadofx.bind
import tornadofx.error

/**
 * Created by JunHyung Lim on 2020-03-27
 */
class RoomController(
    val gameRoom: GameRoom,
    val socketUrl: URLBuilder,
    val codec: Codec
) : Controller() {
    val view by inject<RoomView>()
    var connection: Connection<Frame>? = null

    fun bindPlayers(children: ObservableList<Node>) {
        children.bind(gameRoom.room.players) {
            PlayerCard(it ?: GamePlayer.EMPTY).root
        }
    }

    fun onPacket(packet: Any) {
        when (packet) {
            is Packet.In.Chat -> {
                val user = gameRoom.gameManager.users[packet.sender] ?: User.EMPTY
                view.chat("${user.name}: ${packet.message}")
            }
            is Packet.In.Error -> {
                error("에러", "에러코드: ${packet.code}", owner = view.currentWindow)
                view.close()
            }
            is Packet.In.Unknown -> System.err.println("Unknown packet: ${packet.type} ${packet.element}")
            else -> System.err.println("Uncaught packet: ${packet.javaClass.name}")
        }
    }

    fun chat(message: String) {
        sendPacket(Packet.Out.Chat(message))
    }

    fun sendPacket(packet: Any) {
        GlobalScope.launch(Dispatchers.IO) {
            val encoded = codec.encode(packet)
            connection?.send(encoded)
        }
    }

    fun start() {
        shutdown()
        connection = startWebSocket(socketUrl.buildString()) {
            val decoded = codec.decode(it)
            withContext(Dispatchers.Main) {
                onPacket(decoded)
            }
        }
    }

    fun shutdown() {
        connection?.cancel()
    }
}