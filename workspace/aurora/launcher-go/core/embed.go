package core

import (
	_ "embed"
)

//go:embed assets/mods.json
var modsManifestJSON []byte

//go:embed assets/AetherGlass.jar
var ClientModJar []byte

// EmbeddedModManifest — манифест модов, собранный из Modrinth API: точные
// версии, размеры и контрольные суммы, а не «последняя какая попадётся».
func EmbeddedModManifest() (*ModManifest, error) {
	return ParseModManifest(modsManifestJSON)
}
