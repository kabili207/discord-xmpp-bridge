package com.zyrenth.xmpp.discordbridge.entities;

import org.xmpp.packet.JID;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordJID extends JID {

    public static final Pattern DiscordUserPattern = Pattern.compile("^u(\\d+)$");
    public static final Pattern DiscordGroupPattern = Pattern.compile("^g(\\d+)$");
    public static final Pattern DiscordJidPattern = Pattern.compile("^[ug](\\d+)$");


    private DiscordJID(String node, String domain, String resource) {
        super(node, domain, resource);
    }

    public static DiscordJID from(JID jid) {
         return new DiscordJID(jid.getNode(), jid.getDomain(), jid.getResource());
    }

    public boolean isServer() {
        return getNode() == null;
    }

    public boolean isDiscordUser() {
        if (isServer())
            return false;
        Matcher m = DiscordUserPattern.matcher(getNode());
        return m.find();
    }

    public boolean isDiscordGroup() {
        if (isServer())
            return false;
        Matcher m = DiscordGroupPattern.matcher(getNode());
        return m.find();
    }

    public String getDiscordId() {
        if (isDiscordGroup() || isDiscordUser()) {
            Matcher m = DiscordJidPattern.matcher(getNode());
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof String) {
            object = new JID((String)object);
        }
        return super.equals(object);
    }
}
