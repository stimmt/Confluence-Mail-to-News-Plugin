/**
 * The configuration actions which are used to edit
 * the configuration settings of this plugin.
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
 * @package  com.midori.confluence.plugin.mail2news.mail2news.actions
 */

package com.midori.confluence.plugin.mail2news.actions;

import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.log4j.Logger;


import com.atlassian.confluence.core.ConfluenceActionSupport;
import com.midori.confluence.plugin.mail2news.ConfigurationManager;
import com.midori.confluence.plugin.mail2news.MailConfiguration;

public class ConfigurationAction extends ConfluenceActionSupport {

	/**
	 * The log to which we will be logging infos and errors.
	 */
	protected final Logger log = Logger.getLogger(this.getClass());

	/* The configuration  manager for this plugin to access the configuration */
	private ConfigurationManager configurationManager;

	/* The mail configuration */
	private MailConfiguration mailConfiguration = new MailConfiguration();

	/* The string for the result of the configuration test */
	private String configurationTestResult;

	public ConfigurationAction() {
		configurationManager = new ConfigurationManager();
	}

	/**
	 * Returns the mail configuration of the plugin.
	 *
	 * @return The current configuration of the plugin.
	 */
	public MailConfiguration getMailConfiguration()
	{
		return mailConfiguration;
	}

	/**
	 * Action methods
	 */

	/**
	 * This action displays the current configuration.
	 *
	 * @return Result of the action
	 */
	public String doDefault() throws Exception {

		log.info("Getting configuration.");
		/* retrieve the configuration */
		mailConfiguration = configurationManager.getMailConfiguration();

		return ConfluenceActionSupport.INPUT;
	}

	/**
	 * This action stores the configuration entered by the user.
	 *
	 * @return Result of the action
	 */
	public String execute() throws Exception {
		/* store the configuration */
		configurationManager.setMailConfiguration(mailConfiguration);
		configurationManager.saveConfig();

		return ConfluenceActionSupport.SUCCESS;
	}

	/**
	 * This action tests the configuration entered by the user.
	 *
	 * @return Result of the action
	 */
	public String testConfiguration() throws Exception {

		/***
		 * Test the configuration by trying to connect to the store.
		 ***/

		configurationTestResult = "Test successful.";
		log.info("Testing configuration.");

		try
		{

			/* get the mail configuration from the manager */
			MailConfiguration config = configurationManager.getMailConfiguration();
			if (config == null)
			{
				throw new Exception("Could not get mail configuration.");
			}

			/* create the properties for the session */
			Properties prop = new Properties();

			/* get the protocol to use */
			String protocol = config.getProtocol().toLowerCase().concat(config.getSecure() ? "s" : "");
			/* assemble the property prefix for this protocol */
			String propertyPrefix = "mail.";
			propertyPrefix = propertyPrefix.concat(protocol).concat(".");

			/* get the server port from the configuration and add it to the properties,
			 * but only if it is actually set. If port = 0 this means we use the standard
			 * port for the chosen protocol */
			int port = config.getPort();
			if (port != 0)
			{
				prop.setProperty(propertyPrefix.concat("port"), "" + port);
			}

			/* set connection timeout (10 seconds) */
			prop.setProperty(propertyPrefix.concat("connectiontimeout"), "10000");

			/* get the session for connecting to the mail server */
			Session session = Session.getInstance(prop, null);

			/* get the mail store, using the desired protocol */
			Store store;
			if (config.getSecure())
			{
				store = session.getStore(protocol);
			}
			else
			{
				store = session.getStore(protocol);
			}

			/* get the host and credentials for the mail server from the configuration */
			String host = config.getServer();
			String username = config.getUsername();
			String password = config.getPassword();

			/* sanity check */
			if (host == null || username == null || password == null)
			{
				throw new Exception("Incomplete mail configuration settings (at least one setting is null).");
			}

			/* connect to the mailstore */
			try {
				store.connect(host, username, password);
			}
			catch (AuthenticationFailedException afe)
			{
				throw new Exception("Authentication for mail store failed: " + afe.getMessage(), afe);
			}
			catch (MessagingException me)
			{
				throw new Exception("Connecting to mail store failed: " + me.getMessage(), me);
			}
			catch (IllegalStateException ise)
			{
				throw new Exception("Connecting to mail store failed, already connected: " + ise.getMessage(), ise);
			}
			catch (Exception e)
			{
				throw new Exception("Connecting to mail store failed, general exception: " + e.getMessage(), e);
			}
		}
		catch (Exception e)
		{
			String reason = (e.getMessage() != null) ? e.getMessage() : e.toString();
			configurationTestResult = reason;
		}

		return ConfluenceActionSupport.SUCCESS;
	}

	/**
	 * Return the result of the configuration test.
	 *
	 * @return The configuration test result.
	 */
	public String getConfigurationTestResult() {
		return configurationTestResult;
	}

	/**
	 * Get a list with the possible protocols.
	 *
	 * @return Returns a map with the possible protocols.
	 */
	public Map getProtocols() {
		/** FIXME
		 * WebWorks somehow balks at this hashtable. Find out why.
		 * At the moment the values are hardcoded into the VM file.
		 */
		/* create a hashtable with the possible protocols */
		Hashtable h = new Hashtable();
		h.put("imap", "IMAP");
		h.put("pop3", "POP3");

		return h;
	}

}
