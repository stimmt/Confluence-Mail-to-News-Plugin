/**
 * The mail2news confluence plugin, job module.
 * This is the job which is periodically executed to
 * check for new email messages in a specified account
 * and add them to the news of a space.
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderNotFoundException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.pages.BlogPost;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.confluence.util.GeneralUtil;
import com.atlassian.mail.MailFactory;
import com.atlassian.mail.server.SMTPMailServer;
import com.atlassian.quartz.jobs.AbstractJob;
import com.atlassian.spring.container.ContainerManager;
import com.atlassian.user.User;
import com.atlassian.user.search.SearchResult;
import com.atlassian.user.search.page.Pager;

public class Mail2NewsJob extends AbstractJob {

	/**
	 * The log to which we will be logging infos and errors.
	 */
	protected final Logger log = Logger.getLogger(this.getClass());

	/**
	 * The page manager of this Confluence instance
	 */
	private PageManager pageManager;

	/**
	 * The space manager of this Confluence instance
	 */
	private SpaceManager spaceManager;

	/**
	 * The attachment manager of this Confluence instance
	 */
	private AttachmentManager attachmentManager;

	/**
	 * The user accessor of this Confluence instance, used to find
	 * users.
	 */
	private UserAccessor userAccessor;

	/**
	 * The configuration manager of this plugin which contains
	 * the settings of this plugin (e.g. login credentials for
	 * the email account which is monitored).
	 */
	private ConfigurationManager configurationManager;

	/**
	 * The content of a message, this will be the content of the
	 * news entry
	 */
	private String blogEntryContent;

	/**
	 * A list of attachments, used when examining a new message
	 */
	private LinkedList attachments;
	/**
	 * A list of input streams for the attachments.
	 */
	private LinkedList attachmentsInputStreams;

	/**
	 * A flag indicating whether the current post contains an image
	 */
	private boolean containsImage;

	/**
	 * The default constructor. Autowires this component and creates a
	 * new configuration manager.
	 */
	public Mail2NewsJob() {
		/* autowire this component (this means that the space and
		 * page manager are automatically set by confluence */
		ContainerManager.autowireComponent(this);

		/* create the configuration manager */
		this.configurationManager = new ConfigurationManager();

	}

	/**
	 * The main method of this job. Called by confluence every time the mail2news trigger
	 * fires.
	 *
	 * @see com.atlassian.quartz.jobs.AbstractJob#doExecute(org.quartz.JobExecutionContext)
	 */
	public void doExecute(JobExecutionContext arg0)
			throws JobExecutionException {

		/* The mailstore object used to connect to the server */
		Store store = null;

		try {

			this.log.info("Executing mail2news plugin.");

			/* check if we have all necessary components */
			if (pageManager == null)
			{
				throw new Exception("Null PageManager instance.");
			}
			if (spaceManager == null)
			{
				throw new Exception("Null SpaceManager instance.");
			}
			if (configurationManager == null)
			{
				throw new Exception("Null ConfigurationManager instance.");
			}

			/* get the mail configuration from the manager */
			MailConfiguration config = configurationManager.getMailConfiguration();
			if (config == null)
			{
				throw new Exception("Null MailConfiguration instance.");
			}

			/* create the properties for the session */
			Properties prop = new Properties();

			/* get the protocol to use */
			if (config.getProtocol() == null)
			{
				throw new Exception("Cannot get protocol.");
			}
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

			/***
			 * Open the INBOX
			 ***/

			/* get the INBOX folder */
			Folder folderInbox = store.getFolder("INBOX");
			/* we need to open it READ_WRITE, because we want to move messages we already handled */
			try {
				folderInbox.open(Folder.READ_WRITE);
			}
			catch (FolderNotFoundException fnfe)
			{
				throw new Exception("Could not find INBOX folder: " + fnfe.getMessage(), fnfe);
			}
			catch (Exception e)
			{
				throw new Exception("Could not open INBOX folder: " + e.getMessage(), e);
			}

			/* here we have to split, because IMAP will be handled differently from POP3 */
			if (config.getProtocol().toLowerCase().equals("imap"))
			{
				/***
				 * Open the default folder, under which will be the processed
				 * and the invalid folder.
				 ***/

				Folder folderDefault = null;
				try
				{
					folderDefault = store.getDefaultFolder();
				}
				catch (MessagingException me)
				{
					throw new Exception("Could not get default folder: " + me.getMessage(), me);
				}
				/* sanity check */
				try {
					if (!folderDefault.exists())
					{
						throw new Exception("Default folder does not exist. Cannot continue. This might indicate that this software does not like the given IMAP server. If you think you know what the problem is contact the author.");
					}
				}
				catch (MessagingException me)
				{
					throw new Exception("Could not test existence of the default folder: " + me.getMessage(), me);
				}

				/**
				 * This is kind of a fallback mechanism. For some reasons it can happen that
				 * the default folder has an empty name and exists() returns true, but when
				 * trying to create a subfolder it generates an error message.
				 * So what we do here is if the name of the default folder is empty, we
				 * look for the "INBOX" folder, which has to exist and then create the
				 * subfolders under this folder.
				 */
				if (folderDefault.getName().equals(""))
				{
					this.log.warn("Default folder has empty name. Looking for 'INBOX' folder as root folder.");
					folderDefault = store.getFolder("INBOX");
					if (!folderDefault.exists())
					{
						throw new Exception("Could not find default folder and could not find 'INBOX' folder. Cannot continue. This might indicate that this software does not like the given IMAP server. If you think you know what the problem is contact the author.");
					}
				}

				/***
				 * Open the folder for processed messages
				 ***/

				/* get the folder where we store processed messages */
				Folder folderProcessed = folderDefault.getFolder("Processed");
				/* check if it exists */
				if (!folderProcessed.exists()) {
					/* does not exist, create it */
					try {
						if (!folderProcessed.create(Folder.HOLDS_MESSAGES))
						{
							throw new Exception("Creating 'processed' folder failed.");
						}
					}
					catch (MessagingException me)
					{
						throw new Exception("Could not create 'processed' folder: " + me.getMessage(), me);
					}
				}
				/* we need to open it READ_WRITE, because we want to move messages we already handled to this folder */
				try {
					folderProcessed.open(Folder.READ_WRITE);
				}
				catch (FolderNotFoundException fnfe)
				{
					throw new Exception("Could not find 'processed' folder: " + fnfe.getMessage(), fnfe);
				}
				catch (Exception e)
				{
					throw new Exception("Could not open 'processed' folder: " + e.getMessage(), e);
				}

				/***
				 * Open the folder for invalid messages
				 ***/

				/* get the folder where we store invalid messages */
				Folder folderInvalid = folderDefault.getFolder("Invalid");
				/* check if it exists */
				if (!folderInvalid.exists()) {
					/* does not exist, create it */
					try {
						if (!folderInvalid.create(Folder.HOLDS_MESSAGES))
						{
							throw new Exception("Creating 'invalid' folder failed.");
						}
					}
					catch (MessagingException me)
					{
						throw new Exception("Could not create 'invalid' folder: " + me.getMessage(), me);
					}
				}
				/* we need to open it READ_WRITE, because we want to move messages we already handled to this folder */
				try {
					folderInvalid.open(Folder.READ_WRITE);
				}
				catch (FolderNotFoundException fnfe)
				{
					throw new Exception("Could not find 'invalid' folder: " + fnfe.getMessage(), fnfe);
				}
				catch (Exception e)
				{
					throw new Exception("Could not open 'invalid' folder: " + e.getMessage(), e);
				}

				/***
				 * Get all new messages
				 ***/

				/* get all messages in the INBOX */
				Message message[] = folderInbox.getMessages();

				/* go through all messages and get the unseen ones (all should be unseen,
				 * as the seen ones get moved to a different folder
				 */
				for (int i = 0; i < message.length; i++) {

					if (message[i].isSet(Flags.Flag.SEEN)) {
						/* this message has been seen, should not happen */
						/* send email to the sender */
						sendErrorMessage(message[i], "This message has already been flagged as seen before being handled and was thus ignored.");
						/* move this message to the invalid folder */
						moveMessage(message[i], folderInbox, folderInvalid);
						/* skip this message */
						continue;
					}

					Space space = null;
					try {
						space = getSpaceFromAddress(message[i]);
					}
					catch (Exception e)
					{
						this.log.error("Could not get space from message: " + e.getMessage());
						/* send email to the sender */
						sendErrorMessage(message[i], "Could not get space from message: " + e.getMessage());
						/* move this message to the invalid folder */
						moveMessage(message[i], folderInbox, folderInvalid);
						/* skip this message */
						continue;
					}

					/* initialise content and attachments */
					blogEntryContent = null;
					attachments = new LinkedList();
					attachmentsInputStreams = new LinkedList();

					containsImage = false;

					/* get the content of this message */
					try {
						Object content = message[i].getContent();
						if (content instanceof Multipart) {
							handleMultipart((Multipart)content);
						} else {
							handlePart(message[i]);
						}
					}
					catch (Exception e)
					{
						this.log.error("Error while getting content of message: " + e.getMessage(), e);
						/* send email to the sender */
						sendErrorMessage(message[i], "Error while getting content of message: " + e.getMessage());
						/* move this message to the invalid folder */
						moveMessage(message[i], folderInbox, folderInvalid);
						/* skip this message */
						continue;
					}

					try {
						createBlogPost(space, message[i]);
					}
					catch (MessagingException me)
					{
						this.log.error("Error while creating blog post: " + me.getMessage(), me);
						/* send email to sender */
						sendErrorMessage(message[i], "Error while creating blog post: " + me.getMessage());
						/* move this message to the invalid folder */
						moveMessage(message[i], folderInbox, folderInvalid);
						/* skip this message */
						continue;
					}

					/* move the message to the processed folder */
					moveMessage(message[i], folderInbox, folderProcessed);

				}

				/* close the folders, expunging deleted messages in the process */
				folderInbox.close(true);
				folderProcessed.close(true);
				folderInvalid.close(true);
				/* close the store */
				store.close();

			}
			else if (config.getProtocol().toLowerCase().equals("pop3"))
			{
				/* get all messages in this POP3 account */
				Message message[] = folderInbox.getMessages();

				/* go through all messages */
				for (int i = 0; i < message.length; i++) {

					Space space = null;
					try {
						space = getSpaceFromAddress(message[i]);
					}
					catch (Exception e)
					{
						this.log.error("Could not get space from message: " + e.getMessage());
						/* send email to the sender */
						sendErrorMessage(message[i], "Could not get space from message: " + e.getMessage());
						/* delete this message */
						message[i].setFlag(Flags.Flag.DELETED, true);
						/* get the next message, this message will be deleted when
						 * closing the folder */
						continue;
					}

					/* initialise content and attachments */
					blogEntryContent = null;
					attachments = new LinkedList();
					attachmentsInputStreams = new LinkedList();

					containsImage = false;

					/* get the content of this message */
					try {
						Object content = message[i].getContent();
						if (content instanceof Multipart) {
							handleMultipart((Multipart)content);
						} else {
							handlePart(message[i]);
						}
					}
					catch (Exception e)
					{
						this.log.error("Error while getting content of message: " + e.getMessage(), e);
						/* send email to the sender */
						sendErrorMessage(message[i], "Error while getting content of message: " + e.getMessage());
						/* delete this message */
						message[i].setFlag(Flags.Flag.DELETED, true);
						/* get the next message, this message will be deleted when
						 * closing the folder */
						continue;
					}

					try {
						createBlogPost(space, message[i]);
					}
					catch (MessagingException me)
					{
						this.log.error("Error while creating blog post: " + me.getMessage(), me);
						/* send email to the sender */
						sendErrorMessage(message[i], "Error while creating blog post: " + me.getMessage());
						/* delete this message */
						message[i].setFlag(Flags.Flag.DELETED, true);
						/* get the next message, this message will be deleted when
						 * closing the folder */
						continue;
					}

					/* finished processing this message, delete it */
					message[i].setFlag(Flags.Flag.DELETED, true);
					/* get the next message, this message will be deleted when
					 * closing the folder */

				}

				/* close the pop3 folder, deleting all messages flagged as DELETED */
				folderInbox.close(true);
				/* close the mail store */
				store.close();

			}
			else
			{
				throw new Exception("Unknown protocol: " + config.getProtocol());
			}

		}
		catch (Exception e)
		{
			/* catch any exception which was not handled so far */
			this.log.error("Error while executing mail2news job: " + e.getMessage(), e);
			JobExecutionException jee = new JobExecutionException("Error while executing mail2news job: " + e.getMessage(), e, false);
			throw jee;
		}
		finally
		{
			/* try to do some cleanup */
			try
			{
				store.close();
			} catch (Exception e) {}
		}
	}

	/**
	 * Send an mail containing the error message back to the
	 * user which sent the given message.
	 *
	 * @param m The message which produced an error while handling it.
	 * @param error The error string.
	 */
	private void sendErrorMessage(Message m, String error) throws Exception // FIXME this method should use the higher level email sending facilities in confluence instead of this low level approach
	{
		/* get the SMTP mail server */
		SMTPMailServer smtpMailServer = MailFactory.getServerManager().getDefaultSMTPMailServer();
		if(smtpMailServer == null) {
			log.warn("Failed to send error message as no SMTP server is configured");
			return;
		}

		if(smtpMailServer.getHostname() == null) {
			log.warn("Failed to send error message as JNDI bound SMTP servers are not supported (JNDI location:<" + smtpMailServer.getJndiLocation() + ">)");
			return;
		}

		/* get system properties */
		Properties props = System.getProperties();

		/* Setup mail server */
		props.put("mail.smtp.host", smtpMailServer.getHostname());

		/* get a session */
		Session session = Session.getDefaultInstance(props, null);
		/* create the message */
		MimeMessage message = new MimeMessage(session);
		message.setFrom(new InternetAddress(smtpMailServer.getDefaultFrom()));
		String senderEmail = getEmailAddressFromMessage(m);
		if (senderEmail == "")
		{
			throw new Exception("Unknown sender of email.");
		}
		message.addRecipient(Message.RecipientType.TO, new InternetAddress(senderEmail));
		message.setSubject("[mail2news] Error while handling message (" + m.getSubject() + ")");
		message.setText("An error occurred while handling your message:\n\n  " + error + "\n\nPlease contact the administrator to solve the problem.\n");

		/* send the message */
		Transport tr = session.getTransport("smtp");
		if(StringUtils.isBlank(smtpMailServer.getPort())) {
			tr.connect(smtpMailServer.getHostname(), smtpMailServer.getUsername(), smtpMailServer.getPassword());
		} else {
			int smtpPort = Integer.parseInt(smtpMailServer.getPort());
			tr.connect(smtpMailServer.getHostname(), smtpPort, smtpMailServer.getUsername(), smtpMailServer.getPassword());
		}
		message.saveChanges();
		tr.sendMessage(message, message.getAllRecipients());
		tr.close();
	}

	/**
	 * Move a given message from one IMAP folder to another. It will be flagged as DELETED
	 * in the originating folder and thus be deleted the next time EXPUNGE is called.
	 *
	 * @param m The message to be moved.
	 * @param from The folder from which the message has to be moved.
	 * @param to The folder to where to move the message.
	 */
	private void moveMessage(Message m, Folder from, Folder to)
	{
		try {
			/* copy the message to the destination folder */
			from.copyMessages(new Message[] {m}, to);
			/* delete the message from the originating folder */
			/* this sets the DELETED flag, the message will be deleted
			 * when expunging the folder */
			m.setFlag(Flags.Flag.DELETED, true);
		}
		catch (Exception e)
		{
			this.log.error("Could not copy message: " + e.getMessage(), e);
			try {
				/* cannot move the message. mark it read so we will not look at it again */
				m.setFlag(Flags.Flag.SEEN, true);
			}
			catch (MessagingException me)
			{
				/* could not set SEEN on the message */
				this.log.error("Could not set SEEN on message.", me);
			}
		}

	}
	/**
	 * Handle a multipart of a email message. May recursively call handleMultipart or
	 * handlePart.
	 *
	 * @param multipart The multipart to handle.
	 * @throws MessagingException
	 * @throws IOException
	 */
	private void handleMultipart(Multipart multipart) throws MessagingException, IOException {

		for (int i = 0, n = multipart.getCount(); i < n; i++) {
			Part p = multipart.getBodyPart(i);
			if (p instanceof Multipart) {
				handleMultipart((Multipart)p);
			}
			else
			{
				handlePart(multipart.getBodyPart(i));
			}
		}
	}

	/**
	 * Handle a part of a email message. This is either displayable text or some MIME
	 * attachment.
	 *
	 * @param part The part to handle.
	 * @throws MessagingException
	 * @throws IOException
	 */
	private void handlePart(Part part) throws MessagingException, IOException {

		/* get the content type of this part */
		String contentType = part.getContentType();

		if (part.getContent() instanceof Multipart)
		{
			handleMultipart((Multipart)part.getContent());
			return;
		}

		log.debug("Content-Type: " + contentType);

		/* check if the content is printable */
		if (contentType.toLowerCase().startsWith("text/plain") && blogEntryContent == null)
		{
			/* get the charset */
			Charset charset = getCharsetFromHeader(contentType);
			/* set the blog entry content to this content */
			blogEntryContent = "";
			InputStream is = part.getInputStream();
			BufferedReader br = null;
			if (charset != null)
			{
				br = new BufferedReader(new InputStreamReader(is, charset));
			}
			else
			{
				br = new BufferedReader(new InputStreamReader(is));
			}
			String currentLine = null;

			while ((currentLine = br.readLine()) != null) {
				blogEntryContent = blogEntryContent.concat(currentLine).concat("\r\n");
			}
		}
		else
		{
			/* the content is not text, so we assume it is some sort of MIME attachment */

			try {
				/* get the filename */
				String fileName = part.getFileName();

				/* no filename, ignore this part */
				if (fileName == null)
				{
					this.log.warn("Attachment with no filename. Ignoring.");
					return;
				}

				/* retrieve an input stream to the attachment */
				InputStream is = part.getInputStream();

				/* clean-up the content type (only the part before the first ';' is relevant) */
				if (contentType.indexOf(';') != -1) {
					contentType = contentType.substring(0, contentType.indexOf(';'));
				}

				if (contentType.toLowerCase().indexOf("image") != -1)
				{
					/* this post contains an image as attachment, add the gallery macro to the blog post */
					containsImage = true;
				}

				ByteArrayInputStream bais = null;
				byte[] attachment = null;
				/* put the attachment into a byte array */
				try
				{
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					byte buf[] = new byte[1024];
					int numBytes;
					while(true)
					{
						numBytes = is.read(buf);
						if (numBytes > 0)
						{
							baos.write(buf, 0, numBytes);
						}
						else
						{
							/* end of stream reached */
							break;
						}
					}
					/* create a new input stream */
					attachment = baos.toByteArray();
					bais = new ByteArrayInputStream(attachment);
					//this.log.info("Attachment size: " + attachment.length);
				}
				catch (Exception e)
				{
					this.log.error("Could not load attachment:" + e.getMessage(), e);
					/* skip this attachment */
					throw e;
				}
				/* create a new attachment */
				Attachment a = new Attachment(fileName, contentType, attachment.length, "Attachment added by mail2news");
				Date d = new Date();
				a.setCreationDate(d);
				a.setLastModificationDate(d);

				/* add the attachment and the input stream to the attachment to the list
				 * of attachments of the current blog entry */
				attachments.addLast(a);
				attachmentsInputStreams.addLast(bais);

			} catch (Exception e) {
				this.log.error("Error while saving attachment: " + e.getMessage(), e);
			}
		}
	}

	/**
	 * Get the charset listed in a "Content-Type" header.
	 * @param contentType The "Content-Type" header.
	 * @return Returns the used charset or null if no information is found.
	 */
	private Charset getCharsetFromHeader(String contentType) {

		StringTokenizer tok = new StringTokenizer(contentType, ";");

		while (tok.hasMoreTokens())
		{
			String token = tok.nextToken().trim();
			if (token.toLowerCase().startsWith("charset"))
			{
				if (token.indexOf('=') != -1)
				{
					String charsetString = token.substring(token.indexOf('=')+1);
					try {
						Charset characterSet = Charset.forName(charsetString);
						return characterSet;
					} catch (Exception e) {
						log.warn("Unsupported charset in email content (" + charsetString + "). Some characters may be wrong.");
						return null;
					}
				}
			}
		}

		return null;
	}

	/**
	 * Get the space key and subsequently the space from the recipient
	 * email address.
	 * The space key is extracted in the form "email+spacekey@domain.net". If the email
	 * address does not contain a "+spacekey", then the application tests if it can
	 * find a spacekey which is equivalent to the local part of the email address.
	 *
	 * @param message The mail message from which to extract the space key.
	 * @return Returns the space
	 * @throws Exception Throws an exception if the space key cannot be extracted or the space cannot be found.
	 */
	private Space getSpaceFromAddress(Message message) throws Exception
	{
		/* list for deferred space keys (see below) */
		List<Space> deferredSpaceKeys = new LinkedList<Space>();

		/* get the To: email addresses */
		Address[] recipientTo = message.getRecipients(Message.RecipientType.TO);

		/* get the CC: email addresses */
		Address[] recipientCc = message.getRecipients(Message.RecipientType.CC);

		/* merge To and CC addresses into one array */
		if (recipientTo == null) // FIXME this should be seriously rewritten
		{
			recipientTo = new Address[0];
		}
		if (recipientCc == null)
		{
			recipientCc = new Address[0];
		}
		Address[] recipient = new Address[recipientTo.length + recipientCc.length];

		System.arraycopy(recipientTo, 0, recipient, 0, recipientTo.length);
		System.arraycopy(recipientCc, 0, recipient, recipientTo.length, recipientCc.length);

		/* check if we have any address */
		if (recipient.length == 0)
		{
			/* no recipient */
			this.log.error("No recipient found in email.");
			/* throw an error */
			throw new Exception("No recipient found in email.");
		}

		/* loop through all addresses until we found one where we can extract
		 * a space key */
		for (int i = 0; i < recipient.length; i++)
		{
			/* retrieve the email address */
			String emailAddress;
			if (recipient[i] instanceof InternetAddress)
			{
				emailAddress = ((InternetAddress)recipient[i]).getAddress();
			}
			else
			{
				emailAddress = recipient[i].toString();
			}

			/* extract the wiki space name */
			Pattern pattern = Pattern.compile("(.+?)([a-zA-Z0-9]+\\+[a-zA-Z0-9]+)@(.+?)");
			Matcher matcher = pattern.matcher(emailAddress);
			String spaceKey = "";
			boolean defer = false;
			if (matcher.matches())
			{
				String tmp = matcher.group(2);
				spaceKey = tmp.substring(tmp.indexOf('+')+1);
			}
			else
			{
				/* the email address is not in the form "aaaa+wikispace@bbb"
				/* fallback: test if there exists a space with a spacekey equal to the
				 *           local part of the email address.
				 */
				spaceKey = emailAddress.substring(0, emailAddress.indexOf('@'));
				defer = true;
			}

			/* check if the space exists */
			Space space = spaceManager.getSpace(spaceKey);
			if(space == null)
			{
				// fall back to look up a personal space
				space = spaceManager.getPersonalSpace(spaceKey);
			}
			if (space == null)
			{
				/* could not find the space specified in the email address */
				this.log.info("Unknown space key: " + spaceKey);
				/* try the next address if possible. */
				continue;
			}

			/* check if it is a fallback space key */
			if (defer)
			{
				/* add to the list of fallback spaces. if we don't find another
				 * space in the form addr+spacekey@..., then we take the first one
				 * of the fallback spaces */
				deferredSpaceKeys.add(space);
			}
			else
			{
				return space;
			}
		}

		/* we did not find a space in the form addr+spacekey@domain.net.
		 * check for a fallback space */
		if (deferredSpaceKeys.size() > 0)
		{
			/* take the first fallback space */
			Space s = deferredSpaceKeys.get(0);
			return s;
		}

		/* did not find any space, not even a fallback key */

		/* Concat the to headers into one string for the error message */
		String[] toHeaders = message.getHeader("To");
		String toString = "";
		for (int j = 0; j < toHeaders.length; j++)
		{
			toString = toString.concat(toHeaders[j]);
			if (j < (toHeaders.length - 1))
			{
				toString = toString.concat(" / ");
			}
		}
		throw new Exception("Could not extract space key from any of the To: addresses: " + toString);
	}

	/**
	 * Create a blog post from the content and the attachments retrieved from a
	 * mail message.
	 * There are only two parameters, the other necessary parameters are global
	 * variables.
	 *
	 * @param space The space where to publish the blog post.
	 * @param m The message which to publish as a blog post.
	 * @throws MessagingException Throws a MessagingException if something goes wrong when getting attributes from the message.
	 */
	private void createBlogPost(Space space, Message m) throws MessagingException
	{
		/* create the blogPost and add values */
		BlogPost blogPost = new BlogPost();
		/* set the creation date of the blog post to the current date */
		blogPost.setCreationDate(new Date());
		/* set the space where to save the blog post */
		blogPost.setSpace(space);
		/* if the gallery macro is set and the post contains an image add the macro */
		MailConfiguration config = configurationManager.getMailConfiguration();
		if (config.getGallerymacro())
		{
			/* gallery macro is set */
			if (containsImage)
			{
				/* post contains an image */
				/* add the macro */
				blogEntryContent = blogEntryContent.concat("{gallery}");
			}
		}
		/* set the blog post content */
		if (blogEntryContent != null)
		{
			blogPost.setContent(blogEntryContent);
		}
		else
		{
			blogPost.setContent("");
		}
		/* set the title of the blog post */
		String title = m.getSubject();
		/* check for illegal characters in the title and replace them with a space */
		/* could be replaced with a regex */
		/* Only needed for Confluence < 4.1 */
		String version = GeneralUtil.getVersionNumber();
		if (!Pattern.matches("^4\\.[1-9]+.*$", version)) {
			char[] illegalCharacters = {':', '@', '/', '%', '\\', '&', '!', '|', '#', '$', '*', ';', '~', '[', ']', '(', ')', '{', '}', '<', '>', '.'};
			for (int i = 0; i < illegalCharacters.length; i++)
			{
				if (title.indexOf(illegalCharacters[i]) != -1)
				{
					title = title.replace(illegalCharacters[i], ' ');
				}
			}
		}
		blogPost.setTitle(title);

		/* set creating user */
		String creatorEmail = getEmailAddressFromMessage(m);

		String creatorName = "Anonymous";
		User creator = null;
		if (creatorEmail != "")
		{
			SearchResult sr = userAccessor.getUsersByEmail(creatorEmail);

			Pager p = sr.pager();
			List l = p.getCurrentPage();

			if (l.size() == 1)
			{
				/* found a matching user for the email address of the sender */
				creator = (User)l.get(0);
				creatorName = creator.getName();
			}
		}

		//this.log.info("creatorName: " + creatorName);
		//this.log.info("creator: " + creator);
		blogPost.setCreatorName(creatorName);

		if (creator != null)
		{
			AuthenticatedUserThreadLocal.setUser(creator);
		}
		else
		{
			//this.log.info("Resetting authenticated user.");
			AuthenticatedUserThreadLocal.setUser(null);
		}

		/* save the blog post */
		pageManager.saveContentEntity(blogPost, null);

		/* set attachments of this blog post */
		/* we have to save the blog post before we can add the
		 * attachments, because attachments need to be attached to
		 * a content. */
		Attachment[] a = new Attachment[attachments.size()];
		a = (Attachment[])attachments.toArray(a);
		for (int j = 0; j < a.length; j++)
		{
			InputStream is = (InputStream)attachmentsInputStreams.get(j);

			/* save the attachment */
			try
			{
				/* set the creator of the attachment */
				a[j].setCreatorName(creatorName);
				/* set the content of this attachment to the newly saved blog post */
				a[j].setContent(blogPost);
				attachmentManager.saveAttachment(a[j], null, is);
			}
			catch (Exception e)
			{
				this.log.error("Could not save attachment: " + e.getMessage(), e);
				/* skip this attachment */
				continue;
			}

			/* add the attachment to the blog post */
			blogPost.addAttachment(a[j]);
		}
	}

	private String getEmailAddressFromMessage(Message m) throws MessagingException
	{
		Address[] sender = m.getFrom();
		String creatorEmail = "";
		if (sender.length > 0) {
			if (sender[0] instanceof InternetAddress) {
				creatorEmail = ((InternetAddress) sender[0]).getAddress();
			} else {
				try {
					InternetAddress ia[] = InternetAddress.parse(sender[0].toString());
					if (ia.length > 0) {
						creatorEmail = ia[0].getAddress();
					}
				} catch (AddressException ae) {
				}
			}
		}

		return creatorEmail;
	}

	/**
	 * Override the method which indicates whether this job can
	 * be run several times simultaneously. We do not allow this
	 * job to be run several times at once, because this would
	 * generate all sorts of problems with the access to the mailbox.
	 *
	 * @return Returns whether concurrent execution is allowed.
	 */
	protected boolean allowConcurrentExecution() {
		return false;
	}

	/**
	 * This method is automatically called by Confluence to pass the
	 * PageManager of this Confluence instance.
	 *
	 * @param pageManager The PageManager of this Confluence instance
	 */
	public void setPageManager(PageManager pageManager)
	{
		this.pageManager = pageManager;
	}

	/**
	 * This method is automatically called by Confluence to pass the
	 * SpaceManager of this Confluence instance.
	 *
	 * @param pageManager The PageManager of this Confluence instance
	 */
	public void setSpaceManager(SpaceManager spaceManager)
	{
		this.spaceManager = spaceManager;
	}

	/**
	 * This method is automatically called by Confluence to pass the
	 * AttachmentManager of this Confluence instance.
	 *
	 * @param attachmentManager The AttachmentManager of this Confluence instance
	 */
	public void setAttachmentManager(AttachmentManager attachmentManager)
	{
		this.attachmentManager = attachmentManager;
	}

	/**
	 * This method is automatically called by Confluence to pass the
	 * UserAccessor of this Confluence instance.
	 *
	 * @param userAccessor The UserAccessor of this Confluence instance
	 */
	public void setUserAccessor(UserAccessor userAccessor)
	{
		this.userAccessor = userAccessor;
	}
}
