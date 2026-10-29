package com.eupolemo.desafiosplugin.desafio;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * O que espera no fim do corredor.
 *
 * @param comandos rodados no console com %jogador% trocado pelo nome de quem venceu. E por aqui
 *                 que o premio pode ser um item de outro plugin, sem este depender dele.
 */
public record Premio(String mensagem, int xp, List<ItemStack> itens, List<String> comandos) {

    public static final Premio VAZIO = new Premio("", 0, List.of(), List.of());
}
