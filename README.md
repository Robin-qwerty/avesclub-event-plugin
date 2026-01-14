# AvesAnvilDrop (Paper 1.21.x)

Minecraft anvil-drop event manager:

- `/anvildrop open`: teleport all players from the **lobby world** to the **event world** and show a configurable title for a configurable number of seconds
- `/anvildrop start`: chat countdown for all players in the event world, then start repeating “anvil waves”
- `/anvildrop p` / `/anvildrop r`: pause / resume the repeating anvil waves
- `/anvildrop <percent>`: instantly do one anvil wave at that percent (0–100)
- `/anvildrop setarena`: save the arena cuboid from your current WorldEdit selection
- `/anvildrop stop`: stop the event and cancel tasks
- `/anvildrop reload`: reload config

## Permissions (LuckPerms compatible)

- `anvildrop.open`
- `anvildrop.start`
- `anvildrop.control` (pause/resume/stop/setarena/manual percent)
- `anvildrop.reload`

## Setup

1. Put the built jar in `plugins/`
2. Install **WorldEdit** (needed for `/anvildrop setarena`)
3. Edit `plugins/AvesAnvilDrop/config.yml`:
   - `worlds.lobbyWorld` and `worlds.eventWorld`
   - `spawns.lobby` and `spawns.event`
   - `arena.min/max` (or set it with `/anvildrop setarena`)
   - `dropper.*` (interval, starting percent, increase per wave, max percent)

## Notes

- The “alive players” scoreboard counts **participants** (players teleported in on open / present in event world at start) that are:
  - online
  - inside the arena cuboid
  - not eliminated (death inside arena during event)


