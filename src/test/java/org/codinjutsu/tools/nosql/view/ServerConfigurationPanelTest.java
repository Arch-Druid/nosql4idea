/*
 * Copyright (c) 2015 David Boissier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codinjutsu.tools.nosql.view;

import com.intellij.openapi.command.impl.DummyProject;
import org.codinjutsu.tools.nosql.ServerConfiguration;
import org.codinjutsu.tools.nosql.database.DatabaseVendorManager;
import org.codinjutsu.tools.nosql.logic.ConfigurationException;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.Containers;
import org.fest.swing.fixture.FrameFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerConfigurationPanelTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ServerConfigurationPanel configurationPanel;
    private DatabaseVendorManager databaseVendorManager;

    private FrameFixture frameFixture;

    @Before
    public void setUp() throws Exception {
        databaseVendorManager = Mockito.spy(new DatabaseVendorManager());
        configurationPanel = GuiActionRunner.execute(new GuiQuery<ServerConfigurationPanel>() {
            protected ServerConfigurationPanel executeInEDT() {
                return new ServerConfigurationPanel(DummyProject.getInstance(), databaseVendorManager);
            }
        });

        frameFixture = Containers.showInFrame(configurationPanel.getRootPanel());
    }

    @After
    public void tearDown() {
        frameFixture.cleanUp();
    }

    @Test
    public void validateFormWithOneServerUrl() throws Exception {

        frameFixture.textBox("serverUrlsField").setText("localhost:25");
        frameFixture.textBox("usernameField").setText("john");
        frameFixture.textBox("passwordField").setText("johnpassword");

        frameFixture.checkBox("userDatabaseAsMySingleDatabaseField").check();
        frameFixture.checkBox("sslConnectionField").check();
        frameFixture.checkBox("autoConnectField").check();

        ServerConfiguration configuration = new ServerConfiguration();

        configurationPanel.applyConfigurationData(configuration);

        assertEquals(Arrays.asList("localhost:25"), configuration.getServerUrls());
        assertEquals("john", configuration.getUsername());
        assertTrue(configuration.isUserDatabaseAsMySingleDatabase());
        assertTrue(configuration.isSslConnection());
        assertTrue(configuration.isConnectOnIdeStartup());
    }

    @Test
    public void loadFormWithOneServerUrl() throws Exception {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.setServerUrls(Arrays.asList("localhost:25"));
        configuration.setUsername("john");
        configuration.setPassword("johnpassword");

        configurationPanel.loadConfigurationData(configuration);

        frameFixture.textBox("serverUrlsField").requireText("localhost:25");
        frameFixture.textBox("usernameField").requireText("john");
        frameFixture.textBox("passwordField").requireText("johnpassword");
    }

    @Test
    public void validateFormWithMissingMongoUrlShouldThrowAConfigurationException() {
        thrown.expect(ConfigurationException.class);
        thrown.expectMessage("URL(s) should be set");

        frameFixture.textBox("serverUrlsField").setText(null);

        configurationPanel.applyConfigurationData(new ServerConfiguration());
    }

    @Test
    public void validateFormWithEmptyMongoUrlShouldThrowAConfigurationException() {
        thrown.expect(ConfigurationException.class);
        thrown.expectMessage("URL(s) should be set");

        frameFixture.textBox("serverUrlsField").setText("");

        configurationPanel.applyConfigurationData(new ServerConfiguration());
    }

    @Test
    public void validateFormWithBadMongoUrlShouldThrowAConfigurationException() {
        thrown.expect(ConfigurationException.class);
        thrown.expectMessage("URL 'host' format is incorrect. It should be 'host:port'");

        frameFixture.textBox("serverUrlsField").setText("host");

        configurationPanel.applyConfigurationData(new ServerConfiguration());
    }


    @Test
    public void validateFormWithBadMongoPortShouldThrowAConfigurationException() {
        thrown.expect(ConfigurationException.class);
        thrown.expectMessage("Port in the URL 'host:port' is incorrect. It should be a number");

        frameFixture.textBox("serverUrlsField").setText("host:port");

        configurationPanel.applyConfigurationData(new ServerConfiguration());
    }


    @Test
    public void validateFormWithReplicatSet() throws Exception {

        frameFixture.textBox("serverUrlsField").setText(" localhost:25, localhost:26 ");

        ServerConfiguration configuration = new ServerConfiguration();

        configurationPanel.applyConfigurationData(configuration);

        assertEquals(Arrays.asList("localhost:25", "localhost:26"), configuration.getServerUrls());
    }

    @Test
    public void loadFormWithReplicatSet() throws Exception {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.setServerUrls(Arrays.asList("localhost:25", "localhost:26"));

        configurationPanel.loadConfigurationData(configuration);

        frameFixture.textBox("serverUrlsField").requireText("localhost:25,localhost:26");
    }
}
