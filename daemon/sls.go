package main

import (
	"github.com/fatih/color"
	"protoxon.com/sls/daemon/cmd"
	"protoxon.com/sls/daemon/system"
)

// Copyright (C) 2025 Jesse Faler <jfaler8@gmail.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

func main() {

	// Execute the main binary code.
	cmd.Execute()

}

func init() {

	// Bypasses the check for non-tty output streams. While in development
	// noinspection GoBoolExpressions
	if system.Version == "develop" {
		color.NoColor = false
	}

}
