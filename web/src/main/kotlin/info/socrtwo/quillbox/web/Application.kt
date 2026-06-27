package info.socrtwo.quillbox.web

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }

    val mail = MailService()

    routing {
        // Serves the web UI from src/main/resources/web/.
        staticResources("/", "web")

        post("/api/inbox") {
            val req = call.receive<InboxRequest>()
            mail.runCatchingInbox(req).fold(
                onSuccess = { call.respond(it) },
                onFailure = { call.respond(HttpStatusCode.BadGateway, ApiError(it.message ?: "Fetch failed")) }
            )
        }

        post("/api/send") {
            val req = call.receive<SendRequest>()
            mail.send(req).fold(
                onSuccess = { call.respond(ApiStatus("sent")) },
                onFailure = { call.respond(HttpStatusCode.BadGateway, ApiError(it.message ?: "Send failed")) }
            )
        }
    }
}

private suspend fun MailService.runCatchingInbox(req: InboxRequest): Result<List<MessageDto>> =
    runCatching { fetchInbox(req.account, req.limit) }
