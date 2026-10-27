package de.basky.servertags.command;

import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Field;
import java.util.Map;

public final class VanillaTagAlias {
    private VanillaTagAlias() {}

    public static void move(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> old = dispatcher.getRoot().getChild("tag");
        if (old == null || old.getChild("targets") == null)
            throw new IllegalStateException("Vanilla-/tag fehlt oder wurde bereits verändert.");
        if (dispatcher.getRoot().getChild("entitytag") != null)
            throw new IllegalStateException("/entitytag ist bereits belegt.");
        var alias = literal("entitytag").requires(old.getRequirement());
        old.getChildren().forEach(alias::then);
        dispatcher.register(alias);
        // Brigadier exposes no removal API. All three lookup maps must be updated together;
        // removing only getChildren() leaves a stale literal in getRelevantNodes().
        try {
            for (String name : new String[] {"children", "literals", "arguments"}) {
                Field field = CommandNode.class.getDeclaredField(name);
                field.setAccessible(true);
                ((Map<?, ?>) field.get(dispatcher.getRoot())).remove("tag");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Vanilla-/tag konnte nicht verschoben werden.", e);
        }
    }
}
