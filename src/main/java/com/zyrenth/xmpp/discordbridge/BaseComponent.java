package com.zyrenth.xmpp.discordbridge;

import org.javacord.api.DiscordApi;
import org.javacord.api.listener.GloballyAttachableListener;
import org.xmpp.component.Component;
import org.xmpp.packet.JID;

import java.util.Set;

/**
 * Created by kabili on 1/31/16.
 */
public interface BaseComponent extends Component, GloballyAttachableListener {

    String getVersion();

    Identity getIdentity(JID jid);

    Set<Feature> getFeatures(JID jid);

    DiscordApi getDiscord();

    String getDomain();

}
