package com.eupolemo.desafiosplugin.listener;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import com.eupolemo.desafiosplugin.arena.Arena;
import com.eupolemo.desafiosplugin.corrida.Corrida;
import com.eupolemo.desafiosplugin.corrida.CorridaManager;
import com.eupolemo.desafiosplugin.desafio.Desafio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * As regras que valem dentro de uma arena: nao se quebra bloco, nao nasce mob por conta propria,
 * e quase nada mata — cair, queimar ou levar o golpe final devolve ao checkpoint da sala.
 *
 * <p>Barrar o nascimento natural de mob nao e enfeite. As salas sao caixas fechadas e escuras,
 * que e exatamente a condicao de spawn do jogo: sem isto, um labirinto vira ninho de zumbi entre
 * uma corrida e outra.
 */
public final class DesafioListener implements Listener {

    /** Uma placa de pressao dispara a cada tique em que o jogador esta em cima dela. */
    private static final long ESPERA_ENTRE_GATILHOS_MS = 2000;

    private final DesafiosPlugin plugin;
    private final Map<UUID, Long> ultimoGatilho = new HashMap<>();

    public DesafioListener(DesafiosPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Entrada sem comando: botao, alavanca ou placa de pressao do portico comecam a corrida.
     *
     * <p>So valem os gatilhos do portico daquela arena. Um botao qualquer que o jogador tenha
     * colocado do lado de fora nao entra em desafio nenhum, e nada dentro da arena vira gatilho.
     */
    // Sem ignoreCancelled: o evento de interacao ja chega marcado como cancelado em varios
    // casos normais (modo aventura, item na mao que nao serve ali), e o portico tem de funcionar
    // do mesmo jeito — aqui so se le qual bloco foi tocado.
    @EventHandler
    public void aoInteragir(PlayerInteractEvent evento) {
        Block bloco = evento.getClickedBlock();
        if (bloco == null
                || (evento.getAction() != Action.PHYSICAL
                && evento.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !ehGatilho(bloco.getType())) {
            return;
        }
        Arena arena = plugin.getArenaManager().em(bloco.getLocation());
        if (arena == null || !arena.portico().contem(bloco.getLocation())) {
            return;
        }

        Player jogador = evento.getPlayer();
        if (plugin.getCorridaManager().correndo(jogador)) {
            return;
        }
        long agora = System.currentTimeMillis();
        Long ultimo = ultimoGatilho.get(jogador.getUniqueId());
        if (ultimo != null && agora - ultimo < ESPERA_ENTRE_GATILHOS_MS) {
            return;
        }
        ultimoGatilho.put(jogador.getUniqueId(), agora);

        if (!jogador.hasPermission("desafios.use")) {
            jogador.sendMessage(Component.text("Sem permissao para entrar em desafios.",
                    NamedTextColor.RED));
            return;
        }
        Desafio desafio = plugin.getDesafioManager().porId(arena.desafio());
        if (desafio == null) {
            jogador.sendMessage(Component.text("O desafio '" + arena.desafio() + "' sumiu do "
                    + "config.yml. A arena continua de pe, mas nao da para entrar.",
                    NamedTextColor.RED));
            return;
        }
        plugin.getCorridaManager().tentarIniciar(jogador, desafio, arena);
    }

    private static boolean ehGatilho(Material material) {
        String nome = material.name().toUpperCase(Locale.ROOT);
        return nome.endsWith("_BUTTON") || nome.endsWith("_PRESSURE_PLATE") || material == Material.LEVER;
    }

    /**
     * Nenhum efeito de pocao entra durante a corrida. Salto reforcado transforma o parkour num
     * passeio e velocidade quebra a pista de gelo; o desafio tem de medir o jogador, nao o
     * armario dele. Tirar efeito continua liberado — e assim que a largada limpa o que ele
     * trouxe, e o que ele trouxe volta no fim.
     */
    @EventHandler(ignoreCancelled = true)
    public void aoGanharEfeito(EntityPotionEffectEvent evento) {
        if (evento.getAction() == EntityPotionEffectEvent.Action.REMOVED) {
            return;
        }
        if (evento.getEntity() instanceof Player jogador
                && plugin.getCorridaManager().correndo(jogador)) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoBeber(PlayerItemConsumeEvent evento) {
        Material tipo = evento.getItem().getType();
        if ((tipo == Material.POTION || tipo == Material.CHORUS_FRUIT)
                && plugin.getCorridaManager().correndo(evento.getPlayer())) {
            evento.setCancelled(true);
            evento.getPlayer().sendActionBar(Component.text("Nada de pocao dentro do desafio.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoTentarVoar(PlayerToggleFlightEvent evento) {
        if (plugin.getCorridaManager().correndo(evento.getPlayer())) {
            evento.setCancelled(true);
            evento.getPlayer().setAllowFlight(false);
            evento.getPlayer().sendActionBar(Component.text("Sem voar aqui.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoPlanar(EntityToggleGlideEvent evento) {
        if (evento.isGliding() && evento.getEntity() instanceof Player jogador
                && plugin.getCorridaManager().correndo(jogador)) {
            evento.setCancelled(true);
            jogador.sendActionBar(Component.text("Sem elitro aqui.", NamedTextColor.RED));
        }
    }

    /** Perola cancelada e perola nao gasta: o jogador so nao pula o obstaculo com ela. */
    @EventHandler(ignoreCancelled = true)
    public void aoLancarProjetil(ProjectileLaunchEvent evento) {
        if (evento.getEntity() instanceof EnderPearl perola
                && perola.getShooter() instanceof Player jogador
                && plugin.getCorridaManager().correndo(jogador)) {
            evento.setCancelled(true);
            jogador.sendActionBar(Component.text("Sem perola dentro do desafio.",
                    NamedTextColor.RED));
        }
    }

    /** Rede de seguranca para qualquer outro atalho que termine em teleporte. */
    @EventHandler(ignoreCancelled = true)
    public void aoTeleportar(PlayerTeleportEvent evento) {
        if (!plugin.getCorridaManager().correndo(evento.getPlayer())) {
            return;
        }
        // DISMOUNT fica de fora de proposito: a largada tira o jogador de qualquer montaria, e
        // desmontar gera um teleporte com essa causa. Cancela-lo prenderia o jogador na sela.
        switch (evento.getCause()) {
            case ENDER_PEARL, CHORUS_FRUIT, SPECTATE -> {
                evento.setCancelled(true);
                evento.getPlayer().sendActionBar(Component.text("Sem atalho: o desafio e a pe.",
                        NamedTextColor.RED));
            }
            default -> {
                // COMMAND e PLUGIN passam: e assim que o proprio plugin devolve ao checkpoint.
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoTomarDano(EntityDamageEvent evento) {
        if (!(evento.getEntity() instanceof Player jogador)) {
            return;
        }
        Corrida corrida = plugin.getCorridaManager().de(jogador);
        if (corrida == null) {
            return;
        }

        boolean queimadura = switch (evento.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> true;
            default -> false;
        };
        if (queimadura && plugin.getConfig().getBoolean("corrida.volta-por-queimadura", true)) {
            evento.setCancelled(true);
            plugin.getCorridaManager().devolverAoCheckpoint(corrida, jogador,
                    Component.text("Voce se queimou."));
            return;
        }

        if (evento.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // A queda dentro da arena ja e resolvida pela altura minima; somar dano em cima disso
            // so puniria duas vezes o mesmo erro.
            evento.setCancelled(true);
            return;
        }

        if (plugin.getConfig().getBoolean("corrida.morte-real", false)) {
            return;
        }
        if (evento.getFinalDamage() >= jogador.getHealth()) {
            evento.setCancelled(true);
            plugin.getCorridaManager().devolverAoCheckpoint(corrida, jogador,
                    Component.text("Voce nao aguentou."));
        }
    }

    @EventHandler
    public void aoMorrer(PlayerDeathEvent evento) {
        plugin.getCorridaManager().encerrar(evento.getEntity(), CorridaManager.Fim.MORREU);
    }

    @EventHandler
    public void aoSair(PlayerQuitEvent evento) {
        plugin.getCorridaManager().encerrar(evento.getPlayer(), CorridaManager.Fim.SERVIDOR);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoPerderFome(FoodLevelChangeEvent evento) {
        if (evento.getEntity() instanceof Player jogador
                && plugin.getCorridaManager().correndo(jogador)) {
            evento.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoLargarItem(PlayerDropItemEvent evento) {
        if (!plugin.getCorridaManager().correndo(evento.getPlayer())) {
            return;
        }
        evento.setCancelled(true);
        evento.getPlayer().sendActionBar(Component.text("Nao da para largar itens no desafio.",
                NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void aoQuebrar(BlockBreakEvent evento) {
        if (protegido(evento.getBlock().getLocation(), evento.getPlayer())) {
            evento.setCancelled(true);
            evento.getPlayer().sendActionBar(Component.text("Isto faz parte de uma arena.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoColocar(BlockPlaceEvent evento) {
        if (protegido(evento.getBlock().getLocation(), evento.getPlayer())) {
            evento.setCancelled(true);
            evento.getPlayer().sendActionBar(Component.text("Isto faz parte de uma arena.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodirEntidade(EntityExplodeEvent evento) {
        evento.blockList().removeIf(bloco -> plugin.getArenaManager().em(bloco.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void aoExplodirBloco(BlockExplodeEvent evento) {
        evento.blockList().removeIf(bloco -> plugin.getArenaManager().em(bloco.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void aoNascerBicho(CreatureSpawnEvent evento) {
        if (evento.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            // E a sala dos guardioes chamando; qualquer outro motivo dentro da arena e intruso.
            return;
        }
        if (plugin.getArenaManager().em(evento.getLocation()) != null) {
            evento.setCancelled(true);
        }
    }

    /**
     * @return true se o bloco pertence a uma arena e o jogador nao pode mexer. Quem tem
     *         {@code desafios.criar} e nao esta correndo continua livre para consertar a arena
     *         na mao.
     */
    private boolean protegido(org.bukkit.Location onde, Player jogador) {
        Arena arena = plugin.getArenaManager().em(onde);
        if (arena == null) {
            return false;
        }
        if (plugin.getCorridaManager().correndo(jogador)) {
            return true;
        }
        return !jogador.hasPermission("desafios.criar");
    }
}
