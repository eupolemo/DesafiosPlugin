package com.eupolemo.desafiosplugin.desafio;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import com.eupolemo.desafiosplugin.gerador.Dificuldade;
import com.eupolemo.desafiosplugin.gerador.Geradores;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Le a secao {@code desafios} do config.yml e mantem os desafios em memoria. */
public final class DesafioManager {

    /** Teto de etapas por desafio. Cada etapa e uma sala inteira construida no mundo. */
    private static final int MAXIMO_ETAPAS = 20;

    private final DesafiosPlugin plugin;
    private final Map<String, Desafio> porId = new LinkedHashMap<>();

    public DesafioManager(DesafiosPlugin plugin) {
        this.plugin = plugin;
    }

    public void carregar() {
        porId.clear();
        ConfigurationSection raiz = plugin.getConfig().getConfigurationSection("desafios");
        if (raiz == null) {
            plugin.getLogger().warning("Nenhum desafio no config.yml: a secao 'desafios' sumiu.");
            return;
        }

        for (String id : raiz.getKeys(false)) {
            ConfigurationSection secao = raiz.getConfigurationSection(id);
            if (secao == null) {
                continue;
            }
            String chave = id.toLowerCase(Locale.ROOT);
            Dificuldade dificuldade = Dificuldade.porNome(secao.getString("dificuldade"),
                    Dificuldade.NORMAL);
            int etapas = Math.clamp(secao.getInt("etapas", 4), 1, MAXIMO_ETAPAS);
            if (etapas != secao.getInt("etapas", 4)) {
                plugin.getLogger().warning("Desafio '" + chave + "': etapas fora de 1.."
                        + MAXIMO_ETAPAS + ", ajustado para " + etapas + ".");
            }

            List<String> tipos = new ArrayList<>();
            for (String tipo : secao.getStringList("tipos")) {
                String limpo = tipo.toLowerCase(Locale.ROOT).trim();
                if (Geradores.porId(limpo) == null) {
                    plugin.getLogger().warning("Desafio '" + chave + "': gerador desconhecido '"
                            + tipo + "'. Conhecidos: " + String.join(", ", Geradores.ids()));
                    continue;
                }
                tipos.add(limpo);
            }

            porId.put(chave, new Desafio(chave,
                    secao.getString("nome", chave),
                    dificuldade,
                    etapas,
                    Math.max(0, secao.getInt("tempo-limite", 0)),
                    List.copyOf(tipos),
                    lerPremio(chave, secao.getConfigurationSection("premio"))));
        }
    }

    private Premio lerPremio(String desafio, ConfigurationSection secao) {
        if (secao == null) {
            return Premio.VAZIO;
        }
        List<ItemStack> itens = new ArrayList<>();
        for (Map<?, ?> bruto : secao.getMapList("itens")) {
            Object nomeMaterial = bruto.get("material");
            if (nomeMaterial == null) {
                continue;
            }
            Material material = Material.matchMaterial(nomeMaterial.toString());
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Premio de '" + desafio + "': item desconhecido '"
                        + nomeMaterial + "'. Ignorado.");
                continue;
            }
            int quantidade = bruto.get("quantidade") instanceof Number n ? n.intValue() : 1;
            ItemStack item = new ItemStack(material, Math.clamp(quantidade, 1, 64));
            if (bruto.get("nome") != null) {
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(bruto.get("nome").toString(),
                        NamedTextColor.GOLD).decoration(net.kyori.adventure.text.format
                        .TextDecoration.ITALIC, false));
                item.setItemMeta(meta);
            }
            itens.add(item);
        }
        return new Premio(secao.getString("mensagem", ""),
                Math.max(0, secao.getInt("xp", 0)),
                List.copyOf(itens),
                List.copyOf(secao.getStringList("comandos")));
    }

    public Desafio porId(String id) {
        return id == null ? null : porId.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Desafio> todos() {
        return porId.values();
    }

    public List<String> ids() {
        return List.copyOf(porId.keySet());
    }
}
