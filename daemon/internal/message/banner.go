package message

import (
	"fmt"
	"time"

	"github.com/mitchellh/colorstring"
	"protoxon.com/sls/daemon/system"
)

func Start() {
	logo := fmt.Sprintf(`
[green]  ___ _    ___ 
[green] / __| |  / __|[red] Daemon [yellow]v%s
[green] \__ \ |__\__ \[dark_gray] Server Launch System
[green] |___/____|___/[light_blue] Copyright © 2022 - %d [magenta]%s

[light_blue]Website: [reset]https://slimelabs.net
[light_blue] Source: [reset]https://github.com/jessefaler/SLS
[light_blue]License: [reset]https://github.com/jessefaler/SLS/blob/main/LICENSE

[light_blue]This software is made available under the terms of the [magenta]APGL-3.0[light_blue] license.
[light_blue]The above copyright notice and this permission notice shall be included
[light_blue]in all copies or substantial portions of the Software.

`, system.Version, time.Now().Year(), system.Authors)

	fmt.Print(colorstring.Color(logo))
}
