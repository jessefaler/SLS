package router

import (
	"fmt"
	"html/template"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/protocube/system"
)

func postBanner(c *gin.Context) {
	html := fmt.Sprintf(`<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Protocube</title>
<style>
    body {
        background-color: black;
        color: lightgreen;
        font-family: monospace;
        white-space: pre;
        padding: 20px;
    }
    .red { color: red; }
    .yellow { color: yellow; }
    .dark_gray { color: darkgray; }
    .light_blue { color: deepskyblue; }
    .magenta { color: magenta; }
    .reset { color: lightgreen; }
</style>
</head>
<body>
<pre> 
  ___ _    ___  
 / __| |  / __| <span class="red">Protocube</span> <span class="yellow">v%s</span>
 \__ \ |__\__ \ <span class="dark_gray">Server Launch System</span>
 |___/____|___/ <span class="light_blue">Copyright © 2022 - %d <span class="magenta">%s</span></span>

<span class="light_blue">Website: </span><span class="reset">https://slimelabs.net</span>
<span class="light_blue"> Source: </span><span class="reset">https://github.com/jessefaler/SLS</span>
<span class="light_blue">License: </span><span class="reset">https://github.com/jessefaler/SLS/blob/main/LICENSE</span>

<span class="light_blue">This software is made available under the terms of the <span class="magenta">APGL-3.0</span> license.</span>
<span class="light_blue">The above copyright notice and this permission notice shall be included</span>
<span class="light_blue">in all copies or substantial portions of the Software.</span>
</pre>
</body>
</html>`, system.Version, time.Now().Year(), system.Authors)

	c.Data(200, "text/html; charset=utf-8", []byte(html))
}

// getNodes writes a simple HTML page listing nodes.
func (r *Router) getNodes(c *gin.Context) {
	nodes := r.NodeManager.GetNodes()

	const tmpl = `<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>Nodes</title>
  <style>
    body { background: #000; color: #0f0; font-family: monospace; padding: 20px; }
    h1 { margin-top: 0; color: #6f6; }
    .node_old { margin: 12px 0; padding: 8px 10px; border-radius: 6px; background: rgba(255,255,255,0.02); }
    .label { color: #9f9; font-weight: 600; width: 80px; display: inline-block; }
    .empty { color: #777; }
  </style>
</head>
<body>
  <h1>Nodes</h1>
  {{ if . }}
    {{ range . }}
      <div class="node_old">
        <div><span class="label">Name:</span> {{ .Name }}</div>
        <div><span class="label">ID:</span> {{ .Id }}</div>
        <div><span class="label">Location:</span> {{ .Location }}</div>
      </div>
    {{ end }}
  {{ else }}
    <div class="empty">No nodes found.</div>
  {{ end }}
</body>
</html>`

	t, err := template.New("nodes").Parse(tmpl)
	if err != nil {
		// template parse error — return plain text so the browser sees something useful
		c.String(http.StatusInternalServerError, "template parse error: %v", err)
		return
	}

	c.Header("Content-Type", "text/html; charset=utf-8")
	if err := t.Execute(c.Writer, nodes); err != nil {
		// execution error
		c.String(http.StatusInternalServerError, "template execute error: %v", err)
	}
}
