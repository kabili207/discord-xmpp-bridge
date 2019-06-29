package com.zyrenth.xmpp.discordbridge;

import com.zyrenth.xmpp.discordbridge.iq.IQHandler;
import com.zyrenth.xmpp.discordbridge.iq.IQProcessor;
import org.apache.commons.io.FileUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.dom4j.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.exception.CannotMessageUserException;
import org.javacord.api.listener.message.MessageCreateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.AbstractComponent;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.*;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletionException;

/**
 * Created by kabili on 1/30/16.
 */
public class BridgeComponent extends AbstractComponent implements BaseComponent, MessageCreateListener {

    public static final Logger logger = LoggerFactory.getLogger(BridgeComponent.class);

    private static final String ContactResource = "user";

    static Properties prop = new Properties();
    private JID mJid = null;
    private DiscordApi discord = null;

    public BridgeComponent(DiscordApi discord) {
        this.discord = discord;
    }

    public DiscordApi getDiscord() {
        return discord;
    }

    public String getVersion() {
        String version = this.getClass().getPackage().getImplementationVersion();
        if (version == null)
            version = "1.0";
        return version;
    }

    public String getName() {
        return ("Discord Bridge");
    }

    public String getDescription() {
        return ("Echos your message back to you in upper case");
    }

    public Identity getIdentity(JID rawJid) {
        JIDExt jid = JIDExt.from(rawJid);
        Identity ident = null;
        if (jid.equals(getDomain())) {
            ident = new Identity("gateway", "discord", getName());
        } else if (jid.isDiscordUser()) {
            ident = new Identity("client", "pc", getName());
        } else if (jid.isDiscordGroup()) {
            ident = new Identity("conference", "text");
        }
        return ident;
    }

    public Set<Feature> getFeatures(JID rawJid) {
        Set<Feature> features = new TreeSet<Feature>();
        JIDExt jid = JIDExt.from(rawJid);
        String domain = getDomain();
        IQHandler.Type probeType = jid.equals(domain) ? IQHandler.Type.SERVER : IQHandler.Type.USER;

        features.add(new Feature(NAMESPACE_DISCO_INFO));
        features.add(new Feature(NAMESPACE_XMPP_PING));
        features.add(new Feature(NAMESPACE_LAST_ACTIVITY));
        features.add(new Feature(NAMESPACE_ENTITY_TIME));

        if (probeType == IQHandler.Type.USER) {
            features.add(new Feature(EntityCapabilities.NAMESPACE)); // XEP-0115
            features.add(new Feature("urn:xmpp:receipts")); // XEP-0184
        }

        for (final String feature : IQProcessor.getSupportedFeatures(probeType)) {
            features.add(new Feature(feature));
        }
        for (final String feature : discoInfoFeatureNamespaces()) {
            features.add(new Feature(feature));
        }

        return features;
    }

    @Override
    protected IQ handleDiscoInfo(IQ iq) {
        final IQ replyPacket = IQ.createResultIQ(iq);
        final Element responseElement = replyPacket.setChildElement("query", NAMESPACE_DISCO_INFO);

        JIDExt toJid = JIDExt.from(iq.getTo());

        logger.debug("disco request from " + iq.getFrom().toString() + " for " + toJid.toString());

        MessageDigest digest = null;

        if (!toJid.isDiscordUser()) {
            try {
                digest = MessageDigest.getInstance("sha-1");
                String hash = EntityCapabilities.getVerificationString(this, iq.getTo(), digest);
                responseElement.addAttribute("node", "http://" + getDomain() + "#" + hash);

            } catch (NoSuchAlgorithmException e) {
                logger.error("Error creating entity capability hash", e);
            }
        }
        Identity ident = getIdentity(toJid);

        responseElement.addElement("identity")
                .addAttribute("category", ident.getCategory())
                .addAttribute("type", ident.getType())
                .addAttribute("name", ident.getName());

        // features
        for (final Feature feature : getFeatures(toJid)) {
            responseElement.addElement("feature").addAttribute("var", feature.getVar());
        }
        return replyPacket;

    }

