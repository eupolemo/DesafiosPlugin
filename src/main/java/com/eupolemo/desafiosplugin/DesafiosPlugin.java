package com.eupolemo.desafiosplugin;

import com.eupolemo.desafiosplugin.arena.ArenaBuilder;
import com.eupolemo.desafiosplugin.arena.ArenaManager;
import com.eupolemo.desafiosplugin.arena.RegistroBlocos;
import com.eupolemo.desafiosplugin.command.DesafioCommand;
import com.eupolemo.desafiosplugin.corrida.CorridaManager;
import com.eupolemo.desafiosplugin.corrida.Recordes;
import com.eupolemo.desafiosplugin.desafio.DesafioManager;
import com.eupolemo.desafiosplugin.listener.DesafioListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class DesafiosPlugin extends JavaPlugin {

    /** Versao do formato de config.yml. Subir este numero arquiva a config antiga no proximo boot. */
    private static final int VERSAO_CONFIG = 1;

    private DesafioManager desafioManager;
    private ArenaManager arenaManager;
    private ArenaBuilder arenaBuilder;
    private CorridaManager corridaManager;
    private Recordes recordes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrarConfig();

        this.desafioManager = new DesafioManager(this);
        this.desafioManager.carregar();

        this.arenaManager = new ArenaManager(this);
        this.arenaManager.carregar();

        this.arenaBuilder = new ArenaBuilder(this,
                new RegistroBlocos(getDataFolder().toPath().resolve("blocos"), getLogger()));

        this.recordes = new Recordes(this);
        this.recordes.carregar();

        this.corridaManager = new CorridaManager(this, recordes);
        this.corridaManager.ligar();

        getServer().getPluginManager().registerEvents(new DesafioListener(this), this);

        PluginCommand comando = getCommand("desafio");
        if (comando == null) {
            getLogger().severe("Comando 'desafio' nao declarado no plugin.yml. Desabilitando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        DesafioCommand executor = new DesafioCommand(this);
        comando.setExecutor(executor);
        comando.setTabCompleter(executor);

        getLogger().info("DesafiosPlugin ativo com " + desafioManager.todos().size()
                + " desafio(s) e " + arenaManager.todas().size() + " arena(s).");
    }

    @Override
    public void onDisable() {
        if (corridaManager != null) {
            // Encerra as corridas em andamento: sem isso o jogador ficaria preso em modo aventura
            // dentro da arena, sem barra e sem comando que resolva.
            corridaManager.desligar();
        }
        if (arenaManager != null) {
            arenaManager.salvar();
        }
        getLogger().info("DesafiosPlugin desligado.");
    }

    /**
     * Arquiva um config.yml de formato antigo e gera um novo a partir do jar. Sem isso, quem ja
     * rodou uma versao anterior ficaria com a config velha — o Bukkit nunca sobrescreve — e sem
     * as chaves novas.
     */
    private void migrarConfig() {
        int versao = getConfig().getInt("versao-config", 1);
        if (versao >= VERSAO_CONFIG) {
            return;
        }

        File atual = new File(getDataFolder(), "config.yml");
        File backup = new File(getDataFolder(), "config-v" + versao + ".yml.bak");
        try {
            Files.move(atual.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().severe("Nao consegui arquivar o config.yml antigo: " + e.getMessage()
                    + ". Apague plugins/DesafiosPlugin/config.yml e reinicie.");
            return;
        }
        saveDefaultConfig();
        reloadConfig();
        getLogger().warning("config.yml da versao " + versao + " foi arquivado como "
                + backup.getName() + " e substituido pelo formato " + VERSAO_CONFIG + ".");
    }

    public DesafioManager getDesafioManager() {
        return desafioManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public ArenaBuilder getArenaBuilder() {
        return arenaBuilder;
    }

    public CorridaManager getCorridaManager() {
        return corridaManager;
    }

    public Recordes getRecordes() {
        return recordes;
    }
}
