package com.zyrenth.xmpp.discordbridge.iq;

import com.zyrenth.xmpp.discordbridge.BaseComponent;
import org.dom4j.Element;
import org.xmpp.packet.IQ;

import java.util.ArrayList;

/**
 * Created by kabili on 1/31/16.
 */
public class IQProcessor {

    private static ArrayList<IQHandler> handlers;

    static {
        handlers = new ArrayList<>();
        handlers.add(new RegisterHandler());
        handlers.add(new VCardHandler());
        handlers.add(new VersionHandler());
    }

    public static ArrayList<String> getSupportedFeatures(IQHandler.Type type) {
        ArrayList<String> ns = new ArrayList<>();
        for (IQHandler handler : handlers) {
            if (handler.getSupportedTypes().contains(type)) {
                ns.add(handler.getNamespace());
            }
        }
        return ns;
    }

    public static IQ processIQ(BaseComponent component, IQ orig) {

        for (Object obj : orig.getElement().elements()) {

            Element elem = (Element) obj;
            String xmlns = elem.getNamespaceURI();

            for (IQHandler handler : handlers) {
                if (handler.getNamespace().equals(xmlns)) {
                    IQ resp = handler.processIQ(component, orig);
                    if (resp != null)
                        return resp;
                }
            }
        }

        return null;
    }

}
