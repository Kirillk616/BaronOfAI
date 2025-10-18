package wadtool.web

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.html.*
import kotlinx.html.*
import wadtool.storage.FileLevelStorage
import wadtool.storage.LevelStorage
import java.io.File

fun main() {
    val storage: LevelStorage = FileLevelStorage()
    embeddedServer(Netty, port = 8080) {
        module(storage)
    }.start(wait = true)
}

fun Application.module(storage: LevelStorage) {
    routing {
        get("/") {
            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title { +"Doom Levels" }
                    meta { charset = "utf-8" }
                    script(src = "https://unpkg.com/htmx.org@1.9.12") {}
                    style {
                        unsafe {
                            +"""
                            body { margin:0; font-family: Arial, sans-serif; }
                            .layout { display:flex; height: 100vh; }
                            .left { width: 320px; border-right: 1px solid #ddd; overflow:auto; }
                            .right { flex:1; padding: 12px; }
                            .item { padding: 8px 10px; border-bottom: 1px solid #eee; cursor:pointer; }
                            .item:hover { background:#f5f5f5; }
                            .prompt { color:#555; font-size: 12px; margin-top: 4px; }
                            .toolbar { padding: 8px; border-bottom: 1px solid #eee; }
                            """
                        }
                    }
                }
                body {
                    div("layout") {
                        div("left") {
                            div(classes = "toolbar") {
                                h2 { +"Recent Levels" }
                            }
                            div {
                                id = "level-list"
                                attributes["hx-get"] = "/levels"
                                attributes["hx-trigger"] = "load"
                                attributes["hx-target"] = "#level-list"
                                attributes["hx-swap"] = "outerHTML"
                                +"Loading levels..."
                            }
                        }
                        div("right") {
                            h2 { +"Preview" }
                            div {
                                id = "preview"
                                p { +"Select a level from the left to preview its SVG and download WAD." }
                            }
                        }
                    }
                }
            }
        }

        get("/levels") {
            val items = storage.list()
            call.respondHtml(HttpStatusCode.OK) {
                body {
                    div {
                        id = "level-list"
                        items.forEach { meta ->
                            div("item") {
                                attributes["hx-get"] = "/levels/${meta.id}/view"
                                attributes["hx-target"] = "#preview"
                                attributes["hx-swap"] = "innerHTML"
                                strong { +meta.id }
                                div("prompt") { +meta.prompt.take(160) }
                            }
                        }
                        if (items.isEmpty()) {
                            div { +"No levels yet. Generate one to get started." }
                        }
                    }
                }
            }
        }

        get("/levels/{id}/view") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val prompt = storage.getPrompt(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondHtml(HttpStatusCode.OK) {
                body {
                    div {
                        h3 { +"Level $id" }
                        p { +prompt }
                        div {
                            style = "border:1px solid #ddd; height:75vh; overflow:auto;"
                            // Use object tag to render SVG
                            unsafe {
                                +"""
                                <object data="/levels/$id/svg" type="image/svg+xml" style="width:100%; height:100%"></object>
                                """
                            }
                        }
                        p {
                            a("/levels/$id/wad") { +"Download WAD" }
                        }
                    }
                }
            }
        }

        get("/levels/{id}/svg") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val svg = storage.getSvg(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondText(svg, ContentType.Image.SVG)
        }

        get("/levels/{id}/wad") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val wad = storage.getWad(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.response.header(HttpHeaders.ContentDisposition, ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "${id}.wad").toString())
            call.respondFile(wad)
        }
    }
}