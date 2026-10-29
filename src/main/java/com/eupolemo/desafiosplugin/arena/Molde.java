package com.eupolemo.desafiosplugin.arena;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A arena inteira montada em memoria antes de encostar no mundo. Os geradores escrevem aqui, e
 * so depois o {@link ArenaBuilder} despeja no mundo em lotes por tick.
 *
 * <p>Sao dois mapas por causa da ordem de colocacao. Lava, agua, tocha e placa colocadas antes
 * das paredes escorrem ou caem; entao tudo que e liquido ou fragil vai para o segundo mapa e so
 * entra depois que a estrutura fechou.
 */
public final class Molde {

    private final Map<Long, BlockData> estrutura = new LinkedHashMap<>();
    private final Map<Long, BlockData> acabamento = new LinkedHashMap<>();

    /**
     * Empacota a posicao num long: X nos bits 38..63, Z nos 12..37 e Y nos 0..11. Os 26 bits de
     * X e de Z cobrem +-33 milhoes, bem alem da borda do mundo; Y ganha 64 de folga para caber
     * sem sinal nos 12 bits.
     */
    public static long chave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 64) & 0xFFF);
    }

    /**
     * Os deslocamentos aqui tem de levar o bit mais alto do campo ate o bit 63 antes do
     * deslocamento a direita, senao o sinal se perde e a coordenada volta errada. X ja termina no
     * bit 63, entao basta o deslocamento a direita.
     */
    public static int chaveX(long chave) {
        return (int) (chave >> 38);
    }

    public static int chaveY(long chave) {
        return (int) (chave & 0xFFF) - 64;
    }

    public static int chaveZ(long chave) {
        return (int) (chave << 26 >> 38);
    }

    public void por(int x, int y, int z, Material material) {
        por(x, y, z, material.createBlockData());
    }

    public void por(int x, int y, int z, BlockData dado) {
        long chave = chave(x, y, z);
        if (fragil(dado.getMaterial())) {
            estrutura.remove(chave);
            acabamento.put(chave, dado);
        } else {
            acabamento.remove(chave);
            estrutura.put(chave, dado);
        }
    }

    public void preencher(Caixa caixa, Material material) {
        BlockData dado = material.createBlockData();
        for (int x = caixa.x1(); x <= caixa.x2(); x++) {
            for (int y = caixa.y1(); y <= caixa.y2(); y++) {
                for (int z = caixa.z1(); z <= caixa.z2(); z++) {
                    por(x, y, z, dado);
                }
            }
        }
    }

    /** Preenche a casca da caixa e deixa o miolo intacto. */
    public void contorno(Caixa caixa, Material material) {
        BlockData dado = material.createBlockData();
        for (int x = caixa.x1(); x <= caixa.x2(); x++) {
            for (int y = caixa.y1(); y <= caixa.y2(); y++) {
                for (int z = caixa.z1(); z <= caixa.z2(); z++) {
                    boolean borda = x == caixa.x1() || x == caixa.x2()
                            || y == caixa.y1() || y == caixa.y2()
                            || z == caixa.z1() || z == caixa.z2();
                    if (borda) {
                        por(x, y, z, dado);
                    }
                }
            }
        }
    }

    public void limpar(Caixa caixa) {
        preencher(caixa, Material.AIR);
    }

    public int tamanho() {
        return estrutura.size() + acabamento.size();
    }

    public Map<Long, BlockData> estrutura() {
        return Collections.unmodifiableMap(estrutura);
    }

    public Map<Long, BlockData> acabamento() {
        return Collections.unmodifiableMap(acabamento);
    }

    /**
     * Blocos que nao aguentam ser colocados no vazio: liquido escorre, tocha e placa caem,
     * areia despenca. Todos esperam a estrutura fechar.
     */
    private static boolean fragil(Material material) {
        if (material.hasGravity()) {
            return true;
        }
        return switch (material) {
            case LAVA, WATER, TORCH, WALL_TORCH, SOUL_TORCH, SOUL_WALL_TORCH, LANTERN,
                 SOUL_LANTERN, REDSTONE_TORCH, REDSTONE_WALL_TORCH, LADDER, VINE, COBWEB,
                 IRON_BARS, OAK_SIGN, OAK_WALL_SIGN, CHEST,
                 POLISHED_BLACKSTONE_BUTTON, STONE_PRESSURE_PLATE -> true;
            default -> false;
        };
    }
}
