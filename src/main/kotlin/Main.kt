import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.event.message.*
import dev.kord.voice.AudioFrame
import dev.kord.voice.VoiceConnection
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val dotenv = dotenv()
val clientHttp = HttpClient(CIO)
val lavaplayerManager = DefaultAudioPlayerManager()
// to use YouTube, we tell LavaPlayer to use remote sources, like YouTube.
@OptIn(KordVoice::class)
val connections: MutableMap<Snowflake, VoiceConnection> = mutableMapOf()
var pingPongLevel: Int = 0;
var pingPongLevelUpMessage: String = "";

suspend fun main() {
    val clientDiscord = Kord(dotenv["DISCORD_BOT_TOKEN"])

    AudioSourceManagers.registerRemoteSources(lavaplayerManager)

    // On message create
    clientDiscord.on<MessageCreateEvent> {
        // Check message command if start with !
        if (message.content.startsWith("!")) {
            // More complex commands
            if (message.content.startsWith("!play")) {
                playMusic(member!!, message, clientDiscord)
            }
            // For simple commands
            when(message.content) {
                "!ping" -> pong(message)
                "!stop" -> stopMusic(guildId)
                "!help" -> help(message)
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

@OptIn(KordVoice::class)
suspend fun playMusic(member: Member?, message: Message, clientDiscord: Kord) {
    println("Call playMusic()")

    // Check if link or other is passed
    val msgSplit = message.content.split(" ")
    if (msgSplit.size <= 1) {
        message.channel.createMessage("You forgot the music !")
        return
    }

    // Check if user is in a channel
    val channel = member?.getVoiceState()?.getChannelOrNull()
    if (channel == null) {
        message.channel.createMessage("You're not in a channel !")
        return
    }

    // Check if already in a channel
    if (connections.contains(message.getGuild().id)) {
        // Already playing, shutdown to restart a voice connection
        connections.remove(message.getGuild().id)!!.leave()
    }

    val player = lavaplayerManager.createPlayer()
    var track: AudioTrack? = null

    // Check if it's a YouTube link
    if (msgSplit[1].startsWith("https://www.youtube.com/") || msgSplit[1].startsWith("https://youtu.be/")) {
        // YouTube link
        val ytLink = msgSplit[1]
        track = lavaplayerManager.playTrack(ytLink, player)
        message.channel.createMessage(message.author?.username + " requested => " + msgSplit[1])
    } else {
        // Not a YouTube link
        // Lavaplayer can search video for us
        val query = "ytsearch: " + msgSplit.drop(1).joinToString(" ")
        track = lavaplayerManager.playTrack(query, player)
        message.channel.createMessage(message.author?.username + " requested => " + track.info?.uri)
    }

    println(track?.info?.uri)

    if (track == null) {
        // Fail to find track
        message.channel.createMessage("Fail to find music...")
    }

    // Need OPUS encoded byte array
    val voiceConnection = (channel as VoiceChannel).connect {
        audioProvider { AudioFrame.fromData(player.provide()?.data) }
    }

    connections[message.getGuild().id] = voiceConnection
}

@OptIn(KordVoice::class)
suspend fun stopMusic(guildId: Snowflake?) {
    println("Call stopMusic()")
    if (guildId != null) {
        if (connections.contains(guildId)) {
            // Already playing, shutdown to restart a voice connection
            connections.remove(guildId)!!.leave()
        }
    }
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
    pingPongLevel++

    when (pingPongLevel) {
        3 -> pingPongLevelUpMessage = "You're starting to get good"
        5 -> pingPongLevelUpMessage = "You're now a ping pong master"
        10 -> {
            pingPongLevelUpMessage = "The ball hit you in the head and you forgot all your skills"
            pingPongLevel = 0
        }
        else -> {
            pingPongLevelUpMessage = "you're getting better!"
        }
    }

    message.channel.createMessage("$pingPongLevelUpMessage")

    response.addReaction(pingPong)

}


suspend fun help(message: Message) = message.channel.createMessage("!pong -> ping-pong\n" +
        "$<crypto> -> Price of a crypto, example : ${"$"}btc\n" +
        "!play <youtube link OR search term>, example : !play vald\n" +
        "!stop -> Stop music")

// lavaplayer isn't super kotlin-friendly, so we'll make it nicer to work with
suspend fun DefaultAudioPlayerManager.playTrack(query: String, player: AudioPlayer): AudioTrack {
    val track = suspendCoroutine<AudioTrack> {
        this.loadItem(query, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                it.resume(track)
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                it.resume(playlist.tracks.first())
            }

            override fun noMatches() {
                TODO()
            }

            override fun loadFailed(exception: FriendlyException?) {
                TODO()
            }
        })
    }

    player.playTrack(track)

    return track
}

// Probably useless after voice rework
fun String.runCommand(workingDir: File) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}
