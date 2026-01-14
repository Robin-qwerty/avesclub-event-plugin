package club.aves.anvildrop.ffa;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record FFAKit(String name, List<ItemStack> contents, List<ItemStack> armor, ItemStack offhand) {
}


