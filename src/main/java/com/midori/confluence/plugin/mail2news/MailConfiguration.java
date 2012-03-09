/**
 * This object represents the configuration of
 * the mail2news plugin and is stored using
 * bandana for persistence.
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

import java.io.Serializable;

@com.atlassian.xwork.ParameterSafe
public class MailConfiguration implements Serializable {

	/* The mail server */
	private String server;

	/* The protocol to use, either
	 * 'IMAP' or 'POP3' */
	private String protocol;

	/* The server port */
	private int port;

	/* The username */
	private String username;

	/* The password */
	private String password;

	/* Whether to use a secure connection to the mail store */
	private boolean secure;

	/* Whether automatically add the gallery macro if an attachment is an image */
	private boolean gallerymacro;

	/**
	 * The constructor, fills out default (dummy) values.
	 */
	public MailConfiguration()
	{
		server = "mail.domain";
		protocol = "IMAP";
		port = 143;
		username = "user@domain";
		password = "password";
		secure = false;
		gallerymacro = false;
	}

	/**
	 * Get the mail server.
	 *
	 * @return the server
	 */
	public String getServer() {
		return server;
	}

	/**
	 * Set the mail server.
	 *
	 * @param server
	 *            the server to set
	 */
	public void setServer(String server) {
		this.server = server;
	}

	/**
	 * Get the mail server port.
	 *
	 * @return the port
	 */
	public int getPort() {
		return port;
	}

	/**
	 * Set the mail server port.
	 *
	 * @param port
	 *            the port to set
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Get the username.
	 *
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Set the username.
	 *
	 * @param username
	 *            the username to set
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Get the password.
	 *
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Set the password.
	 *
	 * @param password
	 *            the password to set
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Get whether to use a secure connection to the mail store.
	 *
	 * @return True is SSL should be used, false if not.
	 */
	public boolean getSecure() {
		return secure;
	}

	/**
	 * Set whether to use SSL when connecting to the mail store.
	 *
	 * @param secure Whether to use SSL
	 */
	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	/**
	 * Returns which protocol to use,
	 * 'IMAP' or 'POP3'.
	 *
	 * @return The protocol to use.
	 */
	public String getProtocol() {
		return protocol;
	}

	/**
	 * Set which protocol to use, 'IMAP' or
	 * 'POP3'.
	 *
	 * @param protocol The protocol which should be used.
	 */
	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	/**
	 * Set whether to automatically add the gallery macro.
	 *
	 * @return the gallerymacro
	 */
	public boolean getGallerymacro() {
		return gallerymacro;
	}

	/**
	 * Get the current value of the gallery macro flag.
	 *
	 * @param gallerymacro the gallerymacro to set
	 */
	public void setGallerymacro(boolean gallerymacro) {
		this.gallerymacro = gallerymacro;
	}

	/**
	 * Convenience method.
	 *
	 * @return Returns this configuration as a string.
	 */
	public String toString()
	{
		return "Server: " + server + " , Protocol: " + protocol + ", Secure: " + secure + ", Port: " + port + " , Username: " + username + ", Password: ****" + ", Gallery macro: " + gallerymacro;
	}


	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public String getSmtpserver() {
		return null;
	}

	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public void setSmtpserver(String smtpserver) {
	}

	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public String getSmtpusername() {
		return null;
	}

	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public void setSmtpusername(String smtpusername) {
	}

	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public String getSmtppassword() {
		return null;
	}

	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public void setSmtppassword(String smtppassword) {
	}

	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public String getEmailaddress() {
		return null;
	}

	/**
	 * This is practically removed, but the method is kept to be
	 * able to deserialize legacy settings. Will be removed in 2.0.
	 */
	public void setEmailaddress(String emailaddress) {
	}
}
