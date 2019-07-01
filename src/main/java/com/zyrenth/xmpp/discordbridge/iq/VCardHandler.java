package com.zyrenth.xmpp.discordbridge.iq;

import com.zyrenth.xmpp.discordbridge.BaseComponent;
import com.zyrenth.xmpp.discordbridge.entities.DiscordJID;
import org.dom4j.Element;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.Icon;
import org.javacord.api.entity.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import java.util.Base64;
import java.util.EnumSet;

/**
 * Created by kabili on 2/1/16.
 */
public class VCardHandler implements IQHandler {
    private static Logger logger = LoggerFactory.getLogger(VCardHandler.class);

    private static final String NAMESPACE = "vcard-temp";

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public EnumSet<Type> getSupportedTypes(){
        return EnumSet.of(Type.USER, Type.SERVER);
    }

    @Override
    public IQ processIQ(BaseComponent component, IQ iq) {
        IQ response = iq.createCopy();
        response.setType(IQ.Type.result);
        response.setTo(iq.getFrom());
        response.setFrom(iq.getTo());
        DiscordJID origTo = DiscordJID.from(iq.getTo());

        Element child = response.setChildElement("vCard", NAMESPACE);
        child.addElement("JABBERID").setText(origTo.toBareJID());

        try {
            DiscordApi jda = component.getDiscord();
            if (origTo.isDiscordUser()) {

                User user = jda.getUserById(origTo.getDiscordId()).join();
                child.addElement("FN").setText(user.getName() + "#" + user.getDiscriminator());
                child.addElement("NICKNAME").setText(user.getName());
                Icon avatar = user.getAvatar();
                Element photo = child.addElement("PHOTO");
                photo.addElement("TYPE").setText("text/png");
                photo.addElement("BINVAL").setText(Base64.getEncoder().encodeToString(avatar.asByteArray().join()));
            }
        } catch (Throwable e) {
            logger.error("Error sending vCard", e);
        }
        return response;
    }
}
