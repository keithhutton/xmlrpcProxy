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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;

import org.apache.xmlrpc.webserver.XmlRpcServlet;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XmlrpcConfig {

    @Bean
    public Servlet xmlrpcServlet() {
        final XmlRpcServlet xmlRpcServlet = new ProxyServlet();
        return xmlRpcServlet;
    }

    @Bean
    public ServletRegistrationBean xmlrpcServletRegistration() {
        final ServletRegistrationBean servletRegBean = new ServletRegistrationBean(xmlrpcServlet(), "/xmlrpc");
        final Map<String, String> params = new HashMap<String, String>();
        params.put("enabledForExtensions", "true");
        servletRegBean.setInitParameters(params);
        return servletRegBean;
    }
}
