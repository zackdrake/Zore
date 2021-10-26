import dev.kord.core.*
import dev.kord.core.entity.*
import dev.kord.core.event.message.*
import kotlinx.coroutines.delay

suspend fun main() {
    val client = Kord("OTAyNDc5Mjk4MTk4MzkyODMy.YXfBVw.VCJ8uAQBQvJDu1LACEcmVwVdQTA")
    val pingPong = ReactionEmoji.Unicode("\uD83C\uDFD3")

    // On message create
    client.on<MessageCreateEvent> {
        if (message.content != "!ping") return@on

        println("Pong!")
        val response = message.channel.createMessage("Pong!")
        response.addReaction(pingPong)

        delay(5000)
        message.delete()
        response.delete()
    }

    // Bot login
    client.login()
}
