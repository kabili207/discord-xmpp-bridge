package com.zyrenth.xmpp.discordbridge;

import org.xmpp.packet.JID;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JIDExt extends JID {

    public static final Pattern DiscordUserPattern = Pattern.compile("^u(\\d+)$");
    public static final Pattern DiscordGroupPattern = Pattern.compile("^g(\\d+)$");
    public static final Pattern DiscordJidPattern = Pattern.compile("^[ug](\\d+)$");


    private JIDExt(String node, String domain, String resource) {
        super(node, domain, resource);
    }

    public static JIDExt from(JID jid) {
         return new JIDExt(jid.getNode(), jid.getDomain(), jid.getResource());
    }

    public boolean isDiscordUser() {
        Matcher m = DiscordUserPattern.matcher(getNode());
        return m.find();
    }

    public boolean isDiscordGroup() {
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
