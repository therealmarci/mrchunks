package at.marcelr.mrchunks.dummy;

import net.luckperms.api.LuckPermsProvider;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Loads the LuckPerms user of a dummy before it joins. LuckPerms normally loads users while a client logs in - a dummy
 * joins without login, so plugins that ask LuckPerms on join (chat and tab plugins) found no user ("Unable to get a
 * user"). Only used while LuckPerms is installed (class loaded on demand, compileOnly).
 */
final class LuckPermsUsers {

    private LuckPermsUsers() {
    }

    /**
     * Loads (or creates) the user; waits at most two seconds. Without the name: LuckPerms only accepts real Minecraft
     * names (letters, digits, _) and refused dummy names like "[D1]Name" with "Invalid username entry".
     */
    static void load(UUID id) throws Exception {
        LuckPermsProvider.get().getUserManager().loadUser(id).get(2, TimeUnit.SECONDS);
    }
}
