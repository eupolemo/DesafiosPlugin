package com.eupolemo.desafiosplugin.arena;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import com.eupolemo.desafiosplugin.desafio.Desafio;
import com.eupolemo.desafiosplugin.gerador.Contexto;
import com.eupolemo.desafiosplugin.gerador.Gerador;
import com.eupolemo.desafiosplugin.gerador.Geradores;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Monta a arena inteira em memoria e depois despeja no mundo em lotes por tick, guardando o que
 * cada bloco era antes.
 *
 * <p>A ordem importa. Primeiro a casca — paredes, teto, fundo e as divisorias entre as salas —,
 * depois os vaos de porta, e so entao cada gerador desenha a sua sala. Se os vaos fossem abertos
 * por ultimo, a grade de ferro que a sala dos guardioes coloca na porta seria apagada; se a casca
 * viesse por ultimo, ela apagaria o labirinto.
 */
public final class ArenaBuilder {

    private final DesafiosPlugin plugin;
    private final RegistroBlocos registro;

    public ArenaBuilder(DesafiosPlugin plugin, RegistroBlocos registro) {
        this.plugin = plugin;
        this.registro = registro;
    }

    /**
     * Constroi a arena. A escrita e assincrona no tempo — sai espalhada por varios ticks —, entao
     * {@code aoTerminar} e o unico ponto em que a arena esta de fato pronta.
     */
    public void construir(Arena arena, Desafio desafio, World mundo, CommandSender remetente,
                          Runnable aoTerminar) {
        List<Consumer<World>> depois = new ArrayList<>();
        Molde molde = montar(arena, desafio, depois);

        int porTick = Math.max(1, plugin.getConfig().getInt("arena.blocos-por-tick", 1500));
        List<Map.Entry<Long, BlockData>> ordem = new ArrayList<>(molde.tamanho());
        ordem.addAll(molde.estrutura().entrySet());
        ordem.addAll(molde.acabamento().entrySet());

        Iterator<Map.Entry<Long, BlockData>> fila = ordem.iterator();
        List<BlocoSalvo> anteriores = new ArrayList<>(ordem.size());

        remetente.sendMessage(Component.text("Construindo '" + arena.id() + "': "
                + ordem.size() + " blocos, " + arena.tipos().size() + " etapas.",
                NamedTextColor.GRAY));

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < porTick; i++) {
                    if (!fila.hasNext()) {
                        registro.salvar(arena.id(), mundo.getUID(), anteriores);
                        for (Consumer<World> acao : depois) {
                            acao.accept(mundo);
                        }
                        remetente.sendMessage(Component.text("Arena '" + arena.id()
                                + "' pronta. Entre com /desafio entrar " + arena.desafio() + ".",
                                NamedTextColor.GREEN));
                        if (aoTerminar != null) {
                            aoTerminar.run();
                        }
                        cancel();
                        return;
                    }
                    Map.Entry<Long, BlockData> entrada = fila.next();
                    int x = Molde.chaveX(entrada.getKey());
                    int y = Molde.chaveY(entrada.getKey());
                    int z = Molde.chaveZ(entrada.getKey());
                    Block bloco = mundo.getBlockAt(x, y, z);
                    BlockData atual = bloco.getBlockData();
                    if (atual.matches(entrada.getValue())) {
                        continue;
                    }
                    anteriores.add(new BlocoSalvo(x, y, z, atual));
                    bloco.setBlockData(entrada.getValue(), false);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Desfaz a arena, devolvendo cada bloco ao que era antes. */
    public void remover(String id, CommandSender remetente, Runnable aoTerminar) {
        RegistroBlocos.Salvo salvo = registro.carregar(id);
        if (salvo == null) {
            remetente.sendMessage(Component.text("Nao ha blocos salvos da arena '" + id
                    + "'. Ela pode ter sido construida por uma versao anterior; o que estiver no "
                    + "mundo tem de sair na mao.", NamedTextColor.RED));
            if (aoTerminar != null) {
                aoTerminar.run();
            }
            return;
        }
        World mundo = plugin.getServer().getWorld(salvo.mundo());
        if (mundo == null) {
            remetente.sendMessage(Component.text("O mundo da arena '" + id
                    + "' nao esta carregado.", NamedTextColor.RED));
            return;
        }

        int porTick = Math.max(1, plugin.getConfig().getInt("arena.blocos-por-tick", 1500));
        List<BlocoSalvo> blocos = salvo.blocos();
        // De tras para frente: e a ordem inversa da construcao, entao o que foi sobrescrito por
        // ultimo volta primeiro.
        int[] cursor = {blocos.size() - 1};

        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < porTick; i++) {
                    if (cursor[0] < 0) {
                        registro.apagar(id);
                        remetente.sendMessage(Component.text("Arena '" + id + "' removida: "
                                + blocos.size() + " blocos restaurados.", NamedTextColor.GREEN));
                        if (aoTerminar != null) {
                            aoTerminar.run();
                        }
                        cancel();
                        return;
                    }
                    BlocoSalvo bloco = blocos.get(cursor[0]--);
                    mundo.getBlockAt(bloco.x(), bloco.y(), bloco.z())
                            .setBlockData(bloco.dado(), false);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private Molde montar(Arena arena, Desafio desafio, List<Consumer<World>> depois) {
        Molde molde = new Molde();
        Material casca = switch (desafio.dificuldade()) {
            case FACIL -> Material.STONE_BRICKS;
            case NORMAL -> Material.DEEPSLATE_BRICKS;
            case DIFICIL -> Material.POLISHED_BLACKSTONE_BRICKS;
            case INSANO -> Material.OBSIDIAN;
        };

        int total = arena.totalSalas();
        int yFundo = arena.y0() - Arena.PROFUNDIDADE_POCO - 1;
        int yPiso = arena.y0() - 1;
        int yTeto = arena.y0() + arena.altura();
        int zIni = arena.z0() - 1;
        int zFim = arena.zFim(total - 1) + 1;
        int xw1 = arena.x0() - 1;
        int xw2 = arena.x0() + arena.largura();

        molde.preencher(new Caixa(xw1, yFundo, zIni, xw1, yTeto, zFim), casca);
        molde.preencher(new Caixa(xw2, yFundo, zIni, xw2, yTeto, zFim), casca);
        molde.preencher(new Caixa(xw1, yTeto, zIni, xw2, yTeto, zFim), casca);
        molde.preencher(new Caixa(xw1, yFundo, zIni, xw2, yFundo, zFim), casca);
        molde.preencher(new Caixa(xw1, yFundo, zIni, xw2, yTeto, zIni), casca);
        molde.preencher(new Caixa(xw1, yFundo, zFim, xw2, yTeto, zFim), casca);
        for (int i = 1; i < total; i++) {
            int z = arena.zInicio(i) - 1;
            molde.preencher(new Caixa(xw1, yFundo, z, xw2, yTeto, z), casca);
        }
        for (int i = 1; i < total; i++) {
            molde.limpar(arena.portaAntesDe(i));
        }

        for (int i = 0; i < total; i++) {
            Caixa interior = arena.interior(i);
            molde.limpar(interior);

            String tipo = arena.tipoDaSala(i);
            Gerador gerador = Geradores.porId(tipo);
            boolean pisoSolido = gerador == null || gerador.pisoSolido();

            if (pisoSolido) {
                molde.preencher(new Caixa(interior.x1(), yPiso, interior.z1(),
                        interior.x2(), yPiso, interior.z2()), casca);
            } else {
                // Poco: o chao some ate o fundo da arena, que ja foi construido la embaixo.
                molde.limpar(new Caixa(interior.x1(), yFundo + 1, interior.z1(),
                        interior.x2(), yPiso, interior.z2()));
            }

            if (gerador != null) {
                Contexto ctx = new Contexto(molde, arena, i, interior, desafio.dificuldade(),
                        new Random(arena.seed() * 31L + i), depois);
                gerador.construir(ctx);
            } else if (i == 0) {
                montarEntrada(molde, arena, desafio, interior, depois);
            } else {
                montarPremio(molde, arena, desafio, interior, depois);
            }
        }

        // Por ultimo, ja com a parede da frente de pe: e nela que o botao se apoia.
        montarPortico(molde, arena, desafio, casca, depois);
        return molde;
    }

    /**
     * O portico: uma alcova do lado de fora da parede da frente, com placa, botao e placa de
     * pressao. E por aqui que se entra no desafio sem digitar comando — quem chega andando ve o
     * nome do desafio e tem duas formas obvias de comecar.
     */
    private void montarPortico(Molde molde, Arena arena, Desafio desafio, Material casca,
                               List<Consumer<World>> depois) {
        Caixa portico = arena.portico();
        int chao = arena.y0() - 1;
        int teto = portico.y2();

        molde.limpar(portico);
        molde.preencher(new Caixa(portico.x1(), chao, portico.z1(),
                portico.x2(), chao, portico.z2()), Material.POLISHED_BLACKSTONE);
        molde.preencher(new Caixa(portico.x1(), teto, portico.z1(),
                portico.x2(), teto, portico.z2()), casca);

        for (int x : new int[]{portico.x1(), portico.x2()}) {
            for (int z : new int[]{portico.z1(), portico.z2()}) {
                for (int y = arena.y0(); y < teto; y++) {
                    molde.por(x, y, z, casca);
                }
            }
            molde.por(x, teto - 1, portico.centroZ(), Blocos.lanternaPendurada());
        }

        int[] botao = arena.posicaoBotao();
        molde.por(botao[0], botao[1], botao[2], Blocos.botaoDeParede(BlockFace.NORTH));

        int[] placaChao = arena.posicaoPlaca();
        molde.por(placaChao[0], placaChao[1], placaChao[2], Material.STONE_PRESSURE_PLATE);

        int px = arena.centroX() - 1;
        int py = arena.y0() + 1;
        int pz = arena.z0() - 2;
        molde.por(px, py, pz, Blocos.placaDeParede(BlockFace.NORTH));
        depois.add(mundo -> Blocos.escrever(mundo, px, py, pz, List.of(
                Component.text(cortar(desafio.nome()), NamedTextColor.DARK_BLUE),
                Component.text(desafio.dificuldade().rotulo(), NamedTextColor.BLACK),
                Component.text("Pise na placa", NamedTextColor.BLACK),
                Component.text("ou use o botao", NamedTextColor.BLACK))));
    }

    /** Sala de largada: piso marcado, luz e uma placa com as regras do desafio. */
    private void montarEntrada(Molde molde, Arena arena, Desafio desafio, Caixa interior,
                               List<Consumer<World>> depois) {
        int y = interior.y1() - 1;
        for (int x = interior.x1(); x <= interior.x2(); x++) {
            molde.por(x, y, interior.z2() - 1, Material.YELLOW_CONCRETE);
        }
        for (int z = interior.z1(); z <= interior.z2(); z += 4) {
            molde.por(interior.x1(), interior.y2(), z, Material.SEA_LANTERN);
            molde.por(interior.x2(), interior.y2(), z, Material.SEA_LANTERN);
        }

        int px = arena.centroX() - 2;
        int py = interior.y1() + 1;
        int pz = interior.z1();
        molde.por(px, py, pz, Blocos.placaDeParede(BlockFace.SOUTH));
        String tempo = desafio.temTempoLimite() ? (desafio.tempoLimite() / 60) + " min" : "sem limite";
        depois.add(mundo -> Blocos.escrever(mundo, px, py, pz, List.of(
                Component.text(cortar(desafio.nome()), NamedTextColor.DARK_BLUE),
                Component.text(desafio.dificuldade().rotulo(), NamedTextColor.BLACK),
                Component.text(desafio.etapas() + " etapas", NamedTextColor.BLACK),
                Component.text(tempo, NamedTextColor.BLACK))));
    }

    /** Sala do premio: um pedestal para o fim da corrida ter cara de fim. */
    private void montarPremio(Molde molde, Arena arena, Desafio desafio, Caixa interior,
                              List<Consumer<World>> depois) {
        int x = arena.centroX();
        int z = interior.z1() + interior.comprimento() / 2;
        int y = interior.y1();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                molde.por(x + dx, y - 1, z + dz, Material.GOLD_BLOCK);
            }
        }
        // O beacon fica direto sobre a camada de ouro: assim ele conta como piramide de nivel 1
        // e acende. O teto barra o feixe, mas o bloco brilha, que e o que interessa aqui.
        molde.por(x, y, z, Material.BEACON);
        for (int dz = -2; dz <= 2; dz += 4) {
            molde.por(x, interior.y2(), z + dz, Blocos.lanternaPendurada());
        }

        int px = arena.centroX() - 2;
        int py = interior.y1() + 1;
        int pz = interior.z1();
        molde.por(px, py, pz, Blocos.placaDeParede(BlockFace.SOUTH));
        depois.add(mundo -> Blocos.escrever(mundo, px, py, pz, List.of(
                Component.text("Voce chegou", NamedTextColor.DARK_GREEN),
                Component.text("ao fim de", NamedTextColor.BLACK),
                Component.text(cortar(desafio.nome()), NamedTextColor.DARK_BLUE),
                Component.text("", NamedTextColor.BLACK))));
    }

    /** Placa tem 15 caracteres uteis por linha; o resto vira reticencias. */
    private static String cortar(String texto) {
        return texto.length() <= 15 ? texto : texto.substring(0, 14) + "…";
    }
}
