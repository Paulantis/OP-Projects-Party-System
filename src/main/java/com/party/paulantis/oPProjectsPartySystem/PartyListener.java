package com.party.paulantis.oPProjectsPartySystem;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PartyListener {
    private final PartyCommand partyCommand;
    private final ProxyServer proxy;

    public PartyListener(PartyCommand partyCommand, ProxyServer proxy) {
        this.partyCommand = partyCommand;
        this.proxy = proxy;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer targetServer = event.getServer();
        Connection connection = MySqlManager.getConnection();
        if (connection == null) return;

        String partyTable = partyCommand.getPartyTableOfPlayer(connection, player);
        if (partyTable != null && partyCommand.isPlayerOwner(player, connection, partyTable)) {
            partyCommand.handlePull(player, connection, targetServer);
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        Connection connection = MySqlManager.getConnection();
        if (connection == null) return;

        String partyTable = PartyCommand.getPartyTableOfPlayer(connection, player);
        if (partyTable == null) return;

        if (PartyCommand.isPlayerOwner(player, connection, partyTable)) {
            List<String> memberNames = new ArrayList<>();
            String query = "SELECT name FROM `" + partyTable + "` WHERE uuid != ?;";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        memberNames.add(rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            String dropSQL = "DROP TABLE IF EXISTS `" + partyTable + "`;";
            try (PreparedStatement stmt = connection.prepareStatement(dropSQL)) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            for (String memberName : memberNames) {
                Player member = proxy.getPlayer(memberName).orElse(null);
                if (member != null) {
                    member.sendMessage(Component.text("Die Party wurde beendet, da der Owner " + player.getUsername() + " den Server verlassen hat.")
                            .color(NamedTextColor.RED));
                }
            }
        } else {
            String removeSQL = "DELETE FROM `" + partyTable + "` WHERE uuid = ?;";
            try (PreparedStatement stmt = connection.prepareStatement(removeSQL)) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            List<String> memberNames = new ArrayList<>();
            String getMembersSQL = "SELECT name FROM `" + partyTable + "`;";
            try (PreparedStatement stmt = connection.prepareStatement(getMembersSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    memberNames.add(rs.getString("name"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            for (String memberName : memberNames) {
                Player member = proxy.getPlayer(memberName).orElse(null);
                if (member != null) {
                    member.sendMessage(Component.text(player.getUsername() + " hat die Party verlassen.")
                            .color(NamedTextColor.RED));
                }
            }
        }
    }
}
