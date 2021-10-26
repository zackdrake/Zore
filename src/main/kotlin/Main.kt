import dev.kord.core.*
import dev.kord.core.entity.*
import dev.kord.core.event.message.*
import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.delay

suspend fun main() {
    val dotenv = dotenv()
    val token: String = dotenv["DISCORD_BOT_TOKEN"]
    println(token)
    val client = Kord(token)
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
