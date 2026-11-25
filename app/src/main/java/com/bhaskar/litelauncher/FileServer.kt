package com.bhaskar.litelauncher

import android.content.Context
import android.os.Environment
import fi.iki.elonen.NanoHTTPD
import java.io.File

object FileServer {
    private var server: Srv? = null
    private lateinit var ctx: Context
    var port = 0
        private set

    /** Tries ports 8765-8770 so it never clashes with other apps already using 8080. */
    fun start(c: Context) {
        ctx = c.applicationContext
        if (server != null) return
        for (p in 8765..8770) {
            val s = Srv(p)
            try { s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false); server = s; port = p; return } catch (e: Exception) { }
        }
    }

    /** USB/SD first (FromPhone folder at its root, else app folder on it), otherwise internal Downloads/FromPhone. */
    fun targetDir(): File {
        val dirs = ctx.getExternalFilesDirs(null).filterNotNull()
        for (d in dirs.drop(1)) {
            val path = d.absolutePath
            val i = path.indexOf("/Android/data")
            if (i > 0) {
                val r = File(path.substring(0, i), "FromPhone")
                if ((r.exists() || r.mkdirs()) && r.canWrite()) return r
            }
            d.mkdirs()
            if (d.canWrite()) return d
        }
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FromPhone")
            .apply { mkdirs() }
    }

    private const val PAGE = """<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
<body style="font-family:sans-serif;padding:20px"><h2>Send files to TV</h2>
<input type=file id=f multiple><br><br><button onclick=go()>Send</button><pre id=o></pre>
<script>
async function go(){
 const fs=document.getElementById('f').files, o=document.getElementById('o'); o.textContent='';
 for(const f of fs){
  o.textContent+='Sending '+f.name+' ... ';
  const d=new FormData(); d.append('file',f,f.name);
  try{ const r=await fetch('/',{method:'POST',body:d}); const t=await r.text(); o.textContent+=(r.ok?t:'failed')+'\n'; }
  catch(e){ o.textContent+='failed\n'; }
 }
}
</script></body>"""

    class Srv(p: Int) : NanoHTTPD(p) {
        override fun serve(s: IHTTPSession): Response {
            if (s.method == Method.POST) {
                val files = HashMap<String, String>()
                return try {
                    s.parseBody(files)
                    val dir = targetDir()
                    for ((field, tmp) in files) {
                        val raw = s.parameters[field]?.firstOrNull() ?: "file_${System.currentTimeMillis()}"
                        File(tmp).copyTo(File(dir, File(raw).name), true)
                    }
                    newFixedLengthResponse("saved to " + dir.absolutePath)
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "error")
                }
            }
            return newFixedLengthResponse(Response.Status.OK, "text/html", PAGE)
        }
    }
}
