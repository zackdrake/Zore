import dev.kord.common.entity.Snowflake
import dev.kord.core.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.message.*
import dev.kord.voice.AudioFrame
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
            // More complex commands
            if (message.content.startsWith("!play")) {
                playMusic(message, clientDiscord)
            }
            // For simple commands
            when(message.content) {
                "!ping" -> pong(message)
                else -> {
                    return@on
                }
            }
        } else if (message.content.startsWith("$")) {
            // Crypto commands
            getCryptoPrice(message, message.content.drop(1).lowercase())
        } else {
            return@on
        }
    }

    // Bot login
    clientDiscord.login()
}

suspend fun playMusic(message: Message, clientDiscord: Kord) {
    // TODO
    // Get first youtube video with music title
    // If it's a youtube link, just use it
    // Get the sound and play it
    println("Call playMusic()")
    val msgSplit = message.content.split(" ")
    if (msgSplit.size <= 1) {
        message.channel.createMessage("You forgot the music !")
        return
    }

    // Check if it's a youtube link
    if (msgSplit[1].startsWith("https://www.youtube.com/") || msgSplit[1].startsWith("https://youtu.be/")) {
        // Youtube link
        message.channel.createMessage(message.author?.username + " requested => " + msgSplit[1])
        val channels = message.getGuild().channelIds
        var voiceChannel: VoiceChannel? = null
        channels.forEach { channel ->
            voiceChannel = clientDiscord.getChannelOf<VoiceChannel>(id = channel)
            if (voiceChannel != null) {
                return@forEach
            }
        }

        println(voiceChannel)

        val musicBytes = getYoutubeAudio("https://www.youtube.com/watch?v=4vPb_VNSWrc")
        voiceChannel?.connect {
            audioProvider {
                AudioFrame.fromData(musicBytes)
            }
        }
        println("Bot connect to channel !")
    } else {
        // Not a youtube link
        message.channel.createMessage(message.author?.username + " requested => " + msgSplit.drop(1).joinToString(" "))

    }
}

// Get direct link with savedeo parsing
suspend fun getYoutubeAudio(ytLink: String): ByteArray {
    println("Start get download link !")
    val ytId = ytLink.substring(ytLink.indexOf("?v=") + "?v=".length)
    val quality = "128"
    var res: HttpResponse = clientHttp.get("https://www.yt-download.org/file/mp3/$ytId")
    var text = res.readText()
    var startIndex = text.indexOf("<a href=\"https://www.yt-download.org/download/$ytId/mp3/$quality/") + "<a href=\"".length
    var endIndex = text.indexOf("\" class", startIndex)
    var downloadLink = text.substring(startIndex, endIndex)
    println(downloadLink)
    res = clientHttp.get(downloadLink)
    println("Finish download music!")
    return res.readBytes()
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
        message.channel.createMessage(tokenName.uppercase() + " -> $price USD")
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