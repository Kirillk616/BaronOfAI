package wadtool.web

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
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
                            +" | "
                            a("/levels/$id/play") { +"Play in Browser" }
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

        get("/levels/{id}/pwad") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val wad = storage.getWad(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondFile(wad)
        }

        get("/levels/{id}/play") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val prompt = storage.getPrompt(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            val wad = storage.getWad(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title { +"Play $id" }
                    meta { charset = "utf-8" }
                    meta {
                        name = "viewport"
                        content = "width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"
                    }
                    style {
                        unsafe {
                            +"""
                            html, body { margin:0; width:100%; height:100%; background:#050505; color:#f2f2f2; font-family: Arial, sans-serif; overflow:hidden; }
                            .shell { display:grid; grid-template-rows:auto 1fr; width:100%; height:100%; }
                            .bar { display:flex; gap:12px; align-items:center; justify-content:space-between; padding:10px 12px; background:#111; border-bottom:1px solid #333; }
                            .title { min-width:0; }
                            .title strong { display:block; font-size:15px; }
                            .title span { display:block; color:#aaa; font-size:12px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:70vw; }
                            .actions { display:flex; gap:8px; align-items:center; }
                            .actions a, .actions button { color:#f2f2f2; background:#242424; border:1px solid #444; padding:7px 10px; text-decoration:none; cursor:pointer; font-size:13px; }
                            .stage { position:relative; min-height:0; display:grid; place-items:center; background:#000; }
                            canvas { width:100%; height:100%; max-width:100vw; max-height:calc(100vh - 58px); object-fit:contain; image-rendering:pixelated; outline:none; }
                            #status { position:absolute; left:12px; bottom:12px; right:12px; color:#ddd; background:rgba(0,0,0,.75); border:1px solid #333; padding:10px; font-size:13px; line-height:1.4; }
                            #status.complete { display:none; }
                            code { color:#98d27b; }
                            """
                        }
                    }
                }
                body {
                    div("shell") {
                        div("bar") {
                            div("title") {
                                strong { +"Playing $id" }
                                span { +prompt.take(220) }
                            }
                            div("actions") {
                                a("/") { +"Back" }
                                a("/levels/$id/wad") { +"Download WAD" }
                                button {
                                    attributes["id"] = "fullscreen"
                                    +"Fullscreen"
                                }
                            }
                        }
                        div("stage") {
                            canvas {
                                attributes["id"] = "doom"
                                attributes["tabindex"] = "0"
                            }
                            div {
                                attributes["id"] = "status"
                                +"Preparing webDOOM runtime..."
                            }
                        }
                    }
                    script {
                        unsafe {
                            +webDoomBootScript(id, wad.length())
                        }
                    }
                }
            }
        }

        staticFiles("/webdoom", File("WADTool\\data\\webdoom"))
    }
}

private fun webDoomBootScript(id: String, wadSize: Long): String {
    val pwadUrl = "/levels/$id/pwad"
    return """
        (function () {
          'use strict';

          var status = document.getElementById('status');
          var canvas = document.getElementById('doom');
          var fullscreen = document.getElementById('fullscreen');
          var pwadUrl = '$pwadUrl';
          var pwadName = 'baron-generated.wad';

          function setStatus(message) {
            status.textContent = message;
            if (!message) status.classList.add('complete');
          }

          function runtimePath(path) {
            var file = path.split('/').pop();
            return '/webdoom/' + file;
          }

          fullscreen.addEventListener('click', function () {
            if (window.Module && Module.requestFullscreen) {
              Module.requestFullscreen(true, false);
            } else if (canvas.requestFullscreen) {
              canvas.requestFullscreen();
            }
          });

          canvas.addEventListener('webglcontextlost', function (event) {
            event.preventDefault();
            setStatus('WebGL context was lost. Reload the page to restart Doom.');
          }, false);

          canvas.addEventListener('contextmenu', function (event) {
            event.preventDefault();
          });

          window.Module = {
            canvas: canvas,
            locateFile: runtimePath,
            arguments: ['-iwad', '/doom2.wad', '-file', '/' + pwadName, '-warp', '1', '-skill', '2'],
            setStatus: function (message) {
              if (!message) {
                setStatus('');
                canvas.focus();
              } else {
                setStatus(message);
              }
            },
            monitorRunDependencies: function () {},
            preRun: [function () {
              var dependency = 'generated-pwad';
              Module.addRunDependency(dependency);
              setStatus('Loading generated PWAD ($wadSize bytes)...');
              fetch(pwadUrl)
                .then(function (response) {
                  if (!response.ok) throw new Error('HTTP ' + response.status + ' while loading generated WAD.');
                  return response.arrayBuffer();
                })
                .then(function (buffer) {
                  Module.FS_createDataFile('/', pwadName, new Uint8Array(buffer), true, true, true);
                  Module.removeRunDependency(dependency);
                })
                .catch(function (error) {
                  console.error(error);
                  setStatus('Could not load generated PWAD: ' + error.message);
                  Module.removeRunDependency(dependency);
                });
            }]
          };

          var script = document.createElement('script');
          script.src = '/webdoom/doom2.js';
          script.async = true;
          script.onerror = function () {
            setStatus('webDOOM runtime not installed. Put doom2.js, doom2.wasm, and doom2.data in WADTool/data/webdoom.');
          };
          document.body.appendChild(script);
        })();
    """.trimIndent()
}
