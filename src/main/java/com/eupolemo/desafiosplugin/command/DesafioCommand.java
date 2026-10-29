package com.eupolemo.desafiosplugin.command;

import com.eupolemo.desafiosplugin.DesafiosPlugin;
import com.eupolemo.desafiosplugin.arena.Arena;
import com.eupolemo.desafiosplugin.corrida.Corrida;
import com.eupolemo.desafiosplugin.corrida.CorridaManager;
import com.eupolemo.desafiosplugin.corrida.Recordes;
import com.eupolemo.desafiosplugin.desafio.Desafio;
import com.eupolemo.desafiosplugin.gerador.Gerador;
import com.eupolemo.desafiosplugin.gerador.Geradores;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DesafioCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMANDOS = List.of("info", "criar", "entrar", "sair",
            "remover", "ranking", "reload");

    private final DesafiosPlugin plugin;

    public DesafioCommand(DesafiosPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("desafios.use")) {
            sender.sendMessage(Component.text("Sem permissao.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            listar(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "info" -> info(sender, args, label);
            case "criar" -> criar(sender, args, label);
            case "remover" -> remover(sender, args, label);
            case "entrar" -> entrar(sender, args, label);
            case "sair" -> sair(sender);
            case "ranking" -> ranking(sender, args, label);
            case "reload" -> recarregar(sender);
            default -> {
                sender.sendMessage(Component.text("Nao conheco '" + sub + "'. Use: "
                        + String.join(", ", SUBCOMANDOS), NamedTextColor.YELLOW));
                yield true;
            }
        };
    }

    private void listar(CommandSender sender) {
        if (plugin.getDesafioManager().todos().isEmpty()) {
            sender.sendMessage(Component.text("Nenhum desafio configurado.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("Desafios:", NamedTextColor.GOLD));
        for (Desafio desafio : plugin.getDesafioManager().todos()) {
            Arena arena = plugin.getArenaManager().porId(desafio.id());
            sender.sendMessage(Component.text("  " + desafio.id() + " — " + desafio.nome()
                    + " (" + desafio.dificuldade().rotulo() + ", " + desafio.etapas() + " etapas)"
                    + (arena == null ? " · sem arena" : " · pronta"),
                    arena == null ? NamedTextColor.GRAY : NamedTextColor.WHITE));
        }
    }

    private boolean info(CommandSender sender, String[] args, String label) {
        Desafio desafio = exigirDesafio(sender, args, label, "info");
        if (desafio == null) {
            return true;
        }
        sender.sendMessage(Component.text(desafio.nome(), desafio.dificuldade().cor()));
        sender.sendMessage(Component.text("  dificuldade: " + desafio.dificuldade().rotulo()
                + " · etapas: " + desafio.etapas()
                + " · tempo: " + (desafio.temTempoLimite()
                ? desafio.tempoLimite() + "s" : "sem limite"), NamedTextColor.GRAY));

        String tipos = desafio.tipos().isEmpty() ? "todos" : String.join(", ", desafio.tipos());
        sender.sendMessage(Component.text("  geradores: " + tipos, NamedTextColor.GRAY));

        List<String> premio = new ArrayList<>();
        if (desafio.premio().xp() > 0) {
            premio.add(desafio.premio().xp() + " xp");
        }
        for (ItemStack item : desafio.premio().itens()) {
            premio.add(item.getAmount() + "x " + item.getType().name().toLowerCase(Locale.ROOT));
        }
        sender.sendMessage(Component.text("  premio: "
                + (premio.isEmpty() ? "nenhum" : String.join(", ", premio)), NamedTextColor.GRAY));

        Arena arena = plugin.getArenaManager().porId(desafio.id());
        if (arena == null) {
            sender.sendMessage(Component.text("  arena: nao construida (/" + label + " criar "
                    + desafio.id() + ")", NamedTextColor.GRAY));
        } else {
            List<String> nomes = new ArrayList<>();
            for (String tipo : arena.tipos()) {
                Gerador gerador = Geradores.porId(tipo);
                nomes.add(gerador != null ? gerador.nome() : tipo);
            }
            sender.sendMessage(Component.text("  arena: seed " + arena.seed() + " em "
                    + arena.x0() + "," + arena.y0() + "," + arena.z0(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  salas: " + String.join(" > ", nomes),
                    NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean criar(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("desafios.criar")) {
            sender.sendMessage(Component.text("Sem permissao para construir arenas.",
                    NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Esse comando precisa de um jogador: a arena nasce "
                    + "a frente de quem chamou.", NamedTextColor.RED));
            return true;
        }
        Desafio desafio = exigirDesafio(sender, args, label, "criar");
        if (desafio == null) {
            return true;
        }
        if (plugin.getArenaManager().porId(desafio.id()) != null) {
            sender.sendMessage(Component.text("Ja existe uma arena de '" + desafio.id()
                    + "'. Use /" + label + " remover " + desafio.id() + " antes.",
                    NamedTextColor.RED));
            return true;
        }

        Long seed = null;
        if (args.length > 2) {
            try {
                seed = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                // Seed escrita como palavra continua servindo: vira numero pelo hash dela.
                seed = (long) args[2].hashCode();
            }
        }

        Arena arena = plugin.getArenaManager().planejar(desafio, jogador.getLocation(), seed);
        World mundo = jogador.getWorld();
        String motivo = plugin.getArenaManager().motivoParaNaoCaber(arena, mundo);
        if (motivo != null) {
            sender.sendMessage(Component.text("Nao da para construir aqui — " + motivo,
                    NamedTextColor.RED));
            return true;
        }

        // Registrada antes de a construcao terminar: se o servidor cair no meio, o /desafio
        // remover ainda encontra a arena e desfaz o que ja tinha sido escrito.
        plugin.getArenaManager().registrar(arena);
        plugin.getArenaBuilder().construir(arena, desafio, mundo, sender, null);
        return true;
    }

    private boolean remover(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("desafios.criar")) {
            sender.sendMessage(Component.text("Sem permissao para remover arenas.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /" + label + " remover <id>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        Arena arena = plugin.getArenaManager().porId(id);
        if (arena == null) {
            sender.sendMessage(Component.text("Nao ha arena de '" + id + "'.",
                    NamedTextColor.RED));
            return true;
        }

        // Ninguem pode continuar correndo numa arena que esta sendo desmontada bloco a bloco.
        for (Corrida corrida : plugin.getCorridaManager().emAndamento()) {
            if (corrida.arena().id().equals(id)) {
                plugin.getCorridaManager().encerrar(corrida.jogador(), CorridaManager.Fim.SERVIDOR);
            }
        }
        plugin.getArenaBuilder().remover(id, sender, () -> plugin.getArenaManager().esquecer(id));
        return true;
    }

    private boolean entrar(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Esse comando precisa de um jogador.",
                    NamedTextColor.RED));
            return true;
        }
        Desafio desafio = exigirDesafio(sender, args, label, "entrar");
        if (desafio == null) {
            return true;
        }
        Arena arena = plugin.getArenaManager().porId(desafio.id());
        if (arena == null) {
            sender.sendMessage(Component.text("A arena de '" + desafio.id() + "' ainda nao foi "
                    + "construida. Peca a um op: /" + label + " criar " + desafio.id(),
                    NamedTextColor.RED));
            return true;
        }
        // As checagens de entrada moram no CorridaManager, para o botao do portico e este
        // comando obedecerem exatamente as mesmas regras.
        plugin.getCorridaManager().tentarIniciar(jogador, desafio, arena);
        return true;
    }

    private boolean sair(CommandSender sender) {
        if (!(sender instanceof Player jogador)) {
            sender.sendMessage(Component.text("Esse comando precisa de um jogador.",
                    NamedTextColor.RED));
            return true;
        }
        if (plugin.getCorridaManager().encerrar(jogador, CorridaManager.Fim.DESISTIU)) {
            return true;
        }
        // Escotilha: as salas sao caixas fechadas, entao quem cai dentro de uma arena sem corrida
        // — por ter deslogado no meio de uma, por exemplo — nao teria como sair andando.
        if (plugin.getArenaManager().em(jogador.getLocation()) != null) {
            jogador.teleport(jogador.getWorld().getSpawnLocation());
            sender.sendMessage(Component.text("Voce nao estava correndo, mas estava dentro de uma "
                    + "arena. Tirei voce de la.", NamedTextColor.YELLOW));
            return true;
        }
        sender.sendMessage(Component.text("Voce nao esta em nenhum desafio.", NamedTextColor.GRAY));
        return true;
    }

    private boolean ranking(CommandSender sender, String[] args, String label) {
        Desafio desafio = exigirDesafio(sender, args, label, "ranking");
        if (desafio == null) {
            return true;
        }
        List<Recordes.Marca> marcas = plugin.getRecordes().ranking(desafio.id(), 10);
        if (marcas.isEmpty()) {
            sender.sendMessage(Component.text("Ninguem venceu " + desafio.nome() + " ainda.",
                    NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("Ranking de " + desafio.nome() + ":",
                NamedTextColor.GOLD));
        int posicao = 1;
        for (Recordes.Marca marca : marcas) {
            sender.sendMessage(Component.text("  " + posicao++ + ". " + marca.nome() + " — "
                    + Corrida.formatarTempo(marca.tempoMs()) + " (" + marca.mortes() + " quedas)",
                    NamedTextColor.WHITE));
        }
        return true;
    }

    private boolean recarregar(CommandSender sender) {
        if (!sender.hasPermission("desafios.reload")) {
            sender.sendMessage(Component.text("Sem permissao para recarregar.",
                    NamedTextColor.RED));
            return true;
        }
        plugin.reloadConfig();
        plugin.getDesafioManager().carregar();
        sender.sendMessage(Component.text("Config recarregada: "
                + plugin.getDesafioManager().todos().size() + " desafio(s). As arenas ja "
                + "construidas continuam com as salas que sortearam.", NamedTextColor.GREEN));
        return true;
    }

    private Desafio exigirDesafio(CommandSender sender, String[] args, String label, String sub) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /" + label + " " + sub + " <id>",
                    NamedTextColor.YELLOW));
            return null;
        }
        Desafio desafio = plugin.getDesafioManager().porId(args[1]);
        if (desafio == null) {
            sender.sendMessage(Component.text("Desafio desconhecido: " + args[1] + ". Conhecidos: "
                    + String.join(", ", plugin.getDesafioManager().ids()), NamedTextColor.RED));
        }
        return desafio;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command, @NotNull String label,
                                                @NotNull String[] args) {
        if (args.length == 1) {
            return filtrar(SUBCOMANDOS, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("remover")) {
                return filtrar(new ArrayList<>(plugin.getArenaManager().todas().stream()
                        .map(Arena::id).toList()), args[1]);
            }
            if (List.of("info", "criar", "entrar", "ranking").contains(sub)) {
                return filtrar(plugin.getDesafioManager().ids(), args[1]);
            }
        }
        return List.of();
    }

    private List<String> filtrar(List<String> opcoes, String prefixo) {
        String alvo = prefixo.toLowerCase(Locale.ROOT);
        List<String> saida = new ArrayList<>();
        for (String opcao : opcoes) {
            if (opcao.startsWith(alvo)) {
                saida.add(opcao);
            }
        }
        return saida;
    }
}
