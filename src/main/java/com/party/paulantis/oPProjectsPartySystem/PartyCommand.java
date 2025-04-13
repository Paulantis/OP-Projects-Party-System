package com.party.paulantis.oPProjectsPartySystem;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PartyCommand implements SimpleCommand {

    private static ProxyServer proxy;

    public PartyCommand(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("Dieser Befehl kann nur von Spielern verwendet werden.")
                    .color(NamedTextColor.RED));
            return;
        }

        Player player = (Player) source;

        if (args.length == 0) {
            player.sendMessage(Component.text("Unbekannter Befehl! Bitte benutze: /party <create|leave|delete|invite|accept|decline|kick|chat|join>")
                    .color(NamedTextColor.RED));
            return;
        }

        Connection connection = MySqlManager.getConnection();
        if (connection == null) {
            player.sendMessage(Component.text("Datenbankverbindung fehlgeschlagen!")
                    .color(NamedTextColor.RED));
            return;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                handleCreate(player, connection, args);
                break;

            case "leave":
                handleLeave(player, connection);
                break;

            case "delete":
                handleDelete(player, connection);
                break;

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Bitte benutze: /party invite [username]")
                            .color(NamedTextColor.RED));
                    return;
                }
                handleInvite(player, connection, args[1]);
                break;

            case "accept":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Bitte benutze: /party accept [owner]")
                            .color(NamedTextColor.RED));
                    return;
                }
                handleAccept(player, connection, args[1]);
                break;

            case "decline":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Bitte benutze: /party decline [owner]")
                            .color(NamedTextColor.RED));
                    return;
                }
                handleDecline(player, connection, args[1]);
                break;

            case "kick":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Bitte benutze: /party kick [username]")
                            .color(NamedTextColor.RED));
                    return;
                }
                handleKick(player, connection, args[1]);
                break;

            case "chat":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Bitte benutze: /party chat [message]")
                            .color(NamedTextColor.RED));
                    return;
                }
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                handleChat(player, connection, message);
                break;

            case "join":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Bitte benutze: /party join [username]")
                            .color(NamedTextColor.RED));
                    return;
                }
                handleJoin(player, connection, args[1]);
                break;

            case "pull":
                Optional<RegisteredServer> targetServerOpt = player.getCurrentServer().map(conn -> conn.getServer());
                if (targetServerOpt.isEmpty()) {
                    player.sendMessage(Component.text("Du befindest dich momentan auf keinem Server.")
                            .color(NamedTextColor.RED));
                    return;
                }
                handlePull(player, connection, targetServerOpt.get());
                break;
            default:
                player.sendMessage(Component.text("Unbekannter Befehl! Bitte benutze: /party <create|leave|delete|invite|accept|decline|kick|chat|join>")
                        .color(NamedTextColor.RED));
                break;
        }
    }


    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> completions = new ArrayList<>();

        List<String> options = List.of("create", "leave", "delete", "invite", "accept", "decline", "kick", "chat", "join");

        if (args.length == 0) {
            completions.addAll(options);
        } else if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String option : options) {
                if (option.startsWith(partial)) {
                    completions.add(option);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                List<String> visibilityOptions = List.of("public", "private");
                String partial = args[1].toLowerCase();
                for (String vis : visibilityOptions) {
                    if (vis.startsWith(partial)) {
                        completions.add(vis);
                    }
                }
            }
            if (args[0].equalsIgnoreCase("invite")) {
                for (Player p : proxy.getAllPlayers()) {
                    completions.add(p.getUsername());
                }
            }
            if (args[0].equalsIgnoreCase("kick")) {
                for (Player p : proxy.getAllPlayers()) {
                    completions.add(p.getUsername());
                }
            }
            if (args[0].equalsIgnoreCase("join")) {
                for (Player p : proxy.getAllPlayers()) {
                    completions.add(p.getUsername());
                }
            }

        }

        return completions;
    }

    private void handleCreate(Player player, Connection connection, String[] args) {
        String existingParty = getPartyTableOfPlayer(connection, player);
        if (existingParty != null) {
            player.sendMessage(Component.text("Du bist bereits Mitglied in einer Party und kannst daher keine eigene erstellen!")
                    .color(NamedTextColor.RED));
            return;
        }

        String visibility = "public";
        if (args.length >= 2) {
            String input = args[1].toLowerCase();
            if (input.equals("public") || input.equals("private")) {
                visibility = input;
            } else {
                player.sendMessage(Component.text("Ungültiger Wert. Nutze entweder 'public' oder 'private'. Sichtbarkeit wird auf 'public' gesetzt.")
                        .color(NamedTextColor.YELLOW));
            }
        }

        String tableName = "party_" + player.getUsername();
        String createTableSQL = "CREATE TABLE `" + tableName + "` ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "uuid VARCHAR(36) NOT NULL, "
                + "name VARCHAR(16) NOT NULL, "
                + "role VARCHAR(16) NOT NULL, "
                + "visibility VARCHAR(8) NOT NULL"
                + ");";
        try (PreparedStatement createStmt = connection.prepareStatement(createTableSQL)) {
            createStmt.executeUpdate();
        } catch (SQLException e) {
            player.sendMessage(Component.text("Fehler beim Erstellen der Party!")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        String insertSQL = "INSERT INTO `" + tableName + "` (uuid, name, role, visibility) VALUES (?, ?, ?, ?);";
        try (PreparedStatement insertStmt = connection.prepareStatement(insertSQL)) {
            insertStmt.setString(1, player.getUniqueId().toString());
            insertStmt.setString(2, player.getUsername());
            insertStmt.setString(3, "Owner");
            insertStmt.setString(4, visibility);
            insertStmt.executeUpdate();
        } catch (SQLException e) {
            player.sendMessage(Component.text("Fehler beim Eintragen des Owners!")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }
        player.sendMessage(Component.text("Party erfolgreich erstellt! (" + visibility + ")")
                .color(NamedTextColor.GREEN));
    }


    private void handleLeave(Player player, Connection connection) {
        String tableName = getPartyTableOfPlayer(connection, player);
        if (tableName == null) {
            player.sendMessage(Component.text("Du bist in keiner Party eingetragen.")
                    .color(NamedTextColor.RED));
            return;
        }
        String deleteRowSQL = "DELETE FROM `" + tableName + "` WHERE uuid = ?;";
        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteRowSQL)) {
            deleteStmt.setString(1, player.getUniqueId().toString());
            int affected = deleteStmt.executeUpdate();
            if (affected == 0) {
                player.sendMessage(Component.text("Du bist in dieser Party nicht eingetragen.")
                        .color(NamedTextColor.RED));
                return;
            }
        } catch (SQLException e) {
            player.sendMessage(Component.text("Fehler beim Verlassen der Party!")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        if (getPartyMemberCount(connection, tableName) == 0) {
            dropPartyTable(connection, tableName);
            player.sendMessage(Component.text("Du hast die Party verlassen. Da keine Mitglieder mehr vorhanden sind, wurde die Party automatisch gelöscht.")
                    .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Du hast die Party verlassen!")
                    .color(NamedTextColor.GREEN));
        }
    }

    private void handleDelete(Player player, Connection connection) {
        String tableName = getPartyTableOfPlayer(connection, player);
        if (tableName == null) {
            player.sendMessage(Component.text("Du bist in keiner Party, die du löschen könntest.")
                    .color(NamedTextColor.RED));
            return;
        }
        if (!isPlayerOwner(player, connection, tableName)) {
            player.sendMessage(Component.text("Nur der Party Owner kann die Party löschen!")
                    .color(NamedTextColor.RED));
            return;
        }
        if (dropPartyTable(connection, tableName)) {
            player.sendMessage(Component.text("Party erfolgreich gelöscht!")
                    .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Fehler beim Löschen der Party!")
                    .color(NamedTextColor.RED));
        }
    }

    private void handleInvite(Player owner, Connection connection, String targetName) {
        String tableName = "party_" + owner.getUsername();
        if (!checkIfPartyExists(connection, tableName)) {
            owner.sendMessage(Component.text("Du hast keine offene Party, in die du Spieler einladen könntest.")
                    .color(NamedTextColor.RED));
            return;
        }
        if (!isPlayerOwner(owner, connection, tableName)) {
            owner.sendMessage(Component.text("Nur der Party Owner kann Einladungen verschicken!")
                    .color(NamedTextColor.RED));
            return;
        }

        Player target = proxy.getPlayer(targetName).orElse(null);


        if (target == null) {
            owner.sendMessage(Component.text("Spieler " + targetName + " wurde nicht gefunden.")
                    .color(NamedTextColor.RED));
            return;
        }

        Component acceptButton = Component.text("[Beitreten]")
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/party accept " + owner.getUsername()));
        Component declineButton = Component.text("[Ablehnen]")
                .color(NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/party decline " + owner.getUsername()));

        Component inviteMessage = Component.text("Du wurdest zu einer Party von " + owner.getUsername() + " eingeladen! ")
                .color(NamedTextColor.GOLD)
                .append(acceptButton)
                .append(Component.text(" "))
                .append(declineButton);

        target.sendMessage(inviteMessage);
        owner.sendMessage(Component.text("Einladung an " + target.getUsername() + " wurde verschickt.")
                .color(NamedTextColor.GREEN));
    }

    private void handleAccept(Player invitee, Connection connection, String ownerName) {
        String tableName = "party_" + ownerName;
        if (!checkIfPartyExists(connection, tableName)) {
            invitee.sendMessage(Component.text("Die Party von " + ownerName + " existiert nicht.")
                    .color(NamedTextColor.RED));
            return;
        }

        String visibility = null;
        String query = "SELECT visibility FROM `" + tableName + "` WHERE role = 'Owner' LIMIT 1;";
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                visibility = rs.getString("visibility");
            }
        } catch (SQLException e) {
            invitee.sendMessage(Component.text("Fehler beim Abrufen der Sichtbarkeit der Party.")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        if (visibility == null) {
            invitee.sendMessage(Component.text("Fehler: Sichtbarkeit der Party konnte nicht ermittelt werden.")
                    .color(NamedTextColor.RED));
            return;
        }

        String insertSQL = "INSERT INTO `" + tableName + "` (uuid, name, role, visibility) VALUES (?, ?, ?, ?);";
        try (PreparedStatement insertStmt = connection.prepareStatement(insertSQL)) {
            insertStmt.setString(1, invitee.getUniqueId().toString());
            insertStmt.setString(2, invitee.getUsername());
            insertStmt.setString(3, "Member");
            insertStmt.setString(4, visibility);
            insertStmt.executeUpdate();
        } catch (SQLException e) {
            invitee.sendMessage(Component.text("Fehler beim Beitreten der Party!")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        invitee.sendMessage(Component.text("Du bist der Party von " + ownerName + " beigetreten!")
                .color(NamedTextColor.GREEN));

        Player owner = proxy.getPlayer(ownerName).orElse(null);
        if (owner != null) {
            owner.sendMessage(Component.text(invitee.getUsername() + " hat deine Einladung angenommen!")
                    .color(NamedTextColor.GREEN));
        }
    }


    private void handleDecline(Player invitee, Connection connection, String ownerName) {
        invitee.sendMessage(Component.text("Du hast die Einladung von " + ownerName + " abgelehnt.")
                .color(NamedTextColor.RED));
        Player owner = proxy.getPlayer(ownerName).orElse(null);
        if (owner != null) {
            owner.sendMessage(Component.text(invitee.getUsername() + " hat deine Einladung abgelehnt!")
                    .color(NamedTextColor.RED));
        }
    }

    private void handleKick(Player owner, Connection connection, String targetName) {
        String tableName = getPartyTableOfPlayer(connection, owner);
        if (tableName == null) {
            owner.sendMessage(Component.text("Du bist in keiner Party.").color(NamedTextColor.RED));
            return;
        }
        if (!isPlayerOwner(owner, connection, tableName)) {
            owner.sendMessage(Component.text("Nur der Party Owner kann Spieler kicken!")
                    .color(NamedTextColor.RED));
            return;
        }

        String query = "SELECT * FROM `" + tableName + "` WHERE name = ? AND role != 'Owner';";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, targetName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    owner.sendMessage(Component.text("Spieler " + targetName + " ist nicht in deiner Party.")
                            .color(NamedTextColor.RED));
                    return;
                }
            }
        } catch (SQLException e) {
            owner.sendMessage(Component.text("Fehler beim Überprüfen der Party-Mitglieder.")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        String deleteSQL = "DELETE FROM `" + tableName + "` WHERE name = ?;";
        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSQL)) {
            deleteStmt.setString(1, targetName);
            deleteStmt.executeUpdate();
        } catch (SQLException e) {
            owner.sendMessage(Component.text("Fehler beim Kicken des Spielers!")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        owner.sendMessage(Component.text("Spieler " + targetName + " wurde aus deiner Party gekickt.")
                .color(NamedTextColor.GREEN));

        Player target = proxy.getPlayer(targetName).orElse(null);
        if (target != null) {
            target.sendMessage(Component.text("Du wurdest aus der Party von " + owner.getUsername() + " gekickt.")
                    .color(NamedTextColor.RED));
        }
    }

    private void handleChat(Player sender, Connection connection, String message) {
        String tableName = getPartyTableOfPlayer(connection, sender);
        if (tableName == null) {
            sender.sendMessage(Component.text("Du bist in keiner Party.").color(NamedTextColor.RED));
            return;
        }
        String query = "SELECT name FROM `" + tableName + "`;";
        List<String> memberNames = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                memberNames.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            sender.sendMessage(Component.text("Fehler beim Senden der Nachricht.").color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        for (String memberName : memberNames) {
            Player member = proxy.getPlayer(memberName).orElse(null);
            if (member != null) {
                member.sendMessage(Component.text("[Party] " + sender.getUsername() + ": " + message)
                        .color(NamedTextColor.YELLOW));
            }
        }
    }

    private void handleJoin(Player joiner, Connection connection, String ownerName) {
        String tableName = "party_" + ownerName;
        if (!checkIfPartyExists(connection, tableName)) {
            joiner.sendMessage(Component.text("Die Party von " + ownerName + " existiert nicht.")
                    .color(NamedTextColor.RED));
            return;
        }

        String query = "SELECT visibility FROM `" + tableName + "` WHERE role = 'Owner' LIMIT 1;";
        String visibility = null;
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                visibility = rs.getString("visibility");
            }
        } catch (SQLException e) {
            joiner.sendMessage(Component.text("Fehler beim Prüfen der Party-Sichtbarkeit.")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        if (visibility == null) {
            joiner.sendMessage(Component.text("Die Party von " + ownerName + " konnte nicht ermittelt werden.")
                    .color(NamedTextColor.RED));
            return;
        }

        if (!visibility.equalsIgnoreCase("public")) {
            joiner.sendMessage(Component.text("Die Party von " + ownerName + " ist privat. Du benötigst eine Einladung!")
                    .color(NamedTextColor.RED));
            return;
        }

        String checkSQL = "SELECT * FROM `" + tableName + "` WHERE uuid = ?;";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSQL)) {
            checkStmt.setString(1, joiner.getUniqueId().toString());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    joiner.sendMessage(Component.text("Du bist bereits in dieser Party.")
                            .color(NamedTextColor.RED));
                    return;
                }
            }
        } catch (SQLException e) {
            joiner.sendMessage(Component.text("Fehler beim Prüfen deiner Party-Mitgliedschaft.")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        String insertSQL = "INSERT INTO `" + tableName + "` (uuid, name, role, visibility) VALUES (?, ?, ?, ?);";
        try (PreparedStatement insertStmt = connection.prepareStatement(insertSQL)) {
            insertStmt.setString(1, joiner.getUniqueId().toString());
            insertStmt.setString(2, joiner.getUsername());
            insertStmt.setString(3, "Member");
            insertStmt.setString(4, visibility);
            insertStmt.executeUpdate();
        } catch (SQLException e) {
            joiner.sendMessage(Component.text("Fehler beim Beitreten der Party!")
                    .color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }
        joiner.sendMessage(Component.text("Du bist der Party von " + ownerName + " beigetreten!")
                .color(NamedTextColor.GREEN));

        Player owner = proxy.getPlayer(ownerName).orElse(null);
        if (owner != null) {
            owner.sendMessage(Component.text(joiner.getUsername() + " ist der Party beigetreten!")
                    .color(NamedTextColor.GREEN));
        }
    }

    public void handlePull(Player owner, Connection connection, RegisteredServer targetServer) {
        String tableName = getPartyTableOfPlayer(connection, owner);
        if (tableName == null) {
            owner.sendMessage(Component.text("Du bist in keiner Party eingetragen.").color(NamedTextColor.RED));
            return;
        }
        if (!isPlayerOwner(owner, connection, tableName)) {
            owner.sendMessage(Component.text("Nur der Party Owner kann die Party ziehen (pull)!").color(NamedTextColor.RED));
            return;
        }

        String query = "SELECT name FROM `" + tableName + "` WHERE uuid != ?;";
        List<String> memberNames = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, owner.getUniqueId().toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memberNames.add(rs.getString("name"));
                }
            }
        } catch (SQLException e) {
            owner.sendMessage(Component.text("Fehler beim Abrufen der Party-Mitglieder.").color(NamedTextColor.RED));
            e.printStackTrace();
            return;
        }

        for (String memberName : memberNames) {
            Player member = proxy.getPlayer(memberName).orElse(null);
            if (member != null) {
                member.createConnectionRequest(targetServer).fireAndForget();
                member.sendMessage(Component.text("Du wurdest von " + owner.getUsername() + " in seine Party gezogen.")
                        .color(NamedTextColor.GREEN));
            }
        }
    }



    public static String getPartyTableOfPlayer(Connection connection, Player player) {
        List<String> partyTables = new ArrayList<>();
        String tablePatternSQL = "SHOW TABLES LIKE 'party\\_%'";
        try (PreparedStatement stmt = connection.prepareStatement(tablePatternSQL);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                partyTables.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }

        for (String table : partyTables) {
            String query = "SELECT * FROM `" + table + "` WHERE uuid = ?;";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return table;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private boolean checkIfPartyExists(Connection connection, String tableName) {
        String checkSQL = "SHOW TABLES LIKE ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSQL)) {
            checkStmt.setString(1, tableName);
            ResultSet rs = checkStmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private int getPartyMemberCount(Connection connection, String tableName) {
        String countSQL = "SELECT COUNT(*) AS count FROM `" + tableName + "`;";
        try (PreparedStatement countStmt = connection.prepareStatement(countSQL);
             ResultSet rs = countStmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private boolean dropPartyTable(Connection connection, String tableName) {
        String dropSQL = "DROP TABLE `" + tableName + "`;";
        try (PreparedStatement dropStmt = connection.prepareStatement(dropSQL)) {
            dropStmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isPlayerOwner(Player player, Connection connection, String tableName) {
        String roleQuery = "SELECT role FROM `" + tableName + "` WHERE uuid = ?;";
        try (PreparedStatement roleStmt = connection.prepareStatement(roleQuery)) {
            roleStmt.setString(1, player.getUniqueId().toString());
            ResultSet rs = roleStmt.executeQuery();
            if (rs.next()) {
                String role = rs.getString("role");
                return role.equalsIgnoreCase("Owner");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}




