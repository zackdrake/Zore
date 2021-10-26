import dev.kord.core.*
import dev.kord.core.entity.*
import dev.kord.core.event.message.*
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay

val dotenv = dotenv()
val clientHttp = HttpClient(CIO)

suspend fun main() {
    val clientDiscord = Kord(dotenv["DISCORD_BOT_TOKEN"])

    // On message create
    clientDiscord.on<MessageCreateEvent> {
        // Check message command if start with !
        if (message.content.startsWith("!")) {
            when(message.content) {
                "!ping" -> pong(message)
                else -> {
                    return@on
                }
            }
        } else if (message.content.startsWith("$")) {
            getCryptoPrice(message, message.content.drop(1).lowercase())
        } else {
            return@on
        }
    }

    // Bot login
    clientDiscord.login()
}

suspend fun getCryptoPrice(message: Message, tokenName: String) {
    println("Call getCryptoPrice($tokenName)")

    try {
        val res: HttpResponse = clientHttp.get("https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest") {
            parameter("symbol", tokenName)
            headers {
                append("X-CMC_PRO_API_KEY", dotenv["CMC_TOKEN"])
            }
        }
        val textJson = res.readText()
        val startingIndex = textJson.indexOf(":{\"price\":") + ":{\"price\":".length
        val endIndex = textJson.indexOf(",", startingIndex)
        val price = textJson.substring(startingIndex, endIndex)
        message.channel.createMessage("$price USD")
    } catch (e: ClientRequestException) {
        println("Request fail")
        message.channel.createMessage("Request fail !")
    }
}

suspend fun pong(message: Message) {
    println("Call pong !")
    val pingPong = ReactionEmoji.Unicode("\uD83C\uDFD3")
    val response = message.channel.createMessage("Pong!")
    response.addReaction(pingPong)

    delay(5000)
    message.delete()
    response.delete()
}