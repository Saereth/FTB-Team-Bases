# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [21.1.10]

### Added
* Added automatic chunk claiming for the lobby area, using FTB Chunks
  * See the new "autoclaiming" section in `ftbteambases-server.snbt` config file for control of this feature
  * FTB Teams 2101.1.7 or later required

## [21.1.9]

### Fixed
* Fix `TeamBasePortalEvent` not getting posted correctly.

## [21.1.8]

### Added
* (NeoForge only) A `TeamBasesPortalEvent` event is now posted when players walk into a team bases portal
  * Cancelling with `event.cancelWithReason(textComponent)` will prevent portal usage and display the reason to the player
  * Can be used to e.g. require the player has a game stage or has completed a quest

### Fixed
* Fixed behaviour when force-disbanding teams, particularly when players are offline
  * Was failing to archive base if `/ftbteams force-disband` is run when all team players are offline
  * Now ensures all players in the team (even if offline) are sent back to the lobby on next login (and inventory cleared if appropriate)
  * Also added `/ftbteambases archive verify` to archive any orphaned bases (i.e. where the team can't be found) that are detected

## [21.1.7]

### Added
* Added `biome_source_from_dimension` server config setting to allow created private dimensions to use the biome source of an existing dimension

### Fixed
* Hopefully fix issues with the lobby spawn position getting messed up

### Fixed

## [21.1.6]

### Fixed
* Resolved issue with structure sets not being loaded properly

## [21.1.5]

### Added
* Support structure sets in the `pregen` dimension construction type

## [21.1.4]

### Added
* Added Chinese translation

## [21.1.3]

### Added
* Added ru_ru translation (thanks @BazZziliuS)

### Fixed
* Fixed players returning from the Nether getting sent to base spawn instead of the portal they went through

## [21.1.2]

### Added
* Added `/ftbteambases setlobbypos <pos>` command to updated lobby spawn position

## [21.1.1]

### Added
* Initial public release
