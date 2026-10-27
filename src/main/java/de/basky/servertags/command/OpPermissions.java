package de.basky.servertags.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

public final class OpPermissions {
    private OpPermissions() {}

    // Native level 2 (Gamemaster) or higher; console has the owner permission set.
    public static boolean admin(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