    @Override
    public void postComponentStart() {
        super.postComponentStart();
        try {
            prop.load(BridgeComponent.class.getClassLoader().getResourceAsStream("application.properties"));

            mJid = getJID();

            final String projectId = prop.getProperty("projectId");
            final String password = prop.getProperty("password");

            //ccsClient = CcsClient.prepareClient(projectId, password, false);
            //ccsClient.setPaylodProcessor(this);
            //ccsClient.connect();

            MySqlDao dao = MySqlDao.getInstance();
            //Map<Integer, String> users = dao.getAllUsers();

            //String domain = getDomain();

            //for (Map.Entry<Integer, String> user : users.entrySet()) {
            //    Map<String, String> contacts = dao.getSubscribedContacts(user.getValue());
            //
            //    for (Map.Entry<String, String> contact : contacts.entrySet()) {
            //
            //        sendPresence(null, contact.getKey() + "@" + domain + "/" + ContactResource, user.getValue(), contact.getValue());
            //   }
            //}

        } catch (Throwable e) {
            logger.error("Error starting", e);
        }
    }

    @Override
    public void preComponentShutdown() {

        try {
            MySqlDao dao = MySqlDao.getInstance();
            Map<Integer, String> users = dao.getAllUsers();
            logger.debug("Notifying users");
            String domain = getDomain();
            if(domain != null) {

                for (Map.Entry<Integer, String> user : users.entrySet()) {
                    Map<String, String> contacts = dao.getSubscribedContacts(user.getValue());

                    for (Map.Entry<String, String> contact : contacts.entrySet()) {
                        String from = contact.getKey() + "@" + domain + "/" + ContactResource;
                        sendPresence(Presence.Type.unavailable, from, user.getValue(), contact.getValue());
                    }
                }
                Thread.sleep(1000);
            }
            //if (ccsClient != null) {
            //    ccsClient.disconnect();
            //}
        } catch (Throwable t) {
            logger.error("Error shutting down", t);
        }
    }

    @Override
    protected IQ handleIQGet(IQ iq) throws Exception {

        return IQProcessor.processIQ(this, iq);
    }

    @Override
    protected IQ handleIQSet(IQ iq) throws Exception {

        return IQProcessor.processIQ(this, iq);
    }

    protected void addCapsElement(Presence presence) {
        MessageDigest digest = null;

        try {
            digest = MessageDigest.getInstance("sha-1");
            String hash = EntityCapabilities.getVerificationString(this, presence.getFrom(), digest);
            Element child = presence.addChildElement("c", "http://jabber.org/protocol/caps");
            child.addAttribute("hash", "sha-1");
            child.addAttribute("node", "http://"+ getDomain());
            child.addAttribute("ver", hash);

        } catch (NoSuchAlgorithmException e) {
            logger.error("Error adding caps", e);
        }
    }

    protected void addNickElement(Message message, String nick) {
        Element nickElem = message.addChildElement("nick", "http://jabber.org/protocol/nick");
        nickElem.setText(nick);
    }

    protected void addNickElement(Presence presence, String nick) {
        Element nickElem = presence.addChildElement("nick", "http://jabber.org/protocol/nick");
        nickElem.setText(nick);
    }

    protected void sendPresence(Presence.Type type, JID from, JID to, String name) throws ComponentException {
        sendPresence(type, from.toString(), to.toString(), name);
    }

    protected void sendPresence(Presence.Type type, String from, String to, String name) throws ComponentException {
        Presence response = new Presence();
        response.setTo(to);
        response.setFrom(from);
        response.setType(type);
        addCapsElement(response);
        if (name != null && !name.isEmpty())
            addNickElement(response, name);
        send(response);
    }

    @Override
    protected void handlePresence(Presence presence) {


        Presence.Type type = presence.getType();

        MySqlDao dao = MySqlDao.getInstance();
        try {

            String origTo = presence.getTo().toBareJID();

            if (type.equals(Presence.Type.subscribe)) {

                //dao.updateContactSubscription(presence.getFrom(), phoneNum, true);
                //String name = dao.getContactName(presence.getFrom(), phoneNum);

                Presence response = new Presence();
                response.setTo(presence.getFrom().toBareJID());
                response.setFrom(presence.getTo().toBareJID());
                response.setType(Presence.Type.subscribed);
                send(response);
                sendPresence(null, presence.getTo(), presence.getFrom(), null);

            } else if (type.equals(Presence.Type.probe)) {

                //String name = dao.getContactName(presence.getFrom(), phoneNum);

                JID from = presence.getTo();
                String resource = presence.getTo().getResource();
                if (resource == null || resource.isEmpty())
                    from = new JID(origTo + "/" + ContactResource);

                sendPresence(null, from, presence.getFrom(), null);

            } else if (type.equals(Presence.Type.unsubscribe)) {

                //dao.updateContactSubscription(presence.getFrom(), phoneNum, false);

                Presence response = new Presence();
                response.setTo(presence.getFrom().toBareJID());
                response.setFrom(presence.getTo().toBareJID());
                response.setType(Presence.Type.unsubscribed);
                send(response);

            }


        } catch (Exception e) {
            logger.error("Error handling presence", e);
        }

    }

