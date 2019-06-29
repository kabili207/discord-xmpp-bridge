package com.zyrenth.xmpp.discordbridge.iq;

import com.zyrenth.xmpp.discordbridge.BaseComponent;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

import java.util.EnumSet;

/**
 * Created by kabili on 2/1/16.
 */
public class RegisterHandler implements IQHandler {
    private static Logger logger = LoggerFactory.getLogger(RegisterHandler.class);

    private static final String NAMESPACE = "jabber:iq:register";

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public EnumSet<Type> getSupportedTypes(){
        return EnumSet.of(Type.SERVER);
    }

    @Override
    public IQ processIQ(BaseComponent component, IQ iq) {
        IQ response = iq.createCopy();
        response.setType(IQ.Type.result);
        response.setTo(iq.getFrom());
        response.setFrom(iq.getTo());
        Element child = iq.getChildElement();

        if (iq.getType().equals(IQ.Type.get)) {
            if (!child.hasContent()){
                Element respChild = response.setChildElement("query", NAMESPACE);
                respChild.addElement("instructions").setText("Please provide your discord token");
                respChild.addElement("token");
            }
        } else if (iq.getType().equals(IQ.Type.set)) {
            response.setChildElement("query", NAMESPACE);
        }
        return response;
    }
}
