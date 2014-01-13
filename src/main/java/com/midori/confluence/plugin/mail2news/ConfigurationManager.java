/**
 * The configuration manager for the mail2news confluence plugin.
 *
 * This software is licensed under the BSD license.
 *
 * Copyright (c) 2008, Liip AG
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 * - Neither the name of Liip AG nor the names of its contributors may be used
 *   to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author   Roman Schlegel <roman@liip.ch>
 * @version  $Id$
 * @package  com.midori.confluence.plugin.mail2news.mail2news
 */

package com.midori.confluence.plugin.mail2news;

import org.apache.log4j.Logger;

import com.atlassian.bandana.BandanaContext;
import com.atlassian.bandana.BandanaManager;
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext;
import com.atlassian.spring.container.ContainerManager;
import com.thoughtworks.xstream.XStream;

public class ConfigurationManager {

    /**
     * The bandana key for the configuration of this plugin.
     */
    private static final String BANDANA_KEY = "com.midori.confluence.plugin.mail2news.mail2news.ConfigurationManager";

    /**
     * The bandana context to access the configuration of this plugin.
     */
    private static final BandanaContext bandanaContext = new ConfluenceBandanaContext();

    /**
     * The bandana manager of this confluence instance,
     * used for storing the settings of this plugin.
     */
    private BandanaManager bandanaManager;

    /**
     * The XStream object used for serialisation.
     */
    private XStream xStream;

    /**
     * The configuration object which holds the configuration of this plugin.
     */
    private MailConfiguration mailConfiguration = null;

    /**
     * The log to which we will be logging infos and errors.
     */
    protected final Logger log = Logger.getLogger(this.getClass());

    public ConfigurationManager()
    {
        ContainerManager.autowireComponent(this);
        loadConfig();
    }

    /**
     * This method is automatically called by Confluence to pass the
     * bandana manager of this confluence instance.
     *
     * @param bandanaManager The bandana manager of this confluence manager
     */
    public void setBandanaManager(BandanaManager bandanaManager) {
        this.bandanaManager = bandanaManager;
    }

    /**
     * This method deserializes the configuration object using
     * bandana.
     */
    public void loadConfig() {
        log.info("Loading configuration.");
        try {
            /* check that the bandana manager exists */
            if (this.bandanaManager == null)
            {
                throw new Exception("No bandana manager set.");
            }

            /* get the serialised configuration object */
            Object data = bandanaManager.getValue( bandanaContext, BANDANA_KEY );
            /* check cast and deserialise */
            if (data instanceof String) {
                try {
                    /* Workaround: have to create a new xStream before deserialising */
                    xStream = new XStream();
                    xStream.setClassLoader(getClass().getClassLoader());
                    Object deserialisedData = xStream.fromXML((String)data);
                    if (deserialisedData instanceof MailConfiguration) {
                        mailConfiguration = (MailConfiguration) deserialisedData;
                    }
                } catch (Exception e) {
                    this.log.error("Could not deserialise configuration.", e);
                    /* force the creation of a dummy configuration */
                    mailConfiguration = null;
                }
            }
            /* check if we could load the configuration */
            if (mailConfiguration == null) {
                /* initialise a default configuration */
                mailConfiguration = new MailConfiguration();
            }

        } catch (Exception e) {
            this.log.error("Could not load configuration.", e);
        }
    }

    /**
     * Save the configuration by saving the mailConfiguration object
     * using bandana.
     */
    public void saveConfig() {
        log.info("Saving configuration.");
        try {
            if (mailConfiguration == null)
            {
                log.error("Mail configuration is null, saving dummy configuration.");
                loadConfig();
            }
            /* Workaround: have to create a new xstream before serialising */
            xStream = new XStream();
            xStream.setClassLoader(getClass().getClassLoader());
            bandanaManager.setValue( bandanaContext, BANDANA_KEY, xStream.toXML(mailConfiguration) );

        } catch (Exception e) {
            this.log.error("Could not save configuration.", e);
        } finally {
            this.loadConfig();
        }
    }

    /**
     * Get the mail configuration of this manager
     *
     * @return Returns the mail configuration of this manager
     */
    public MailConfiguration getMailConfiguration()
    {
        return this.mailConfiguration;
    }

    /**
     * Set the mail configuration of this manager.
     * NOTE: This does not yet save the configuration, call saveConfig()
     *       for this.
     *
     * @param mailConfiguration The mail configuration to set.
     */
    public void setMailConfiguration(MailConfiguration mailConfiguration)
    {
        log.info("Setting configuration: " + mailConfiguration.toString());
        this.mailConfiguration = mailConfiguration;
    }
}
