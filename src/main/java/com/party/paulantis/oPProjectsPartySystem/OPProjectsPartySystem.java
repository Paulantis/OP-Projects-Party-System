package com.party.paulantis.oPProjectsPartySystem;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import java.nio.file.Path;

@Plugin(id = "partysystem", name = "Party System", version = "0.1.0-SNAPSHOT",
url = "https://discord.gg/SA5vA5tE", description = "---", authors = {"Paulantis"})
public class OPProjectsPartySystem {

    private final ProxyServer server;
    private final Path dataDirectory;
    private final ProxyServer proxy;
    private PartyCommand partyCommand;

    @Inject
    public OPProjectsPartySystem(ProxyServer server, @DataDirectory Path dataDirectory, ProxyServer proxy) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.proxy = proxy;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        MySqlManager.connect();
        server.getCommandManager().register("party", new PartyCommand(proxy));
        partyCommand = new PartyCommand(proxy);

        proxy.getEventManager().register(this, new PartyListener(partyCommand, proxy));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        MySqlManager.disconnect();
    }
}

