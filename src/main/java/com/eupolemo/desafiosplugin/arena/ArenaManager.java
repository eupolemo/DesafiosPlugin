package com.eupolemo.desafiosplugin.arena;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import com.eupolemo.desafiosplugin.desafio.Desafio;
import com.eupolemo.desafiosplugin.gerador.Geradores;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * As arenas construidas. Guarda so a origem, as medidas, a seed e a lista de etapas — a geometria
 * toda sai disso por calculo, entao o arquivo fica pequeno e nunca sai de sincronia com o que
 * esta no mundo.
 *
 * <p>Uma arena por desafio: construir de novo o mesmo desafio derruba a anterior antes. Duas
 * arenas do mesmo desafio significariam dois recordes concorrentes e duas listas de blocos
 * salvos com a mesma chave.
 */
public final class ArenaManager {

    private final DesafiosPlugin plugin;
    private final File arquivo;
    private final Map<String, Arena> porId = new LinkedHashMap<>();

    public ArenaManager(DesafiosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "arenas.yml");
    }

    public void carregar() {
        porId.clear();
        if (!arquivo.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(arquivo);
        ConfigurationSection raiz = yaml.getConfigurationSection("arenas");
        if (raiz == null) {
            return;
        }
        for (String id : raiz.getKeys(false)) {
            ConfigurationSection secao = raiz.getConfigurationSection(id);
            if (secao == null) {
                continue;
            }
            try {
                List<String> tipos = new ArrayList<>();
                for (String tipo : secao.getStringList("tipos")) {
                    if (Geradores.porId(tipo) == null) {
                        plugin.getLogger().warning("Arena '" + id + "' usa o gerador '" + tipo
                                + "', que nao existe mais. Remova e construa de novo.");
                    }
                    tipos.add(tipo);
                }
                porId.put(id.toLowerCase(Locale.ROOT), new Arena(
                        id.toLowerCase(Locale.ROOT),
                        secao.getString("desafio", id),
                        UUID.fromString(secao.getString("mundo", "")),
                        secao.getLong("seed"),
                        secao.getInt("x"), secao.getInt("y"), secao.getInt("z"),
                        secao.getInt("largura"), secao.getInt("altura"), secao.getInt("comprimento"),
                        List.copyOf(tipos)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Arena '" + id + "' esta com o registro quebrado: "
                        + e.getMessage() + ". Ignorada.");
            }
        }
        if (!porId.isEmpty()) {
            plugin.getLogger().info(porId.size() + " arena(s) carregada(s): "
                    + String.join(", ", porId.keySet()));
        }
    }

    public void salvar() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena arena : porId.values()) {
            String base = "arenas." + arena.id() + ".";
            yaml.set(base + "desafio", arena.desafio());
            yaml.set(base + "mundo", arena.mundo().toString());
            yaml.set(base + "seed", arena.seed());
            yaml.set(base + "x", arena.x0());
            yaml.set(base + "y", arena.y0());
            yaml.set(base + "z", arena.z0());
            yaml.set(base + "largura", arena.largura());
            yaml.set(base + "altura", arena.altura());
            yaml.set(base + "comprimento", arena.comprimento());
            yaml.set(base + "tipos", arena.tipos());
        }
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("nao consegui criar " + plugin.getDataFolder());
            }
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().severe("Nao consegui salvar arenas.yml: " + e.getMessage()
                    + ". As arenas nao vao sobreviver a um restart.");
        }
    }

    /**
     * Desenha o plano de uma arena a partir de onde o jogador esta. O corredor sempre cresce no
     * sentido +Z, e o jogador fica atras da parede de entrada.
     *
     * @param seed null sorteia uma nova
     */
    public Arena planejar(Desafio desafio, Location origem, Long seed) {
        int largura = imparPeloMenos(plugin.getConfig().getInt("arena.largura", 15), 9);
        int altura = Math.max(5, plugin.getConfig().getInt("arena.altura", 8));
        int comprimento = imparPeloMenos(plugin.getConfig().getInt("arena.comprimento", 21), 11);
        int distancia = Math.max(2, plugin.getConfig().getInt("arena.distancia-inicial", 6));

        long sementeReal = seed != null ? seed : new Random().nextLong();
        List<String> tipos = Geradores.sortear(desafio.etapas(), desafio.dificuldade(),
                desafio.tipos(), new Random(sementeReal));

        return new Arena(desafio.id(), desafio.id(), origem.getWorld().getUID(), sementeReal,
                origem.getBlockX() - largura / 2, origem.getBlockY(),
                origem.getBlockZ() + distancia,
                largura, altura, comprimento, tipos);
    }

    /**
     * @return null se a arena cabe no mundo, ou a explicacao de por que nao cabe. Construir fora
     *         do limite de altura nao da erro: os blocos simplesmente somem, e a arena fica sem
     *         teto ou sem fundo.
     */
    public String motivoParaNaoCaber(Arena arena, World mundo) {
        int yFundo = arena.y0() - Arena.PROFUNDIDADE_POCO - 1;
        int yTeto = arena.y0() + arena.altura();
        if (yFundo < mundo.getMinHeight()) {
            return "muito baixo: a arena precisa comecar pelo menos "
                    + (mundo.getMinHeight() + Arena.PROFUNDIDADE_POCO + 1) + " blocos acima do "
                    + "fundo do mundo.";
        }
        if (yTeto >= mundo.getMaxHeight()) {
            return "muito alto: a arena precisa de " + (arena.altura() + 1)
                    + " blocos livres acima de voce, e o teto do mundo esta em "
                    + mundo.getMaxHeight() + ".";
        }
        return null;
    }

    public void registrar(Arena arena) {
        porId.put(arena.id(), arena);
        salvar();
    }

    public void esquecer(String id) {
        porId.remove(id.toLowerCase(Locale.ROOT));
        salvar();
    }

    public Arena porId(String id) {
        return id == null ? null : porId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> todas() {
        return porId.values();
    }

    /** @return a arena que contem a posicao, ou null */
    public Arena em(Location loc) {
        for (Arena arena : porId.values()) {
            if (arena.contem(loc)) {
                return arena;
            }
        }
        return null;
    }

    private static int imparPeloMenos(int valor, int minimo) {
        int ajustado = Math.max(minimo, valor);
        return ajustado % 2 == 0 ? ajustado + 1 : ajustado;
    }
}
