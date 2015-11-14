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

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcRequest;
import org.apache.xmlrpc.XmlRpcRequestConfig;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.XmlRpcHttpRequestConfig;
import org.apache.xmlrpc.common.XmlRpcHttpRequestConfigImpl;
import org.apache.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.apache.xmlrpc.parser.XmlRpcRequestParser;
import org.apache.xmlrpc.serializer.DefaultXMLWriterFactory;
import org.apache.xmlrpc.serializer.XmlRpcWriter;
import org.apache.xmlrpc.serializer.XmlWriterFactory;
import org.apache.xmlrpc.server.AbstractReflectiveHandlerMapping;
import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcHttpServerConfig;
import org.apache.xmlrpc.server.XmlRpcServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.util.HttpUtil;
import org.apache.xmlrpc.util.SAXParsers;
import org.apache.xmlrpc.webserver.XmlRpcServlet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

@Component
public class ProxyServlet extends XmlRpcServlet {

    private static final long serialVersionUID = 1L;

    @Value("${XmlRpcServlet.destinationURL:http://localhost:8888/xmlrpc}")
    private String destinationURL;
    @Value("${XmlRpcServlet.username:DefaultUser}")
    private String username;
    @Value("${XmlRpcServlet.password:DefaultPwd}")
    private String password;

    private final Log log = LogFactory.getLog(getClass());

    private XmlRpcServer xmlRpcServer = new XmlRpcServer();
    private XmlWriterFactory writerFactory = new DefaultXMLWriterFactory();

    private boolean isAuthenticated(final String user, final String password) {
        this.log.info("Authenticate user: " + user + " pwd: " + password);
        return true;
    }

    protected XmlRpcHandlerMapping newXmlRpcHandlerMapping() throws XmlRpcException {
        PropertyHandlerMapping mapping = (PropertyHandlerMapping) super.newXmlRpcHandlerMapping();
        AbstractReflectiveHandlerMapping.AuthenticationHandler handler = 
            new AbstractReflectiveHandlerMapping.AuthenticationHandler() {

                @Override
                public boolean isAuthorized(XmlRpcRequest request)
                        throws XmlRpcException {
                    XmlRpcHttpRequestConfig config = (XmlRpcHttpRequestConfig) request.getConfig();
                    String user = config.getBasicUserName();
                    String password = config.getBasicPassword();
                    return isAuthenticated(user, password);
                }
                
            };

        mapping.setAuthenticationHandler(handler);
        return mapping;
    }

    @Override
    public void service(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
        this.log.info("The request : " + req.toString());
        this.log.info("was received... ");
        // Go do the request and get the response back
        relayXmlRcpRequest(req, resp);
    }

    private void relayXmlRcpRequest(ServletRequest req, ServletResponse resp) {

        Object theResult = null;

        XmlRpcServerConfigImpl serverConfig = new XmlRpcServerConfigImpl();
        serverConfig.setEnabledForExtensions(true);

        xmlRpcServer.setConfig(serverConfig);
        XmlRpcHttpRequestConfigImpl streamConfig = new XmlRpcHttpRequestConfigImpl();
        streamConfig.setEnabledForExceptions(true);
        streamConfig.setGzipRequesting(false);

        ProxyXmlRpcClient theClient = new ProxyXmlRpcClient();
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        List<Object> params = new ArrayList<Object>();
        try {
            XmlRpcRequest xmlRpcRequest= getMethodAndParamsFromRequest(req);
            XmlRpcHttpRequestConfig xmlRpcRequestConfig = (XmlRpcHttpRequestConfig) xmlRpcRequest.getConfig();
            this.username = xmlRpcRequestConfig.getBasicUserName();
            this.password = xmlRpcRequestConfig.getBasicPassword();
            this.log.info("Relay to : " + destinationURL + " for user=" + this.username + " with password=" +this.password);
            URL url = new URL(destinationURL);
            config.setServerURL(url);
            config.setBasicUserName(username);
            config.setBasicPassword(password);
            config.setEnabledForExtensions(true);
            theClient.setConfig(config);

            // Call a method
            String method = xmlRpcRequest.getMethodName();
            int parameterCount  = xmlRpcRequest.getParameterCount();
            for (int i = 0 ; i < parameterCount; i++) {
                params.add(xmlRpcRequest.getParameter(i));
            }
            theResult = theClient.callMethod(method, params);
        } catch (MalformedURLException mfue) {
            mfue.printStackTrace();
        } catch (XmlRpcException xmlrpce) {
            xmlrpce.printStackTrace();
        }
        identifyAndLog(theResult);
        // Now put the result in the response...
        resp = writeResultToResponseAsXml(streamConfig, resp, theResult);
    }

