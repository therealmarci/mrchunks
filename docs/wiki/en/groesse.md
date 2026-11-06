# Size and chunk budget

Every active loader costs as many chunks from your **budget** as it is big: 7×7 costs 49, 3×3 costs 9, 3×15 costs 45. **Switched-off loaders cost nothing.** With the default budget of 147 chunks you can run e.g. three 7×7 loaders - or sixteen 3×3 loaders.

## Set the size
In a loader's menu, **Settings → Size** opens the size setting:
- **Arrows north, south, west, east:** left click +1 chunk in that direction, right click −1. This way the area becomes a rectangle, and the block does not have to be in the middle - e.g. at the edge of a long farm.
- **Middle:** all four directions at once.
- The top shows how much budget you use and how much is free. **Show border** shows the edge and follows every change.
- Enlarging only works with enough free budget and up to the server's maximum size (default 15×15). If your budget is full, switch off another loader or make one smaller.
- Only you may change the size - invited players may not.

> [!TIP]
> More budget comes from the team (per rank or command). How much you have is shown at the bottom right in `/chunkloader`.

## Good to know
- New loaders are 7×7. If your free budget is not enough, they get the biggest square that still fits; if nothing at all is free, they are placed but stay **off**.
- If two loaders overlap, the shared chunks count fully in the budget for each loader.
- If a [dummy](dummy.md) sits on the loader, the size can only be changed evenly in all directions.
- If an invited friend switches your loader on, it costs **your** budget.