    @Override
    protected void handleMessage(Message message) {


        JIDExt origTo = JIDExt.from(message.getTo());
        MySqlDao dao = MySqlDao.getInstance();

        if (origTo.isDiscordUser()) {

            try {
                User user = discord.getUserById(origTo.getDiscordId()).join();
                Optional<PrivateChannel> privateChannel = user.getPrivateChannel();

                PrivateChannel channel = null;
                if (privateChannel.isPresent()) {
                    channel = privateChannel.get();
                } else {
                    channel = user.openPrivateChannel().join();
                }

                String body = message.getBody();
                ArrayList<File> files = new ArrayList<>();

                Element oobElement = message.getChildElement("x", "jabber:x:oob");
                if (oobElement != null) {
                    Element url = oobElement.element("url");
                    if (url != null) {
                        URL sUrl = new URL(url.getText());
                        String[] sPaths = sUrl.getPath().split("/");
                        String sPath = sPaths[sPaths.length -1];
                        File tempFile = new File(sPath);
                        FileUtils.copyURLToFile(sUrl, tempFile);
                        files.add(tempFile);
                        body = body.replace(url.getText(), "");
                    }
                }
                File[] fileArr = new File[files.size()];
                files.toArray(fileArr);

                if (body != null && !body.isEmpty()) {
                    org.javacord.api.entity.message.Message sentMessage = channel.sendMessage(body.trim(), fileArr).join();
                    String sentId = sentMessage.getIdAsString();
                    logger.info("ID Map: " + message.getID() + " <---> " + sentId);
                } else if (files.size() > 0) {
                    org.javacord.api.entity.message.Message sentMessage = channel.sendMessage(fileArr).join();
                    String sentId = sentMessage.getIdAsString();
                    logger.info("ID Map: " + message.getID() + " <---> " + sentId);
                } else {
                    Element composing = message.getChildElement("composing", "http://jabber.org/protocol/chatstates");
                    if (composing != null) {
                        logger.info("Currently typing");
                        return;
                    }
                    Element pasued = message.getChildElement("paused", "http://jabber.org/protocol/chatstates");
                    if (composing != null) {
                        logger.info("Stopped typing");
                        return;
                    }
                }

                for (File file : files) {
                    file.delete();
                }

            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof CannotMessageUserException) {
                    logger.error("Cannot send message to discord user ID " + origTo.getDiscordId());
                } else {
                    logger.error("Error handling message to discord", e.getCause());
                }
            } catch (Throwable e) {
                logger.error("Error handling message to discord", e);
            }

        }
    }


    @Override
    public void onMessageCreate(MessageCreateEvent messageCreateEvent) {
        if (messageCreateEvent.isGroupMessage()){
            onGuildMessageReceived(messageCreateEvent);
        } else if (messageCreateEvent.isPrivateMessage()) {
            onPrivateMessageReceived(messageCreateEvent);
        }
    }

    public void onPrivateMessageReceived(MessageCreateEvent event) {

        MessageAuthor author = event.getMessageAuthor();

        if (author.isBotUser()) {
            // Ignore messages sent by ourselves for now
            return;
        }

        org.javacord.api.entity.message.Message eventMessage = event.getMessage();

        String jid = "ruby@zyrenth.com";
        String messageContent = eventMessage.getContent();
        Message message = new Message();
        message.setTo(jid);

        if (messageContent != null && !messageContent.isEmpty()) {
            message.setBody(messageContent);
            try {
                Parser parser = Parser.builder().build();
                Node document = parser.parse(eventMessage.getContent());
                HtmlRenderer renderer = HtmlRenderer.builder().build();
                String html = "<body xmlns='http://www.w3.org/1999/xhtml'>" + renderer.render(document) + "</body>";
                Document parsedText = DocumentHelper.parseText(html);

                message.addChildElement("html", "http://jabber.org/protocol/xhtml-im").add(parsedText.getRootElement());
            } catch (DocumentException e) {
                e.printStackTrace();
            }
        }

        logger.info(eventMessage.getContent());
        message.setType(Message.Type.chat);
        message.setID("discord-" + event.getMessageId());
        String authorId = author.getIdAsString();
        String domain = mJid.getDomain();
        message.setFrom("u" + authorId + "@" + domain + "/" + ContactResource);

        Element delayElem = message.addChildElement("delay", "urn:xmpp:delay");
        delayElem.addAttribute("from", domain);
        delayElem.addAttribute("stamp", eventMessage.getCreationTimestamp().toString());
        String name = author.getName();
        addNickElement(message, name);

        send(message);

        for (MessageAttachment attach : eventMessage.getAttachments()) {
            Message attachMsg = new Message();
            attachMsg.setTo(message.getTo());
            attachMsg.setFrom(message.getFrom());
            attachMsg.setID("discord-attach-" + attach.getIdAsString());
            attachMsg.setType(message.getType());
            attachMsg.setBody(attach.getProxyUrl().toString());
            delayElem = attachMsg.addChildElement("delay", "urn:xmpp:delay");
            delayElem.addAttribute("from", domain);
            delayElem.addAttribute("stamp", eventMessage.getCreationTimestamp().toString());

            attachMsg.addChildElement("x", "jabber:x:oob").addElement("url").setText(attach.getProxyUrl().toString());
            send(attachMsg);
        }
    }

    public void onGuildMessageReceived(MessageCreateEvent event) {
        org.javacord.api.entity.message.Message message = event.getMessage();
        String messageId = message.getIdAsString();
    }

    /*
    @Override
    public void handleMessage(CcsMessage msg) throws SmackException.NotConnectedException {

        Map<String, String> payload = msg.getPayload();
        String action = payload.get("action");
        MySqlDao dao = MySqlDao.getInstance();

        switch (action) {
            case "REGISTER":
                try {
                    if (payload.containsKey("pairing_code")) {
                        dao.updateRegistrationId(msg.getFrom(), payload.get("pairing_code"));
                    } else {
                        String pairingCode = dao.addRegistration(msg.getFrom());
                        CcsClient client = CcsClient.getInstance();
                        String msgId = dao.getUniqueMessageId();

                        Map<String, String> outPayload = new HashMap<String, String>();
                        outPayload.put("action", "pair");
                        outPayload.put("pairing_code", pairingCode);
                        outPayload.put("server_jid", "register@" + mJid.getDomain());

                        String jsonRequest =
                                client.createJsonMessage(
                                        msg.getFrom(),
                                        msgId,
                                        outPayload,
                                        null,
                                        null, // TTL (null -> default-TTL)
                                        false);
                        client.send(jsonRequest);
                    }
                } catch (Throwable e) {
                    logger.error("Error handling GCM REGISTER call", e);
                }
                break;

            case "MESSAGE":
                try {
                    String jid = dao.getJid(msg.getFrom());
                    if (jid != null) {
                        Message message = new Message();
                        message.setTo(jid);
                        message.setBody(payload.get("message"));
                        String number = payload.get("sender");
                        String domain = mJid.getDomain();
                        message.setFrom(number + "@" + domain + "/" + ContactResource);

                        Element delayElem = message.addChildElement("delay", "urn:xmpp:delay");
                        delayElem.addAttribute("from", domain);
                        delayElem.addAttribute("stamp", payload.get("timestamp"));
                        String name = null;

                        if (payload.containsKey("contact")) {
                            name = payload.get("contact");
                        }

                        boolean updated = dao.updateContactInfo(jid, number, name);
                        if (updated && name != null && !name.isEmpty()) {
                            addNickElement(message, name);
                        }

                        send(message);
                    }
                } catch (Throwable t) {
                    logger.error("Error handling GCM MESSAGE call", t);
                }
                break;

            case "DELIVERED":
                try {
                    String jid = dao.getJid(msg.getFrom());
                    if (jid != null) {
                        Message message = new Message();
                        message.setTo(jid);
                        String number = payload.get("sender");
                        String domain = mJid.getDomain();
                        message.setFrom(number + "@" + domain + "/" + ContactResource);

                        //Element delayElem = message.addChildElement("delay", "urn:xmpp:delay");
                        // delayElem.addAttribute("from", domain);
                        //delayElem.addAttribute("stamp", payload.get("timestamp"));

                        if (payload.containsKey("message_id")) {
                            Element nickElem = message.addChildElement("received", "urn:xmpp:receipts");
                            nickElem.addAttribute("id", payload.get("message_id"));
                        }

                        send(message);
                    }
                } catch (Throwable t) {
                    logger.error("Error handling GCM DELIVERED call", t);
                }
                break;
        }

    }
    */
}
