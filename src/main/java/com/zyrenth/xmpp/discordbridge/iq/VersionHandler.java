package com.zyrenth.xmpp.discordbridge.iq;

import com.zyrenth.xmpp.discordbridge.BaseComponent;
import org.dom4j.Element;
import org.xmpp.packet.IQ;

import java.util.EnumSet;

/**
 * Created by kabili on 1/31/16.
 */
public class VersionHandler implements IQHandler {


    private static final String NAMESPACE = "jabber:iq:version";

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

        Element child = response.setChildElement("query", NAMESPACE);
        child.addElement("name").setText(component.getName());
        child.addElement("version").setText(component.getVersion());
        child.addElement("os").setText(System.getProperty("os.name"));

        return response;
    }
}
