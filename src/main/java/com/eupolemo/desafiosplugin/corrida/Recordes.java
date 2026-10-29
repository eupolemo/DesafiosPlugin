package com.eupolemo.desafiosplugin.corrida;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * O melhor tempo de cada jogador em cada desafio. Um registro por jogador: o ranking mostra a
 * melhor tentativa de cada um, e nao a mesma pessoa dez vezes.
 */
public final class Recordes {

    /** Uma marca no ranking. */
    public record Marca(UUID jogador, String nome, long tempoMs, int mortes) {
    }

    private final DesafiosPlugin plugin;
    private final File arquivo;
    private YamlConfiguration yaml;

    public Recordes(DesafiosPlugin plugin) {
        this.plugin = plugin;
        this.arquivo = new File(plugin.getDataFolder(), "recordes.yml");
    }

    public void carregar() {
        yaml = arquivo.isFile() ? YamlConfiguration.loadConfiguration(arquivo)
                : new YamlConfiguration();
    }

    /**
     * @return true se o tempo e a melhor marca do jogador nesse desafio. Tempo pior nao apaga o
     *         recorde antigo — o ranking guarda o melhor, nao o ultimo.
     */
    public boolean registrar(String desafio, UUID jogador, String nome, long tempoMs, int mortes) {
        String base = "recordes." + desafio.toLowerCase(Locale.ROOT) + "." + jogador;
        long anterior = yaml.getLong(base + ".tempo", Long.MAX_VALUE);
        if (tempoMs >= anterior) {
            return false;
        }
        yaml.set(base + ".nome", nome);
        yaml.set(base + ".tempo", tempoMs);
        yaml.set(base + ".mortes", mortes);
        yaml.set(base + ".quando", System.currentTimeMillis());
        salvar();
        return true;
    }

    public List<Marca> ranking(String desafio, int limite) {
        List<Marca> marcas = new ArrayList<>();
        ConfigurationSection secao = yaml.getConfigurationSection(
                "recordes." + desafio.toLowerCase(Locale.ROOT));
        if (secao == null) {
            return marcas;
        }
        for (String chave : secao.getKeys(false)) {
            try {
                marcas.add(new Marca(UUID.fromString(chave),
                        secao.getString(chave + ".nome", "?"),
                        secao.getLong(chave + ".tempo"),
                        secao.getInt(chave + ".mortes")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Recorde com chave invalida em '" + desafio + "': "
                        + chave);
            }
        }
        marcas.sort(Comparator.comparingLong(Marca::tempoMs));
        return marcas.size() <= limite ? marcas : marcas.subList(0, limite);
    }

    /** @return a posicao do jogador no ranking, contando a partir de 1, ou 0 se ele nao esta la */
    public int posicao(String desafio, UUID jogador) {
        List<Marca> todas = ranking(desafio, Integer.MAX_VALUE);
        for (int i = 0; i < todas.size(); i++) {
            if (todas.get(i).jogador().equals(jogador)) {
                return i + 1;
            }
        }
        return 0;
    }

    private void salvar() {
        try {
            if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("nao consegui criar " + plugin.getDataFolder());
            }
            yaml.save(arquivo);
        } catch (IOException e) {
            plugin.getLogger().severe("Nao consegui salvar recordes.yml: " + e.getMessage());
        }
    }
}
