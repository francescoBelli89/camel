/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class ManagedWireTapTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.init();
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    @Test
    public void testManageWireTap() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MockEndpoint foo = getMockEndpoint("mock:foo");
        foo.expectedMessageCount(2);

        MockEndpoint bar = getMockEndpoint("mock:bar");
        bar.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "whereto", "foo");
        template.sendBodyAndHeader("direct:start", "Bye World", "whereto", "foo");
        template.sendBodyAndHeader("direct:start", "Hi World", "whereto", "bar");

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the delayer
        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mysend\"");

        // should be on route1
        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("route1", routeId);

        String camelId = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", camelId);

        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals(ServiceStatus.Started.name(), state);

        String uri = (String) mbeanServer.getAttribute(on, "Uri");
        assertEquals("direct:${header.whereto}", uri);

        Boolean dynamicUri = (Boolean) mbeanServer.getAttribute(on, "DynamicUri");
        assertTrue(dynamicUri);

        TabularData data = (TabularData) mbeanServer.invoke(on, "extendedInformation", null, null);
        assertNotNull(data);
        assertEquals(2, data.size());

        data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{false}, new String[]{"boolean"});
        assertNotNull(data);
        assertEquals(3, data.size());

        data = (TabularData) mbeanServer.invoke(on, "explain", new Object[]{true}, new String[]{"boolean"});
        assertNotNull(data);
        assertEquals(13, data.size());

        String json = (String) mbeanServer.invoke(on, "informationJson", null, null);
        assertNotNull(json);
        assertTrue(json.contains("\"description\": \"Routes a copy of a message (or creates a new message) to a secondary destination while continue routing the original message"));
        assertTrue(json.contains(" \"uri\": { \"kind\": \"attribute\", \"required\": \"true\", \"type\": \"string\", \"javaType\": \"java.lang.String\","
                + " \"deprecated\": \"false\", \"value\": \"direct:${header.whereto}\""));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .wireTap("direct:${header.whereto}").id("mysend");

                from("direct:foo").to("mock:foo");

                from("direct:bar").to("mock:bar");
            }
        };
    }

}
