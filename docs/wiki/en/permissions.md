# Permissions

"OP" means: only operators have it automatically. "nobody" means: it has to be given explicitly. How the groups are built in all plugins: [Permissions with LuckPerms](https://mrtechnology.marcelr.at/wiki/allgemein/rechte/).

## Groups
Every group contains the one below, the team groups also contain `mrchunks.player` - best give `mrchunks.player` to the default group:

<!-- auto:permission-groups -->

```text
/lp group default permission set mrchunks.player true
/lp group supporter permission set mrchunks.groups.support true
/lp group staff permission set mrchunks.groups.staff true
/lp group admin permission set mrchunks.groups.admin true
```

## Single permissions
"Included in" names the smallest group that brings the permission; "–" means: in no group, only given on its own.

<!-- auto:permissions -->

Plus two permissions with a number you choose yourself:

| Permission | What for |
|---|---|
| `mrchunks.budget.<amount>` | Chunk budget, e.g. `mrchunks.budget.300` (see [Chunk budget](budget.md)) |
| `mrchunks.dummies.<amount>` | This many dummy players, e.g. `mrchunks.dummies.2` |

## Wildcards
> [!WARNING]
> `*` and `mrchunks.*` contain **everything** in LuckPerms - also `mrchunks.exempt` (loaders are deleted!) and the `unlimited` permissions. For such groups:
> ```text
> /lp group admin permission set mrchunks.exempt false
> ```
> If the budget should still apply, also set `mrchunks.budget.unlimited` to `false`.

## Examples
```text
# All players may use chunk loaders
/lp group default permission set mrchunks.player true

# VIPs: 300 chunks budget (e.g. six 7x7 loaders)
/lp group vip permission set mrchunks.budget.300 true

# Moderators: like staff (loaders on/off, invitations, accounts), plus banning - but no deleting
/lp group mod permission set mrchunks.groups.staff true
/lp group mod permission set mrchunks.admin.ban true

# Exclude alt accounts completely
/lp group alt permission set mrchunks.exempt true
```
