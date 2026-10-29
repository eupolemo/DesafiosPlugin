package com.eupolemo.desafiosplugin.arena;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.Lantern;

import java.util.List;

/** Estados de bloco que precisam de uma propriedade ajustada, num lugar so. */
public final class Blocos {

    private Blocos() {
    }

    public static BlockData lanternaPendurada() {
        Lantern lanterna = (Lantern) Material.LANTERN.createBlockData();
        lanterna.setHanging(true);
        return lanterna;
    }

    public static BlockData lanternaAlmaPendurada() {
        Lantern lanterna = (Lantern) Material.SOUL_LANTERN.createBlockData();
        lanterna.setHanging(true);
        return lanterna;
    }

    /** Um observer serve de olho que atira: parece uma armadilha e nao abre menu nenhum. */
    public static BlockData olho(BlockFace face) {
        Directional dado = (Directional) Material.OBSERVER.createBlockData();
        dado.setFacing(face);
        return dado;
    }

    /**
     * Botao presa numa parede. {@code face} e o lado para onde ele aponta — o bloco de apoio fica
     * do lado oposto, e nao no lado indicado; trocar os dois faz o botao cair na hora de colocar.
     */
    public static BlockData botaoDeParede(BlockFace face) {
        Switch botao = (Switch) Material.POLISHED_BLACKSTONE_BUTTON.createBlockData();
        botao.setAttachedFace(FaceAttachable.AttachedFace.WALL);
        botao.setFacing(face);
        return botao;
    }

    public static BlockData placaDeParede(BlockFace face) {
        Directional dado = (Directional) Material.OAK_WALL_SIGN.createBlockData();
        dado.setFacing(face);
        return dado;
    }

    /**
     * Escreve numa placa ja colocada. Roda depois da construcao: o texto e um estado de bloco,
     * e o molde so carrega {@link BlockData}.
     */
    public static void escrever(World mundo, int x, int y, int z, List<Component> linhas) {
        Block bloco = mundo.getBlockAt(x, y, z);
        if (!(bloco.getState() instanceof Sign placa)) {
            return;
        }
        for (int i = 0; i < linhas.size() && i < 4; i++) {
            placa.getSide(Side.FRONT).line(i, linhas.get(i));
        }
        placa.setWaxed(true);
        placa.update(true, false);
    }
}