    //-------------------------------------------------------------------------
    private ServletResponse writeResultToResponseAsXml(XmlRpcStreamRequestConfig streamConfig,
            ServletResponse resp, Object theResult) {
        ServletResponseWrapper respWrapper = new ServletResponseWrapper(resp);
        try {
            ServletOutputStream sos = respWrapper.getOutputStream();
            writeResponse(streamConfig, sos, theResult);
        } catch (XmlRpcException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return respWrapper;
    }

    private void identifyAndLog(Object theResult) {
        if (theResult instanceof ArrayList) {
            this.log.info("theResult is type ArrayList");
        } else if (theResult instanceof HashMap)  {
            this.log.info("theResult is type HashMap");
        } else if (theResult instanceof String)  {
            this.log.info("theResult is type String");
        } else if (theResult instanceof Integer)  {
            this.log.info("theResult is type Integer");
        } else if (theResult instanceof Object[])  {
            this.log.info("theResult is type Object[]");
        } else {
            this.log.info("Did not identify result type");
        }
    }

    //-------------------------------------------------------------------------
    //
    // These methods are taken from XmlRpcStreamServer
    //
    protected XmlRpcWriter getXmlRpcWriter(XmlRpcStreamRequestConfig pConfig,
            OutputStream pStream) throws XmlRpcException {
        ContentHandler w = getXMLWriterFactory().getXmlWriter(pConfig, pStream);
        return new XmlRpcWriter(pConfig, w, getTypeFactory());
    }

    private TypeFactory getTypeFactory() {
        return this.xmlRpcServer.getTypeFactory();
    }

    protected void writeResponse(XmlRpcStreamRequestConfig pConfig, OutputStream pStream, 
            Object pResult) throws XmlRpcException {
        try {
            getXmlRpcWriter(pConfig, pStream).write(pConfig, pResult);
        } catch (SAXException e) {
            throw new XmlRpcException("Failed to write XML-RPC response: " + e.getMessage(), e);
        }
    }

    public XmlWriterFactory getXMLWriterFactory() {
        return writerFactory;
    }
    // End of methods taken from XmlRpcStreamServer
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    //
    // Taken from XmlRpcStreamServer.getRequest method, slightly modified
    //
    private XmlRpcRequest getMethodAndParamsFromRequest(ServletRequest req)  throws XmlRpcException {
        final XmlRpcStreamRequestConfig pConfig = getConfig((HttpServletRequest) req);
        final XmlRpcRequestParser parser = new XmlRpcRequestParser(pConfig, getTypeFactory());
        final XMLReader xr = SAXParsers.newXMLReader();
        xr.setContentHandler(parser);
        try {
            xr.parse(new InputSource(req.getInputStream()));
        } catch (SAXException e) {
            Exception ex = e.getException();
            if (ex != null  &&  ex instanceof XmlRpcException) {
                throw (XmlRpcException) ex;
            }
            throw new XmlRpcException("Failed to parse XML-RPC request: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XmlRpcException("Failed to read XML-RPC request: " + e.getMessage(), e);
        }
        final List params = parser.getParams();
        final int paramCount = params == null ? 0 : params.size();
        XmlRpcRequest xmlRpcRequest = new XmlRpcRequest() {
            public XmlRpcRequestConfig getConfig() { return pConfig; }
            public String getMethodName() { return parser.getMethodName(); }
            public int getParameterCount() { return params == null ? 0 : params.size(); }
            public Object getParameter(int pIndex) { return paramCount > 0 ? params.get(pIndex) : null; }
        };
        this.log.info("xmlRpcRequest method = " + xmlRpcRequest.getMethodName());
        this.log.info("xmlRpcRequest pcount = " + xmlRpcRequest.getParameterCount());
        this.log.info("xmlRpcRequest param1 = " + xmlRpcRequest.getParameter(0));
        return xmlRpcRequest;
        
    }
    //
    // End of code taken from XmlRpcStreamServer
    //-------------------------------------------------------------------------
    //
    // Taken from XmlRpcServletServer
    //
    protected XmlRpcHttpRequestConfigImpl newConfig(HttpServletRequest pRequest) {
        return new XmlRpcHttpRequestConfigImpl();
    }

    protected XmlRpcHttpRequestConfigImpl getConfig(HttpServletRequest pRequest) {
        XmlRpcHttpRequestConfigImpl result = newConfig(pRequest);
        XmlRpcHttpServerConfig serverConfig = (XmlRpcHttpServerConfig) getConfig();
        result.setBasicEncoding(serverConfig.getBasicEncoding());
        result.setContentLengthOptional(serverConfig.isContentLengthOptional()
                && (pRequest.getHeader("Content-Length") == null));
        result.setEnabledForExtensions(serverConfig.isEnabledForExtensions());
        result.setGzipCompressing(HttpUtil.isUsingGzipEncoding(pRequest.getHeader("Content-Encoding")));
        result.setGzipRequesting(HttpUtil.isUsingGzipEncoding(pRequest.getHeaders("Accept-Encoding")));
        result.setEncoding(pRequest.getCharacterEncoding());
        result.setEnabledForExceptions(serverConfig.isEnabledForExceptions());
        HttpUtil.parseAuthorization(result, pRequest.getHeader("Authorization"));
        return result;
    }
    //
    // End of code taken from XmlRpcServletServer
    //-------------------------------------------------------------------------

    private XmlRpcHttpServerConfig getConfig() {
        return new XmlRpcServerConfigImpl();
    }

    public void setDestinationURL(final String destinationURL) {
        this.destinationURL = destinationURL;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

}
