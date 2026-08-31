# MEProxy Roadmap

Planned direction for the mod. Versions ship one at a time and get real
server time before the next phase starts. Order can change, but bugfixes
always come before feature work.

## Current: v1.2.x

- ME Proxy block (NeoForge 1.21.1 port of the original, with per-tick
  caching to remove scan lag).
- ME/RS Network Bridge: two-way shared storage pool between one AE2
  network and one RS network. Local-first insert and extract, overflow
  spillover, separate autocrafting per side, loop-guarded and
  double-count-proof.

### v1.2.x bugfix line (in progress)

- Investigating: RS exporters reporting "no resources available" for
  items visible in the grid. v1.2.1 ships diagnostic logging on every
  bridge extraction path to pinpoint the failure. Fix lands as v1.2.2.

## v1.3 - Bridge Status Screen

Right-click the bridge block to open a read-only status GUI.

- Both connected networks with online/offline state and the reason when
  offline (no AE2 channel, RS unpowered, chunk not loaded).
- Nameable: set a display name per bridge (e.g. "Main Base Link"),
  stored on the block, shown in the UI. Bridge coordinates shown.
- Per-item source breakdown with search: one row per item showing how
  much of it sits on each side, e.g. "Iron Ingot - 300 AE2 / 200 RS".
- Basic transfer counters start recording here (see v1.6): total items
  and fluids moved across the bridge in each direction.

Small, self-contained, proves out the menu/screen/sync plumbing the
later phases need.

## v1.4 - Universal Grid (storage half)

A new terminal block, the mod's own grid, connected to a bridge. This
is the workaround for a hard limitation: AE2 and RS terminals aggregate
identical items into a single entry and cannot show where items are
stored without invasive hooks into their UIs. Our own grid can.

- Shows the combined pool with per-network separation: the same item
  appears as separate rows (or a split count) per source network, so
  "300 iron in AE2" and "200 iron in RS" are visibly distinct.
- Full terminal interaction: search, sorting, scroll, click to extract,
  shift-click and drag to insert. Extracting from a row pulls from that
  specific network.
- Implementation is adapted (forked) from the existing AE2 and RS
  terminal/grid code rather than written from scratch - both codebases
  have battle-tested list sync, search, and interaction handling.
  Licensing allows this: AE2 is LGPL-3.0 and Refined Storage is MIT,
  both compatible with this project's GPL-3.0, with credit given in the
  README and source headers.

This is the largest phase and may ship in sub-steps (view-only first,
then interaction).

## v1.5 - Universal Grid (autocrafting half)

- A crafting section in the Universal Grid listing craftables from all
  connected networks, clearly separated per network: what AE2 can
  autocraft and what RS can autocraft, side by side.
- Click a craftable, enter an amount, and the request is submitted to
  that network's own crafting system (AE2 crafting CPUs or RS
  crafters). The bridge already lets either side's crafting consume
  ingredients from the combined pool.
- After that: job status view (in-progress crafts per network) if the
  APIs cooperate.

## v1.6 - Stats Page

A statistics tab in the Universal Grid (basic counters visible in the
bridge status screen from v1.3 onward).

- Items in / items out per tick across the bridge, per direction
  (AE2 -> RS and RS -> AE2), with short rolling averages (per second /
  per minute) since per-tick numbers are spiky.
- Top items by transfer volume: which items cross the bridge most.
- Per-network totals over time: stored item count trends for each side.
- Honest scope note: the bridge can only measure what crosses it or
  what the storage APIs report. Cross-bridge throughput is exact.
  Whole-network in/out rates are derived from storage change events and
  are close but not machine-accurate. Per-machine attribution ("which
  exporter moved this") is not knowable from the storage layer and is
  out of scope.

## Deliberately not planned

- Modifying or overlaying the AE2/RS terminal GUIs to show item
  origins. Fragile against their updates; the Universal Grid is the
  answer instead.
- Tagging items with origin data components. Would stop identical
  items from stacking and break recipes.
- N-way relay ("network A sees network C through network B"). The
  current one-hop design is what makes double-counting impossible.
  A multi-network federation hub is a possible far-future rework, not
  an increment.

## Ideas parked for later

- Client-side tooltip experiment: hovering an item in a normal AE2/RS
  terminal shows "300 AE2 / 200 RS" as an optional, clearly-experimental
  toggle.
- Filtering/whitelisting what may cross a bridge.
- Publishing to Modrinth and CurseForge once v1.3 has proven stable.
