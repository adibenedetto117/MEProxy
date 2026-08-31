# MEProxy

Bridge Applied Energistics 2 and Refined Storage into one shared storage pool.

A NeoForge mod for Minecraft 1.21.1 that lets an AE2 ME network and a Refined
Storage network see, store, and use each other's items and fluids - while each
side keeps its own storage, its own autocrafting, and its own way of playing.

Built for servers where different players or teams prefer different storage
mods but want to share one base inventory.

## Blocks

### ME/RS Network Bridge

The main event. Place it touching both an ME cable (AE2) and a Cable (Refined
Storage) and the two networks become windows into one combined pool:

- Every AE2 terminal and every RS grid shows the combined item and fluid
  totals of both networks as single merged entries.
- Items you insert stay on the side you inserted from. AE2 inserts land in ME
  drives, RS inserts land in RS disks. If your side is full, the overflow
  spills into the other side instead of bouncing back.
- Extraction drains your own side first, then pulls from the other side
  automatically.
- Autocrafting stays separate. AE2 patterns and crafting CPUs stay AE2, RS
  patterns and crafters stay RS - but both can consume ingredients from the
  combined pool.
- Storage attached to either network through its own connectors (drawers
  behind an AE2 Storage Bus, chests behind an RS External Storage, and so on)
  is part of that network's pool and comes along for free.

Requirements: the AE2 side uses one channel, the RS side needs the RS network
powered. Use one bridge per pair of networks.

No item duplication is possible by design. The bridge never copies anything:
every insert and extract passes through live to the single real copy, a
re-entrancy guard breaks enumeration cycles between the two networks, and the
bridge subtracts its own cached view so nothing is ever counted twice.

### ME Proxy

The original block this project started from. It exposes an entire ME
network's storage as a plain item/fluid inventory, so mods that only speak
vanilla-style inventories (Create funnels and pumps, pipes, hoppers, an RS
External Storage) can read and write the whole AE2 network as if it were one
giant chest. Uses one channel.

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1.
2. Install [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) 19.x
   and [Refined Storage](https://www.curseforge.com/minecraft/mc-mods/refined-storage) 2.x.
3. Drop the MEProxy jar into the `mods` folder on the server and every client.

## FAQ

**Which side actually stores the items?**
Wherever they were inserted. The bridge shares totals and access, not disk
space - each team's items physically live in their own drives/disks unless
their side fills up.

**Does AE2 autocrafting show up in RS, or vice versa?**
No. Only real stored items and fluids cross the bridge. Craftable-but-not-
stored entries, patterns, and crafting jobs stay on their own side.

**Why do RS counts lag half a second behind?**
The RS side of the bridge refreshes its view of AE2 every 10 game ticks.
The AE2 side updates event-driven and is near-instant.

**Can I use the ME Proxy and the Network Bridge at the same time?**
Yes, it is safe, but pointing an RS External Storage at the ME Proxy while a
bridge links the same two networks is redundant - remove the External Storage
and let the bridge do the work.

## Building from source

Requires Java 21.

```
./gradlew build
```

The jar lands in `build/libs/`.

## Credits and license

Based on [MEProxy by ProGoofster](https://github.com/ProGoofster/MEProxy)
(the original ME Proxy block for Forge 1.20.1). This project ports it to
NeoForge 1.21.1 and adds the ME/RS Network Bridge.

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
