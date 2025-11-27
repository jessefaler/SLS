package router

import (
	"fmt"
	"time"

	"github.com/gin-gonic/gin"
	"protoxon.com/sls/daemon/system"
)

func postBanner(c *gin.Context) {
	html := fmt.Sprintf(`<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>SLS</title>
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
 / __| |  / __| <span class="red">Daemon</span> <span class="yellow">v%s</span>
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
