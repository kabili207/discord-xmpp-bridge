package com.zyrenth.xmpp.discordbridge;

import com.zyrenth.xmpp.discordbridge.entities.EntityCapabilities;
import com.zyrenth.xmpp.discordbridge.entities.Feature;
import com.zyrenth.xmpp.discordbridge.entities.Identity;
import com.zyrenth.xmpp.discordbridge.entities.DiscordJID;
import com.zyrenth.xmpp.discordbridge.iq.IQHandler;
import com.zyrenth.xmpp.discordbridge.iq.IQProcessor;
import org.apache.commons.io.FileUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.dom4j.*;
import org.dom4j.tree.DefaultElement;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.activity.Activity;
import org.javacord.api.entity.activity.ActivityAssets;
import org.javacord.api.entity.activity.ActivityParty;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.PrivateChannel;
import org.javacord.api.entity.message.MessageAttachment;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.user.User;
import org.javacord.api.entity.user.UserStatus;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.event.user.UserChangeActivityEvent;
import org.javacord.api.event.user.UserChangeStatusEvent;
import org.javacord.api.exception.CannotMessageUserException;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.user.UserChangeActivityListener;
import org.javacord.api.listener.user.UserChangeStatusListener;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.AbstractComponent;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.*;

import java.io.File;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionException;

/**
 * Created by kabili on 1/30/16.
 */
public class BridgeComponent extends AbstractComponent implements BaseComponent, MessageCreateListener, UserChangeActivityListener, UserChangeStatusListener {

    public static final Logger logger = LoggerFactory.getLogger(BridgeComponent.class);

    private static final String ContactResource = "user";
    public static final String TEST_USER_JID = "ruby@zyrenth.com";

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

    @Override
    public Identity getIdentity(JID rawJid) {
        DiscordJID jid = DiscordJID.from(rawJid);
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
        DiscordJID jid = DiscordJID.from(rawJid);
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

        DiscordJID toJid = DiscordJID.from(iq.getTo());

        logger.debug("disco request from " + iq.getFrom().toString() + " for " + toJid.toString());

        MessageDigest digest = null;

        if (toJid.isDiscordUser()) {
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
            if (domain != null) {

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
            child.addAttribute("node", "http://" + getDomain());
            child.addAttribute("ver", hash);

        } catch (NoSuchAlgorithmException e) {
            logger.error("Error adding caps", e);
        }
    }

    protected void addNickElement(@NotNull Message message, String nick) {
        Element nickElem = message.addChildElement("nick", "http://jabber.org/protocol/nick");
        nickElem.setText(nick);
    }

    protected void addNickElement(@NotNull Presence presence, String nick) {
        Element nickElem = presence.addChildElement("nick", "http://jabber.org/protocol/nick");
        nickElem.setText(nick);
    }

    protected void sendPresence(Presence.Type type, @NotNull JID from, @NotNull JID to, String name) {
        Presence response = new Presence();
        response.setTo(to);
        response.setFrom(from);
        DiscordJID discordJID = DiscordJID.from(from);
        if (discordJID.isDiscordUser()) {
            User user = discord.getUserById(discordJID.getDiscordId()).join();
            UserStatus status = user.getStatus();
            switch (status) {
                case ONLINE:
                    // do nothing
                    break;
                case DO_NOT_DISTURB:
                    response.setShow(Presence.Show.dnd);
                    break;
                case IDLE:
                    response.setShow(Presence.Show.away);
                    break;
                case OFFLINE:
                case INVISIBLE:
                    type = Presence.Type.unavailable;
                    break;
            }
            addCapsElement(response);
        }
        response.setType(type);
        if (name != null && !name.isEmpty())
            addNickElement(response, name);
        send(response);
    }

    protected void sendPresence(Presence.Type type, @NotNull String from, @NotNull String to, String name) throws ComponentException {
        sendPresence(type, new JID(from), new JID(to), name);
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
    public void onUserChangeStatus(UserChangeStatusEvent userChangeStatusEvent) {
        User user = discord.getUserById(userChangeStatusEvent.getUser().getId()).join();
        String from = "u" + user.getId() + "@" + getDomain() + "/" + ContactResource;
        String to = TEST_USER_JID;
        try {
            sendPresence(null, from, to, null);
        } catch (ComponentException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void handleMessage(Message message) {


        DiscordJID origTo = DiscordJID.from(message.getTo());
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
                        String sPath = sPaths[sPaths.length - 1];
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
        if (messageCreateEvent.isGroupMessage()) {
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

        String jid = TEST_USER_JID;
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

        if (eventMessage.getMentionedUsers().contains(discord.getYourself())) {
            message.addChildElement("attention", "urn:xmpp:attention:0");
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

    @Override
    public void onUserChangeActivity(UserChangeActivityEvent discordEvent) {
        Optional<Activity> optionalActivity = discordEvent.getNewActivity();
        User user = discordEvent.getUser();
        Message message = new Message();
        message.setTo(TEST_USER_JID);
        message.setFrom("u" + user.getId() + "@" + getDomain());
        message.setType(Message.Type.headline);
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");

        // See Gajim source for supported namespaces
        // https://dev.gajim.org/gajim/python-nbxmpp/blob/master/nbxmpp/protocol.py
        Element music = new DefaultElement("items", event.getNamespace())
                .addAttribute("node", "http://jabber.org/protocol/tune")
                .addElement("item").addElement("tune", "http://jabber.org/protocol/tune");

        // Should be "urn:xmpp:gaming:0", but clients haven't caught up yet
        Element game = new DefaultElement("items", event.getNamespace())
                .addAttribute("node", "http://jabber.org/protocol/gaming")
                .addElement("item").addElement("game", "http://jabber.org/protocol/gaming");

        // Should be "urn:xmpp:viewing:0", but clients haven't caught up yet
        Element viewing = new DefaultElement("items", event.getNamespace())
                .addAttribute("node", "http://jabber.org/protocol/viewing")
                .addElement("item").addElement("video", "http://jabber.org/protocol/viewing");

        if (optionalActivity.isPresent()) {
            Activity activity = optionalActivity.get();
            ActivityType type = activity.getType();
            ActivityAssets assets = activity.getAssets().orElse(null);
            String details = activity.getDetails().orElse(null);
            Instant startTime = activity.getStartTime().orElse(null);
            Instant endTime = activity.getEndTime().orElse(null);
            ActivityParty party = activity.getParty().orElse(null);
            String activityState = activity.getState().orElse(null);
            String streamingUrl = activity.getStreamingUrl().orElse(null);
            switch (type) {
                case LISTENING:
                    // XEP-0118: User Tune
                    music.addElement("artist").setText(activityState);
                    music.addElement("title").setText(details);
                    music.addElement("length").setText(Long.toString(endTime.getEpochSecond() - startTime.getEpochSecond()));
                    music.addElement("source").setText(assets.getLargeText().orElse(null));
                    break;
                case PLAYING:
                    // XEP-0196: User Gaming
                    game.addElement("name").setText(activity.getName());
                    break;
                case WATCHING:
                    // XEP-0197: User Viewing
                    // Only available to bots
                    break;
            }

        }

        game = getTopLevel(game);
        music = getTopLevel(music);
        viewing = getTopLevel(viewing);

        event.add(music);
        send(message);
        event.remove(music);

        event.add(game);
        send(message);
        event.remove(game);

        event.add(viewing);
        send(message);
        event.remove(viewing);
    }

    private Element getTopLevel(Element elm) {
        Element parent = elm.getParent();
        if (parent != null) {
            return getTopLevel(parent);
        }
        return elm;
    }
}
