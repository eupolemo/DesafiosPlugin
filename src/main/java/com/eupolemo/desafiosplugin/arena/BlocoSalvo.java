package com.eupolemo.desafiosplugin.arena;

import org.bukkit.block.data.BlockData;

/**
 * O estado de um bloco antes de a arena passar por cima dele. Restaurar todos, na ordem
 * inversa, e o que desfaz uma arena.
 */
public record BlocoSalvo(int x, int y, int z, BlockData dado) {
}
