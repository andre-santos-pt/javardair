rootProject.name = "javardair"

include("jaid")

// 2. Point its directory to the external path
project(":jaid").projectDir = file("../Jaid")