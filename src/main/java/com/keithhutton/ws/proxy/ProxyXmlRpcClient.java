/*
 * Copyright 2015 Keith Hutton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.keithhutton.ws.proxy;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;

import org.apache.ws.commons.util.NamespaceContextImpl;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfig;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.common.TypeFactoryImpl;
import org.apache.xmlrpc.common.XmlRpcController;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.parser.TypeParser;

public class ProxyXmlRpcClient {

    private XmlRpcClientConfig config;

    public Object callMethod(String method, Object... params) throws MalformedURLException, XmlRpcException {
        return callMethod(method, Arrays.asList(params));
    }

    public Object callMethod(String method, List params) throws MalformedURLException, XmlRpcException {
        final XmlRpcClient client = getClientConnection();
        final Object result = client.execute(method, params);
        return result;
    }

    private XmlRpcClient getClientConnection() throws MalformedURLException {
        final XmlRpcClient client = new XmlRpcClient();
        client.setTypeFactory(new FlexiTypeFactory(client));
        client.setConfig(config);
        client.getTypeFactory();
        return client;
    }

    public void setConfig(XmlRpcClientConfigImpl config) {
        this.config = config;
    }

    private class FlexiTypeFactory extends TypeFactoryImpl {

        public FlexiTypeFactory(XmlRpcController pController) {
            super(pController);
        }

        @Override
        public TypeParser getParser(XmlRpcStreamConfig pConfig, NamespaceContextImpl pContext, String pURI, String pLocalName) {
            if ("i8".equals(pLocalName)) {
                pLocalName = "string";
            }
            return super.getParser(pConfig, pContext, pURI, pLocalName);
        }
    }
}
