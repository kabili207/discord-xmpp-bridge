package com.zyrenth.xmpp.discordbridge.iq;

import com.zyrenth.xmpp.discordbridge.BaseComponent;
import org.xmpp.packet.IQ;

import java.util.EnumSet;

/**
 * Created by kabili on 1/31/16.
 */
public interface IQHandler {

    enum Type {
        SERVER,
        USER
    }

    EnumSet<Type> getSupportedTypes();

    String getNamespace();

    IQ processIQ(BaseComponent component, IQ iq);

}
