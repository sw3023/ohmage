/*******************************************************************************
 * Copyright 2012 The Regents of the University of California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.ohmage.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import com.sun.mail.smtp.SMTPTransport;

import jbcrypt.BCrypt;
import net.tanesha.recaptcha.ReCaptchaImpl;
import net.tanesha.recaptcha.ReCaptchaResponse;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.cache.PreferenceCache;
import org.ohmage.cache.UserBin;
import org.ohmage.domain.Clazz;
import org.ohmage.domain.KeycloakUser;
import org.ohmage.domain.User;
import org.ohmage.domain.UserInformation;
import org.ohmage.domain.UserInformation.UserPersonal;
import org.ohmage.domain.UserSummary;
import org.ohmage.domain.campaign.Campaign;
import org.ohmage.exception.CacheMissException;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.DomainException;
import org.ohmage.exception.ServiceException;
import org.ohmage.query.IImageQueries;
import org.ohmage.query.IUserCampaignQueries;
import org.ohmage.query.IUserClassQueries;
import org.ohmage.query.IUserImageQueries;
import org.ohmage.query.IUserQueries;
import org.ohmage.query.impl.QueryResultsList;
import org.ohmage.request.InputKeys;
import org.ohmage.util.CookieUtils;
import org.ohmage.util.StringUtils;
import org.ohmage.util.MailUtils;



/**
 * This class contains the services for users.
 * 
 * @author John Jenkins
 */
public final class UserServices {
	private static final Logger LOGGER = Logger.getLogger(UserServices.class);
	/* HT
	private static final String MAIL_PROTOCOL = "smtp";
	private static final String MAIL_PROPERTY_HOST = 
			"mail." + MAIL_PROTOCOL + ".host";
	private static final String MAIL_PROPERTY_PORT =
			"mail." + MAIL_PROTOCOL + ".port";
	private static final String MAIL_PROPERTY_SSL_ENABLED =
			"mail." + MAIL_PROTOCOL + ".ssl.enable";
	*/
	
	private static final String MAIL_REGISTRATION_TEXT_TOS = "<_TOS_>";
	private static final String MAIL_REGISTRATION_TEXT_REGISTRATION_LINK =
			"<_REGISTRATION_LINK_>";
	
	private static final long REGISTRATION_DURATION = 1000 * 60 * 60 * 24;
	
	final static char[] CHARS_TEMPORARY_PASSWORD = 
			new char[] { 
				'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
				'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
				'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 
				'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 
				'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4', '5', '6', '7', 
				'8', '9' };
	
	private static UserServices instance;
	
	private IUserQueries userQueries;
	private IUserCampaignQueries userCampaignQueries;
	private IUserClassQueries userClassQueries;
	private IUserImageQueries userImageQueries;
	private IImageQueries imageQueries;
	
	/**
	 * Default constructor. Privately instantiated via dependency injection
	 * (reflection).
	 * 
	 * @throws IllegalStateException if an instance of this class already
	 * exists
	 * 
	 * @throws IllegalArgumentException if iUserQueries or iUserClassQueries
	 * or iUserCampaignQueries is null
	 */
	private UserServices(IUserQueries iUserQueries, 
			IUserCampaignQueries iUserCampaignQueries, IUserClassQueries iUserClassQueries,
			IUserImageQueries iUserImageQueries, IImageQueries iImageQueries) {
		
		if(instance != null) {
			throw new IllegalStateException("An instance of this class already exists.");
		}
		instance = this;
		
		if(iUserQueries == null) {
			throw new IllegalArgumentException("An instance of IUserQueries is required.");
		}
		if(iUserCampaignQueries == null) {
			throw new IllegalArgumentException("An instance of IUserCampaignQueries is required.");
		}
		if(iUserClassQueries == null) {
			throw new IllegalArgumentException("An instance of IUserClassQueries is required.");
		}
		if(iUserImageQueries == null) {
			throw new IllegalArgumentException("An instance of IUserImageQueries is required.");
		}
		if(iImageQueries == null) {
			throw new IllegalArgumentException("An instance of IIimageQueries is required.");
		}
		
		userQueries = iUserQueries;
		userCampaignQueries = iUserCampaignQueries;
		userClassQueries = iUserClassQueries;
		userImageQueries = iUserImageQueries;
		imageQueries = iImageQueries;
	}
	
	/**
	 * @return  Returns the singleton instance of this class.
	 */
	public static UserServices instance() {
		return instance;
	}

	/**
	 * Creates a new user.
	 * 
	 * @param username The username for the new user.
	 * 
	 * @param password The password for the new user.
	 * 
	 * @param emailAddress The user's email address or null.
	 * 
	 * @param admin Whether or not the user should initially be an admin.
	 * 
	 * @param enabled Whether or not the user should initially be enabled.
	 * 
	 * @param newAccount Whether or not the new user must change their password
	 * 					 before using any other APIs.
	 * 
	 * @param campaignCreationPrivilege Whether or not the new user is allowed
	 * 									to create campaigns.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void createUser(
			final String username, 
			final String password, 
			final String emailAddress,
			final Boolean admin, 
			final Boolean enabled, 
			final Boolean newAccount, 
			final Boolean campaignCreationPrivilege,
			final boolean storeInitialPassword,
			final Boolean externalAccount)
			throws ServiceException {
		
		try {
			String hashedPassword;
			// Store a static string for password column if user is external
			if (externalAccount) {
				hashedPassword = 
						KeycloakUser.KEYCLOAK_USER_PASSWORD; 
			} else {
				hashedPassword =
						BCrypt.hashpw(password, BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			}
			
			userQueries
				.createUser(
					username, 
					((storeInitialPassword) ? password : null),
					hashedPassword, 
					emailAddress, 
					admin, 
					enabled, 
					newAccount, 
					campaignCreationPrivilege,
					externalAccount);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Creates a new user.
	 * 
	 * @param username The username for the new user.
	 * 
	 * @param password The password for the new user.
	 * 
	 * @param emailAddress The user's email address or null.
	 * 
	 * @param admin Whether or not the user should initially be an admin.
	 * 
	 * @param enabled Whether or not the user should initially be enabled.
	 * 
	 * @param newAccount Whether or not the new user must change their password
	 * 					 before using any other APIs.
	 * 
	 * @param campaignCreationPrivilege Whether or not the new user is allowed
	 * 									to create campaigns.
	 * 
	 * @return Whether or not the user was successfully created.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public boolean createUser(
			final String username, 
			final String password, 
			final String emailAddress,
			final Boolean admin, 
			final Boolean enabled, 
			final Boolean newAccount, 
			final Boolean campaignCreationPrivilege,
			final boolean storeInitialPassword,
			final Boolean externalAccount,
			final UserPersonal personalInfo)
			throws ServiceException {
		
		try {
			String hashedPassword;

			// Store a static string for password column if user is external
			if (externalAccount) {
				hashedPassword = 
						KeycloakUser.KEYCLOAK_USER_PASSWORD; 
			} else {
				hashedPassword =
						BCrypt.hashpw(password, BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			}
			
			return
				userQueries
					.createUser(
						username, 
						((storeInitialPassword) ? password : null),
						hashedPassword, 
						emailAddress, 
						admin, 
						enabled, 
						newAccount, 
						campaignCreationPrivilege,
						externalAccount,
						personalInfo);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Registers the user in the system by first creating the user whose 
	 * account is disabled. It then creates an entry in the registration cache
	 * with the key for the user to activate their account. Finally, it sends 
	 * an email to the user with a link that includes the activation key.
	 * 
	 * @param username The username of the new user.
	 * 
	 * @param password The plain-text password for the new user.
	 * 
	 * @param emailAddress The email address for the user.
	 * 
	 * @throws ServiceException There was a configuration issue with the mail
	 * 							server or if there was an issue in the Query
	 * 							layer.
	 */
	public void createUserRegistration(
			final String username,
			final String password,
			final String emailAddress) 
			throws ServiceException {
		
		try {
			// Generate a random registration ID from the username and some 
			// random bits.
			MessageDigest digest = MessageDigest.getInstance("SHA-512");
			digest.update(username.getBytes());
			digest.update(UUID.randomUUID().toString().getBytes());
			byte[] digestBytes = digest.digest();
			
			StringBuffer buffer = new StringBuffer();
	        for(int i = 0; i < digestBytes.length; i++) {
	        	buffer.append(
	        			Integer.toString(
	        					(digestBytes[i] & 0xff) + 0x100, 16)
	        						.substring(1));
	        }
			String registrationId = buffer.toString();
			
			// Hash the password.
			String hashedPassword = 
					BCrypt.hashpw(
							password, 
							BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			
			// Create the user in the database with all of its connections.
			userQueries.createUserRegistration(
					username, 
					hashedPassword, 
					emailAddress, 
					registrationId.toString());

			// Build the registration text.
			String registrationText;
			try {
				registrationText = PreferenceCache.instance().lookup(
					PreferenceCache.KEY_MAIL_REGISTRATION_TEXT);
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_REGISTRATION_TEXT,
					e);
			}

			// Compute the registration link.
			StringBuilder registrationLink = new StringBuilder("<a href=\"");
			// Get this machine's root URL.
			try {
				registrationLink.append(CookieUtils.buildServerRootUrl());
			}
			catch(DomainException e) {
				throw
					new ServiceException(
						"Could not build the root server URL.",
						e);
			}
			// Get the registration activation function.
			String activationFunction;
                        try {
                                activationFunction = PreferenceCache.instance().lookup(
                                        PreferenceCache.KEY_MAIL_REGISTRATION_ACTIVATION_FUNCTION);
                        }
                        catch(CacheMissException e) {
				//Set to #activate for backwards compatability
				activationFunction = "#activate";
				LOGGER.info("The mail property is not in the preference table:" +
                                                PreferenceCache.KEY_MAIL_REGISTRATION_ACTIVATION_FUNCTION +
						", setting manually");
                        }
			registrationLink.append(activationFunction);
			registrationLink.append('?');
			registrationLink.append(InputKeys.USER_REGISTRATION_ID);
			registrationLink.append('=');
			registrationLink.append(registrationId);
			registrationLink.append("\">I Agree</a>");
			registrationText =
					registrationText.replace(
							MAIL_REGISTRATION_TEXT_REGISTRATION_LINK, 
							registrationLink);
			
			// Get the terms of service.
			String termsOfService;
			try {
				termsOfService =
					"<tt>" +
						PreferenceCache.instance().lookup(
							PreferenceCache.KEY_TERMS_OF_SERVICE)
						+ "</tt>";
				termsOfService = termsOfService.replace("\n", "<br />");
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_TERMS_OF_SERVICE,
					e);
			}
			// Add the terms of service to the email.
			registrationText =
				registrationText
					.replace(MAIL_REGISTRATION_TEXT_TOS, termsOfService);
			
			// Get the session.
			Session smtpSession = MailUtils.getMailSession();
			// Create the message.
			MimeMessage message = new MimeMessage(smtpSession);
			
			try {
				// set up properties
				MailUtils.setMailMessageTo(message, emailAddress);
				MailUtils.setMailMessageFrom(message, PreferenceCache.KEY_MAIL_REGISTRATION_SENDER);
				MailUtils.setMailMessageSubject(message, PreferenceCache.KEY_MAIL_REGISTRATION_SUBJECT);	

				try {
					// Set the content of the message.
					message.setContent(registrationText, "text/html");
				}
				catch(MessagingException e) {
					throw new ServiceException(
							"There was an error constructing the message.",
							e);
				}
					
				// send message
				MailUtils.sendMailMessage(smtpSession, message);

			} catch (ServiceException e) {
				throw new ServiceException("Cannot successfully send the password recovery notification.", e);
			}

			/*
			// Add the recipient.
			try {
				message.setRecipient(
						Message.RecipientType.TO, 
						new InternetAddress(emailAddress));
			}
			catch(AddressException e) {
				throw new ServiceException(
						"The destination address is not a valid email address.",
						e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not add the recipient to the message.",
						e);
			}
			
			// Add the sender.
			try {
				message.setFrom(
						new InternetAddress(
								PreferenceCache.instance().lookup(
									PreferenceCache.KEY_MAIL_REGISTRATION_SENDER)));
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_REGISTRATION_SENDER,
					e);
			}
			catch(AddressException e) {
				throw new ServiceException(
						"The origin address is not a valid email address.",
						e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not update the sender's email address.",
						e);
			}
			
			// Set the subject.
			try {
				message.setSubject(
						PreferenceCache.instance().lookup(
							PreferenceCache.KEY_MAIL_REGISTRATION_SUBJECT));
			}
			catch(CacheMissException e) {
				throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_REGISTRATION_SUBJECT,
					e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not set the subject on the message.",
						e);
			}
			
			try {
				// Set the content of the message.
				message.setContent(registrationText, "text/html");
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"There was an error constructing the message.",
						e);
			}
			
			try {
				message.saveChanges();
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not save the changes to the message.",
						e);
			}
			
			MailUtils.sendMailMessage(smtpSession, message);
			 */

		}
		catch(NoSuchAlgorithmException e) {
			throw new ServiceException("The hashing algorithm is unknown.", e);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		} 
	}
	
	/**
	 * Verifies that self-registration is allowed.
	 * 
	 * @throws ServiceException Self-registration is not allowed, or the 
	 * 							self-registration flag is missing or invalid.
	 */
	public void verifySelfRegistrationAllowed() throws ServiceException {
		try {
			Boolean selfRegistrationAllowed =
				StringUtils.decodeBoolean(
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_SELF_REGISTRATION_ALLOWED));
				
			if(selfRegistrationAllowed == null) {
				throw new ServiceException(
					"The self-registration flag is not a valid boolean: " +
						PreferenceCache.KEY_SELF_REGISTRATION_ALLOWED);	
			}
			else if(! selfRegistrationAllowed) {
				throw new ServiceException(
					ErrorCode.SERVER_SELF_REGISTRATION_NOT_ALLOWED,
					"Self-registration is not allowed.");
			}
		}
		catch(CacheMissException e) {
			throw new ServiceException(
				"Could not retrieve the 'known' key: " +
					PreferenceCache.KEY_SELF_REGISTRATION_ALLOWED,
				e);
		}
	}
	
	/**
	 * Verifies that the given captcha information is valid.
	 * 
	 * @param remoteAddr The address of the remote host.
	 * 
	 * @param challenge The challenge value.
	 * 
	 * @param response The response value.
	 * 
	 * @throws ServiceException Thrown if the private key is missing or if the
	 * 							response is invalid.
	 */
	public void verifyCaptcha(
			final String version,
			final String remoteAddr,
			final String challenge,
			final String response)
			throws ServiceException {
		try { } catch (Exception e) { throw new ServiceException(ErrorCode.SERVER_INVALID_CAPTCHA,"The reCaptcha response was invalid."); }
//		if (version.startsWith("1.")){
//		
//			ReCaptchaImpl reCaptcha = new ReCaptchaImpl();
//			try {
//				reCaptcha.setPrivateKey(
//					PreferenceCache.instance().lookup(
//							PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE));
//			}
//			catch(CacheMissException e) {
//				throw new ServiceException(
//						"The ReCaptcha key is missing from the preferences: " +
//								PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE,
//								e);
//			}
//		
//			ReCaptchaResponse reCaptchaResponse = 
//					reCaptcha.checkAnswer(remoteAddr, challenge, response);
//		
//			if(! reCaptchaResponse.isValid()) {
//				throw new ServiceException(
//						ErrorCode.SERVER_INVALID_CAPTCHA,
//						"The reCaptcha response was invalid.");
//			}
//		} else if (version.startsWith("2.")) {
//			verifyCaptchaV2(remoteAddr, response);
//		
//		} else {
//			throw new ServiceException(
//					ErrorCode.SERVER_INVALID_CAPTCHA,
//					"Invalid Captcha version: "+ version);	
//		}
		
	}
	
	/**
	 * Verifies that the given captcha information is valid.
	 * 
	 * @param remoteAddr The address of the remote host.
	 * 
	 * @param challenge The challenge value.
	 * 
	 * @param response The response value.
	 * 
	 * @throws ServiceException Thrown if the private key is missing or if the
	 * 							response is invalid.
	 */
	public void verifyCaptchaV2(
			final String remoteAddr,
			final String response)
			throws ServiceException {
		
		String secretKey;
		URL url = null;
		String postData = null;
		
		try {
			secretKey = PreferenceCache.instance().lookup(
							PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE);
		}
		catch(CacheMissException e) {
			throw new ServiceException(
					"The ReCaptcha key is missing from the preferences: " +
						PreferenceCache.KEY_RECAPTCHA_KEY_PRIVATE,
					e);
		}
		
		if ((response == null) || (response.length()==0))
			throw new ServiceException(
					ErrorCode.SERVER_INVALID_CAPTCHA,
					"The reCaptcha response was invalid.");
	    
		try { 
			// prepare post data
			StringBuilder param = new StringBuilder();
			param.append("secret=" + URLEncoder.encode(secretKey.toString(), "UTF-8"));
			param.append("&response=" + URLEncoder.encode(response.toString(), "UTF-8"));
			if ((remoteAddr != null) && (remoteAddr.length() > 0))
				param.append("&remoteip=" + URLEncoder.encode(response.toString(), "UTF-8"));
			postData = param.toString();
		
		    url = new URL("https://www.google.com/recaptcha/api/siteverify");
		    HttpURLConnection connection = (HttpURLConnection) url.openConnection(); 
		    connection.setDoOutput(true); 
		    connection.setInstanceFollowRedirects(false);
		    connection.setRequestMethod("POST"); 
		    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
		    connection.setRequestProperty("charset", "utf-8");
		    connection.setRequestProperty("Content-Length", Integer.toString(postData.length()));
		    connection.setUseCaches(false);

		    OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
		    out.write(postData); 
		    out.close(); 
	    
		    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		    StringBuilder strBuilder = new StringBuilder(); 
		    String tmp_str; 

		    while ((tmp_str = in.readLine()) != null)
		    	strBuilder.append(tmp_str);
		    in.close(); 
		    
		    String result = strBuilder.toString();
	    	if (result.length() == 0) { 
				throw new ServiceException(
						ErrorCode.SERVER_INVALID_CAPTCHA,
						"The reCaptcha response was invalid.");
	    	}   
		    //	    System.out.println(result); 
	    	
		    try {
		    	JSONObject jsonResult = new JSONObject(result);
				 
		    	if (! jsonResult.getBoolean("success")) {
					throw new JSONException("Recaptcha failed to verify");
		    	}
		    	
		    } catch (JSONException e) { 
				throw new ServiceException(
						ErrorCode.SERVER_INVALID_CAPTCHA,
						"The reCaptcha response was invalid.");
		    }

		} catch (MalformedURLException  e) {
			throw new ServiceException(
					"MalformedURL: " + url.toString(),
					e);
		} catch (UnsupportedEncodingException e) { 
			throw new ServiceException(
					"UnsupportedEncoding: Can't encode post-data: " + postData,
					e);
		} catch (IOException e) {
			throw new ServiceException(
					"IOEncoding: URL=" + url.toString() + ", post-data: " + postData,
					e);
		}
	}
	
	/**
	 * Checks that a user's existence matches that of 'shouldExist'.
	 * 
	 * @param username The username of the user in question.
	 * 
	 * @param shouldExist Whether or not the user should exist.
	 * 
	 * @throws ServiceException Thrown if there was an error, if the user 
	 * 							exists but shouldn't, or if the user doesn't
	 * 							exist but should.
	 */
	public void checkUserExistance(final String username, 
			final boolean shouldExist) throws ServiceException {
		
		try {
			if(userQueries.userExists(username)) {
				if(! shouldExist) {
					throw new ServiceException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The following user already exists: " + username);
				}
			}
			else {
				if(shouldExist) {
					throw new ServiceException(
							ErrorCode.USER_INVALID_USERNAME, 
							"The following user does not exist: " + username);
				}
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Checks that a Collection of users' existence matches that of 
	 * 'shouldExist'.
	 * 
	 * @param usernames A Collection of usernames to check that each exists.
	 * 
	 * @param shouldExist Whether or not all of the users should exist or not.
	 * 
	 * @throws ServiceException Thrown if there was an error, if one of the 
	 * 							users should have existed and didn't, or if one 
	 * 							of the users shouldn't exist but does.
	 */
	public void verifyUsersExist(final Collection<String> usernames, 
			final boolean shouldExist) throws ServiceException {
		
		for(String username : usernames) {
			checkUserExistance(username, shouldExist);
		}
	}
	
	/**
	 * Assumes the user exists. Checks that a parameterized email address 
	 * matches the user's actual email address. To check if a user doesn't have
	 * an email address, pass NULL in for the 'emailAddress'. Otherwise, a 
	 * string comparison is done between the two email addresses to ensure that
	 * they are identical.
	 * 
	 * @param username The user's username.
	 * 
	 * @param emailAddress The user's expected email address.
	 * 
	 * @throws ServiceException The email addresses didn't match or there was
	 * 							an error.
	 */
	public void isUserEmailCorrect(
			final String username, 
			final String emailAddress)
			throws ServiceException {
		
		try {
			String actualEmailAddress = userQueries.getEmailAddress(username);
			
			if((actualEmailAddress == null) && (emailAddress == null)) {
				// The email addresses match in that the user doesn't have one
				// and none was passed in.
			}
			else if((emailAddress == null) || (actualEmailAddress == null) ||
				(! actualEmailAddress.toLowerCase().equals(emailAddress.toLowerCase()))) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_EMAIL_ADDRESS,
						"The email addresses don't match.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Returns the user's email address or null if they don't have one.
	 * 
	 * @param username
	 *        The username of the user whose email address is desired.
	 * 
	 * @return The user's email address or null if they don't have one or don't
	 *         exist.
	 * 
	 * @throws ServiceException
	 *         There was an internal error.
	 */
	public String getUserEmail(final String username) throws ServiceException {
		try {
			return userQueries.getEmailAddress(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * THIS SHOULD NEVER BE USED.
	 * 
	 * @param username
	 *        The user's username.
	 * 
	 * @return The user's plain-text password or null if the user is unknown or
	 *         their plain-text password was not stored.
	 * 
	 * @throws ServiceException
	 *         There was a problem getting the password.
	 * 
	 */
	public String getInitialPassword(
		final String username)
		throws ServiceException {
		
		try {
			return userQueries.getInitialPassword(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Checks if the user is an admin.
	 * 
	 * TODO Any use case that involves an API call which isn't exclusively
	 * for admins can be migrated to use the isUserAnAdmin() method.
	 * 
	 * @throws ServiceException Thrown if there was an error or if the user is
	 * 							not an admin.
	 */
	public void verifyUserIsAdmin(final String username) 
			throws ServiceException {
		
		try {
			if(! userQueries.userIsAdmin(username)) {
				throw new ServiceException(
						ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
						"The user is not an admin."
					);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Checks if the user is an admin.
	 * 
	 * @return true if the user has admin privileges, false otherwise
	 * 
	 * @throws ServiceException Thrown if there was an error.
	 */
	public boolean isUserAnAdmin(final String username) 
			throws ServiceException {
		
		try {
			return userQueries.userIsAdmin(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Checks if the user is an external (keycloak) user.
	 * 
	 * @return true if the user is external, false otherwise
	 * 
	 * @throws ServiceException Thrown if there was an error.
	 */
	public boolean isUserExternal(final String username) 
			throws ServiceException {
		
		try {
			return userQueries.userIsExternal(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that the user can create campaigns.
	 * 
	 * @param username The username of the user whose campaign creation ability
	 * 				   is being checked.
	 * 
	 * @throws ServiceException Thrown if there is an error or if the user is
	 * 							not allowed to create campaigns.
	 */
	public void verifyUserCanCreateCampaigns(final String username) 
			throws ServiceException {
		
		try {
			if(! userQueries.userCanCreateCampaigns(username)) {
				throw new ServiceException(
						ErrorCode.CAMPAIGN_INSUFFICIENT_PERMISSIONS, 
						"The user does not have permission to create new campaigns.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that the user can create classes.
	 * 
	 * @param username
	 *        The username of the user whose class creation ability is being
	 *        checked.
	 * 
	 * @throws ServiceException
	 *         Thrown if there is an error or if the user is not allowed to
	 *         create classes.
	 */
	public void verifyUserCanCreateClasses(
		final String username) 
		throws ServiceException {
		
		try {
			if(!
				(userQueries.userIsAdmin(username) || 
				userQueries.userCanCreateClasses(username))) {
				
				throw
					new ServiceException(
						ErrorCode.CLASS_INSUFFICIENT_PERMISSIONS, 
						"The user does not have permission to create new " +
							"classes.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that the user can delete a class.
	 * 
	 * @param username
	 *        The username of the user whose class deletion ability is being
	 *        checked.
	 * 
	 * @param classId
	 *        The ID of the class to be deleted.
	 * 
	 * @throws ServiceException
	 *         Thrown if there is an error or if the user is not allowed to
	 *         create classes.
	 */
	public void verifyUserCanDeleteClasses(
		final String username,
		final String classId) 
		throws ServiceException {
		
		try {
			if(!
				(userQueries.userIsAdmin(username) || 
				(
					userQueries.userCanCreateClasses(username) &&
					Clazz
						.Role
						.PRIVILEGED
						.equals(
							userClassQueries
								.getUserClassRole(classId, username))))) {
				
				throw
					new ServiceException(
						ErrorCode.CLASS_INSUFFICIENT_PERMISSIONS, 
						"The user does not have permission to delete the " +
							"class: " +
							classId);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that a user can setup a new user.
	 * 
	 * @param username
	 *        The requesting user's username.
	 * 
	 * @throws ServiceException
	 *         The user cannot setup a new user.
	 */
	public void verifyUserCanSetupUsers(
		final String username)
		throws ServiceException {
		
		try {
			if(
				(! userQueries.userIsAdmin(username)) &&
				(! userQueries.userCanSetupUsers(username))) {
				
				throw
					new ServiceException(
						ErrorCode.CLASS_INSUFFICIENT_PERMISSIONS, 
						"The user does not have permission to setup a new " +
							"user.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Aliens.
	 * 
	 * @param requester
	 *        The user that is attempting to change another user's password.
	 * 
	 * @param requestee
	 *        The user that whose password is being changed.
	 * 
	 * @throws ServiceException
	 *         The requesting user cannot change the requestee user's password.
	 */
	public void verifyUserCanChangeOtherUsersPassword(
		final String requester,
		final String requestee)
		throws ServiceException {
		
		try {
			// If the user is not an admin, make sure they have the
			// Mobilize-specific requirements.
			if(! userQueries.userIsAdmin(requester)) {
				// Get the list of class IDs where the requesting user is
				// privileged.
				Collection<String> requesterClassIds =
					userClassQueries
						.getClassIdsForUserWithRole(
							requester,
							Clazz.Role.PRIVILEGED);

				// Get the map of class IDs and the requestee's role in that
				// class.
				Map<String, Clazz.Role> requesteeClassIds =
					userClassQueries
						.getClassesAndRolesForUser(requestee);
				
				// If there is no intersection between the two class lists,
				// then fail out.
				boolean requesterPrivilegedRequesteeRestricted = false;
				for(String classId : requesterClassIds) {
					if(
						requesteeClassIds.containsKey(classId) &&
						Clazz
							.Role
							.RESTRICTED
							.equals(requesteeClassIds.get(classId))) {
						
						requesterPrivilegedRequesteeRestricted = true;
						break;
					}
				}
				if(! requesterPrivilegedRequesteeRestricted) {
					throw
						new ServiceException(
							ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
							"The user does not have permission to change " +
								"another user's password.",
							"The requesting user is not privileged in any " +
								"class where the requestee user is" +
								"restricted.");
				}
				
				// The requestee user cannot be privileged in any class.
				for(Clazz.Role role : requesteeClassIds.values()) {
					if(Clazz.Role.PRIVILEGED.equals(role)) {				
						throw
							new ServiceException(
								ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
								"The user does not have permission to " +
									"change another user's password.",
								"The requestee is privileged in a class.");
					}
				}
				
				// The user must have been setup via the user/setup call. If,
				// and only if, this happened would their plain-text password
				// be stored in the database.
				if(userQueries.getInitialPassword(requestee) == null) {
					throw
						new ServiceException(
							ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
							"The user does not have permission to change " +
								"another user's password.",
							"The requestee was not setup via the user/setup " +
								"API.");
				}
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Validates that a registration ID still exists, hasn't been used, and 
	 * hasn't expired.
	 * 
	 * @param registrationId The registration's unique identifier.
	 * 
	 * @throws ServiceException The registration doesn't exist or is invalid or
	 * 							there was an error.
	 */
	public void validateRegistrationId(
			final String registrationId) 
			throws ServiceException {
		
		try {
			if(! userQueries.registrationIdExists(registrationId)) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"No such registration ID exists.");
			}
			
			if(userQueries.getRegistrationAcceptedDate(registrationId) != null) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"This registration ID has already been used to activate an account.");
			}
			
			long earliestTime = (new Date()).getTime() - REGISTRATION_DURATION;
			
			if(userQueries.getRegistrationRequestedDate(registrationId).getTime() < earliestTime) {
				throw new ServiceException(
						ErrorCode.USER_INVALID_REGISTRATION_ID,
						"The registration ID has expired.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that a given user is allowed to read the personal information
	 * about a group of users.
	 * 
	 * @param username The username of the reader.
	 * 
	 * @param usernames The usernames of the readees.
	 * 
	 * @throws ServiceException There was an error or the user is not allowed 
	 * 							to read the personal information of one or more
	 * 							of the users.
	 */
	public void verifyUserCanReadUsersPersonalInfo(
			final String username, final Collection<String> usernames) 
			throws ServiceException {
		
		if(
			(usernames == null) ||
			(usernames.size() == 0) ||
			(
				(usernames.size() == 1) && 
				usernames.iterator().next().equals(username)
			)) {
			
			return;
		}
		
		Set<String> supervisorCampaigns = 
			UserCampaignServices.instance().getCampaignsForUser(username, 
					null, null, null, null, null, null, 
					Campaign.Role.SUPERVISOR);
		
		Set<String> privilegedClasses = 
			UserClassServices.instance().getClassesForUser(
					username, 
					Clazz.Role.PRIVILEGED);
		
		for(String currUsername : usernames) {
			if(UserCampaignServices.instance().getCampaignsForUser( 
					currUsername, supervisorCampaigns, privilegedClasses, 
					null, null, null, null, null).size() == 0) {
				
				throw new ServiceException(
						ErrorCode.USER_INSUFFICIENT_PERMISSIONS, 
						"The user is not allowed to view personal information about a user in the list: " + 
							currUsername);
			}
		}
	}

	/**
	 * Verifies that if the user already has personal information in which it 
	 * is acceptable to update any combination of the pieces or that they 
	 * supplied all necessary pieces to update the information.
	 * 
	 * @param username The username of the user whose personal information is
	 * 				   being queried.
	 * 
	 * @param firstName The new first name of the user or null if the first 
	 * 					name is not being updated.
	 * 
	 * @param lastName The new last name of the user or null if the last name 
	 * 				   is not being updated.
	 * 
	 * @param organization The new organization of the user or null if the
	 * 					   organization is not being updated.
	 * 
	 * @param personalId The new personal ID of the user or null if the 
	 * 					 personal ID is not being updated.
	 * 
	 * @throws ServiceException The user doesn't have personal information in
	 * 							the system and is attempting to update some 
	 * 							fields but not all of them. If the user doesn't
	 * 							have personal information already, they must
	 * 							create a new one with all of the information. 
	 * 							Or there was an error.
	 */
	public void verifyUserHasOrCanCreatePersonalInfo(
			final String username, 
			final String firstName,
			final String lastName,
			final String organization,
			final String personalId) 
			throws ServiceException {
		
		// If the user already has personal information, then they are allowed
		// to edit it as they wish.
		try {
			if(userQueries.userHasPersonalInfo(username)) {
				return;
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// If they are all null and the user isn't trying to update the 
		// personal information, then that is fine.
		if((firstName == null) &&
				(lastName == null) &&
				(organization == null) &&
				(personalId == null)) {
			
			return;
		}
		
		if(firstName == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_FIRST_NAME_VALUE, 
					"The user doesn't have personal information yet, and a first name is necessary to create one.");
		}
		else if(lastName == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_LAST_NAME_VALUE, 
					"The user doesn't have personal information yet, and a last name is necessary to create one.");
		}
		else if(organization == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_ORGANIZATION_VALUE, 
					"The user doesn't have personal information yet, and an organization is necessary to create one.");
		}
		else if(personalId == null) {
			throw new ServiceException(
					ErrorCode.USER_INVALID_PERSONAL_ID_VALUE, 
					"The user doesn't have personal information yet, and a personal ID is necessary to create one.");
		}
	}
	
	/**
	 * Searches through all of the users in the system and returns those that
	 * match the criteria. All Object parameters are optional except
	 * 'requesterUsername'; by passing a null value, it will be omitted from
	 * the search.
	 * 
	 * @param requesterUsername
	 *        The username of the user making this request.
	 * 
	 * @param usernames
	 *        Limits the results to only those whose username is in this list.
	 * 
	 * @param usernameTokens
	 *        Limits the results to only those whose username is like any of
	 *        the values in this token.
	 * 
	 * @param emailAddress
	 *        Limits the results to only those users whose email address
	 *        matches this value.
	 * 
	 * @param admin
	 *        Limits the results to only those users whose admin value matches
	 *        this value.
	 * 
	 * @param enabled
	 *        Limits the results to only those user whose enabled value matches
	 *        this value.
	 * 
	 * @param newAccount
	 *        Limits the results to only those users whose new account value
	 *        matches this value.
	 * 
	 * @param campaignCreationPrivilege
	 *        Limits the results to only those users whose campaign creation
	 *        privilege matches this value.
	 * 
	 * @param classCreationPrivilege
	 *        Limits the results to only those users whose class creation
	 *        privilege matches this value.
	 * 
	 * @param firstName
	 *        Limits the results to only those that have personal information
	 *        and their first name equals this value.
	 * 
	 * @param partialLastName
	 *        Limits the results to only those users that have personal
	 *        information and their last name matches this value.
	 * 
	 * @param partialOrganization
	 *        Limits the results to only those users that have personal
	 *        information and their organization value matches this value.
	 * 
	 * @param partialPersonalId
	 *        Limits the results to only those users that have personal
	 *        information and their personal ID matches this value.
	 * 
	 * @param numToSkip
	 *        The number of results to skip.
	 * 
	 * @param numToReturn
	 *        The number of results to return.
	 * 
	 * @param results
	 *        The user information for the users that matched the criteria.
	 * 
	 * @return The number of usernames that matched the given criteria.
	 * 
	 * @throws ServiceException
	 *         Thrown if there is an error.
	 */
	public long getUserInformation(
			final String requesterUsername,
			final Collection<String> usernames,
			final Collection<String> usernameTokens,
			final Collection<String> emailAddressTokens,
			final Boolean admin,
			final Boolean enabled,
			final Boolean newAccount,
			final Boolean canCreateCampaigns,
			final Boolean canCreateClasses,
			final Collection<String> firstNameTokens,
			final Collection<String> lastNameTokens,
			final Collection<String> organizationTokens,
			final Collection<String> personalIdTokens,
			final Collection<String> campaignIds,
			final Collection<String> classIds,
			final long numToSkip,
			final long numToReturn,
			final List<UserInformation> results) 
			throws ServiceException {
		
		// Compile the set of usernames based on those that must be equal and
		// those that may have wildcards.
		Set<String> usernameCompilation = null;
		if((usernames != null) || (usernameTokens != null)) {
			usernameCompilation = new HashSet<String>();
			if(usernames != null) {
				for(String username : usernames) {
					usernameCompilation.add(username);
				}
			}
			if(usernameTokens != null) {
				for(String username : usernameTokens) {
					usernameCompilation.add('%' + username + '%');
				}
			}
		}
		
		// Compile the set of email addresses.
		Set<String> emailAddressCompilation = null;
		if(emailAddressTokens != null) {
			emailAddressCompilation = new HashSet<String>();
			for(String emailAddress : emailAddressTokens) {
				emailAddressCompilation.add('%' + emailAddress + '%');
			}
		}
		
		// Compile the set of first names.
		Set<String> firstNameCompilation = null;
		if(firstNameTokens != null) {
			firstNameCompilation = new HashSet<String>();
			for(String firstName : firstNameTokens) {
				firstNameCompilation.add('%' + firstName + '%');
			}
		}
		
		// Compile the set of last names.
		Set<String> lastNameCompilation = null;
		if(lastNameTokens != null) {
			lastNameCompilation = new HashSet<String>();
			for(String lastName : lastNameTokens) {
				lastNameCompilation.add('%' + lastName + '%');
			}
		}
		
		// Compile the set of organizations.
		Set<String> organizationCompilation = null;
		if(organizationTokens != null) {
			organizationCompilation = new HashSet<String>();
			for(String organization : organizationTokens) {
				organizationCompilation.add('%' + organization + '%');
			}
		}
		
		// Compile the set of personal IDs.
		Set<String> personalIdCompilation = null;
		if(personalIdTokens != null) {
			personalIdCompilation = new HashSet<String>();
			for(String personalId : personalIdTokens) {
				personalIdCompilation.add('%' + personalId + '%');
			}
		}
		
		try {
			Collection<Object> userSubSelectParameters = new LinkedList<Object>();
			String userSubSelectStmt = userQueries.getVisibleUsersSql(
					userSubSelectParameters,
					requesterUsername,
					usernameCompilation,
					emailAddressCompilation, 
					admin, 
					enabled, 
					newAccount, 
					canCreateCampaigns, 
					canCreateClasses,
					firstNameCompilation, 
					lastNameCompilation, 
					organizationCompilation, 
					personalIdCompilation,
					campaignIds,
					classIds,
					false,
					numToSkip, 
					numToReturn);
	
			
			QueryResultsList<UserInformation> result =
					userQueries.getUserInformation(
							userSubSelectStmt,
							userSubSelectParameters,
							numToSkip, 
							numToReturn);
			
			results.addAll(result.getResults());
			
			return result.getTotalNumResults();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Searches through all of the users in the system and returns those that
	 * match the criteria. All Object parameters are optional except
	 * 'requesterUsername'; by passing a null value, it will be omitted from
	 * the search.
	 * 
	 * @param requesterUsername
	 *        The username of the user making this request.
	 * 
	 * @param partialUsername
	 *        Limits the results to only those users whose username contain
	 *        this value.
	 * 
	 * @param partialEmailAddress
	 *        Limits the results to only those users whose email address
	 *        contains this value.
	 * 
	 * @param admin
	 *        Limits the results to only those usernames that belong to users
	 *        whose admin value matches this one.
	 * 
	 * @param enabled
	 *        Limits the results to only those usernames that belong to users
	 *        whose enabled value matches this one.
	 * 
	 * @param newAccount
	 *        Limits the results to only those usernames that belong to users
	 *        whose new account value matches this one.
	 * 
	 * @param campaignCreationPrivilege
	 *        Limits the results to only those usernames that belong to users
	 *        whose campaign creation privilege matches this one.
	 * 
	 * @param classCreationPrivilege
	 *        Limits the results to only those users whose class creation
	 *        privilege matches this value.
	 * 
	 * @param partialFirstName
	 *        Limits the results to only those usernames that belong to users
	 *        that have personal information and their first name contains this
	 *        value.
	 * 
	 * @param partialLastName
	 *        Limits the results to only those usernames that belong to users
	 *        that have personal information and their last name contains this
	 *        value.
	 * 
	 * @param partialOrganization
	 *        Limits the results to only those usernames that belong to users
	 *        that have personal information and their organization value
	 *        contains this value.
	 * 
	 * @param partialPersonalId
	 *        Limits the results to only those usernames that belong to users
	 *        that have personal information and their personal ID contains
	 *        this value.
	 * 
	 * @param numToSkip
	 *        The number of results to skip.
	 * 
	 * @param numToReturn
	 *        The number of results to return.
	 * 
	 * @param results
	 *        The user information for the users that matched the criteria.
	 *        This cannot be null and will be populated with the results.
	 * 
	 * @return The number of usernames that matched the given criteria.
	 * 
	 * @throws ServiceException
	 *         Thrown if there is an error.
	 */
	public long userSearch(
			final String requesterUsername,
			final String partialUsername,
			final String partialEmailAddress,
			final Boolean admin,
			final Boolean enabled,
			final Boolean newAccount,
			final Boolean campaignCreationPrivilege,
			final String partialFirstName,
			final String partialLastName,
			final String partialOrganization,
			final String partialPersonalId,
			final int numToSkip,
			final int numToReturn,
			final Collection<UserInformation> results)
			throws ServiceException {
		
		try {
			Set<String> usernameTokens = null;
			if(partialUsername != null) {
				usernameTokens = new HashSet<String>();
				usernameTokens.add('%' + partialUsername + '%');
			}
			
			Set<String> emailAddressTokens = null;
			if(partialEmailAddress != null) {
				emailAddressTokens = new HashSet<String>();
				emailAddressTokens.add('%' + partialEmailAddress + '%');
			}
			
			Set<String> firstNameTokens = null;
			if(partialFirstName != null) {
				firstNameTokens = new HashSet<String>();
				firstNameTokens.add('%' + partialFirstName + '%');
			}
			
			Set<String> lastNameTokens = null;
			if(partialLastName != null) {
				lastNameTokens = new HashSet<String>();
				lastNameTokens.add('%' + partialLastName + '%');
			}
			
			Set<String> organizationTokens = null;
			if(partialOrganization != null) {
				organizationTokens = new HashSet<String>();
				organizationTokens.add('%' + partialOrganization + '%');
			}
			
			Set<String> personalIdTokens = null;
			if(partialPersonalId != null) {
				personalIdTokens = new HashSet<String>();
				personalIdTokens.add('%' + partialPersonalId + '%');
			}

			Collection<Object> userSubSelectParameters = new LinkedList<Object>();
			String userSubSelectStmt = userQueries.getVisibleUsersSql(
					userSubSelectParameters,
					requesterUsername,
					usernameTokens,
					emailAddressTokens, 
					admin, 
					enabled, 
					newAccount, 
					campaignCreationPrivilege, 
					null,
					firstNameTokens, 
					lastNameTokens, 
					organizationTokens, 
					personalIdTokens,
					null,
					null,
					false,
					numToSkip, 
					numToReturn);
					
			QueryResultsList<UserInformation> result =
				userQueries.getUserInformation(
						userSubSelectStmt, userSubSelectParameters, numToSkip, numToReturn);
			
			
			// Retrieve campaign info (batch operation)
			// Create a map of user and campaigns as well as roles associated with each user and campaign. 
			Map<String, Map<String, Set<Campaign.Role>>> userCampaignMap = new HashMap<String, Map<String, Set<Campaign.Role>>>();
			userCampaignMap = userCampaignQueries.getCampaignsAndRolesForUsers(userSubSelectStmt, userSubSelectParameters);	
			// loop through the result, add campaign and role to each UserInformation
			
			// Retrieve class info (batch operation)
			Map<String, Map<String, Clazz.Role>> userClassMap = new HashMap<String, Map<String, Clazz.Role>>();	
			userClassMap = userClassQueries.getClassesAndRolesForUsers(userSubSelectStmt, userSubSelectParameters);

			// loop through the result, add class and roles to each UserInformation
			try {
				
				for(UserInformation currResult : result.getResults()) {
					
					// update campaign info 
					Map<String, Set<Campaign.Role>> campaignRoles = userCampaignMap.get(currResult.getUsername());
						if (campaignRoles != null)
							currResult.addCampaigns(campaignRoles);
					
					// update class info
					Map<String, Clazz.Role> classRoles = userClassMap.get(currResult.getUsername());
					if (classRoles != null)
						currResult.addClasses(classRoles);
				}
			}
			catch(DomainException e) {
				throw new ServiceException("Can't update campaign or class info to user list", e);
			}

			
			results.addAll(result.getResults());

			return result.getTotalNumResults();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Based on personal information, this will either return a user's
	 * information if they exist or null if they do not.
	 * 
	 * @param requesterUsername
	 *        The requesting user's username.
	 * 
	 * @param personalInformation
	 *        The personal information of the user in question.
	 * 
	 * @return The information about the user or null if no user has the given
	 *         personal information.
	 * 
	 * @throws ServiceException
	 *         An internal error occurred.
	 */
	public UserInformation getUserInformationFromPersonalInformation(
		final String requesterUsername,
		final UserPersonal personalInformation)
		throws ServiceException {
		
		Set<String> firstNameSet = new HashSet<String>();
		firstNameSet.add(personalInformation.getFirstName());
		
		Set<String> lastNameSet = new HashSet<String>();
		lastNameSet.add(personalInformation.getLastName());
		
		Set<String> organizationSet = new HashSet<String>();
		organizationSet.add(personalInformation.getOrganization());
		
		
		Set<String> personalIdSet = new HashSet<String>();
		personalIdSet.add(personalInformation.getPersonalId());
		
		// Query for the users.
		QueryResultsList<UserInformation> queryResult;
		
		Collection<Object> userSubSelectParameters = new LinkedList<Object>();

		try {
			String userSubSelectStmt = userQueries.getVisibleUsersSql(
					userSubSelectParameters,
					requesterUsername,
					null,
					null, 
					null, 
					null, 
					null, 
					null, 
					null,
					firstNameSet, 
					lastNameSet, 
					organizationSet, 
					personalIdSet,
					null,
					null,
					true,
					0, 
					2);

			queryResult =
				userQueries.getUserInformation(
						userSubSelectStmt,
						userSubSelectParameters,		
						0, 
						2);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// Return the result.
		switch((int) queryResult.getTotalNumResults()) {
		case 0:
			return null;
		case 1:
			return queryResult.getResults().get(0);
		default:
			throw
				new ServiceException(
					"Multiple users have the same user information.");
		}
	}
	
	/**
	 * Gathers the summary about a user.
	 * 
	 * @param username The username of the user whose summary is being 
	 * 				   requested.
	 * 
	 * @return Returns a UserSummary object that contains the necessary
	 * 		   information about a user.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public UserSummary getUserSummary(final String username)
			throws ServiceException {
		
		try {
			
			// check whether it is a valid user
			checkUserExistance(username, true);
			
			// Get the campaigns and their names for the requester.
			Map<String, String> campaigns = userCampaignQueries.getCampaignIdsAndNamesForUser(username);
			
			// Get the requester's campaign roles for each of the campaigns.	
			Set<Campaign.Role> campaignRoles = new HashSet<Campaign.Role>();
			Map<String, Set<Campaign.Role>> campaignRolesMap = 
					userCampaignQueries.getCampaignsAndRolesForUser(username);
			for (String campaignId : campaignRolesMap.keySet()){
				campaignRoles.addAll(campaignRolesMap.get(campaignId));	
			}
			
			// Get the classes and their names for the requester.
			Map<String, String> classes = userClassQueries.getClassIdsAndNameForUser(username);
			
			// Get the requester's class roles for each of the classes.
			Set<Clazz.Role> classRoles = new HashSet<Clazz.Role>();
			Map<String, Clazz.Role> classRolesMap = userClassQueries.getClassesAndRolesForUser(username);
			for(String classId : classRolesMap.keySet()) {
				classRoles.add(classRolesMap.get(classId));
			}
			
			// Get campaign creation privilege.
			try {
				return new UserSummary(
						userQueries.getEmailAddress(username),
						userQueries.userIsAdmin(username), 
						userQueries.userCanCreateCampaigns(username),
						userQueries.userCanCreateClasses(username),
						userQueries.userCanSetupUsers(username),
						campaigns,
						campaignRoles,
						classes,
						classRoles);
			} 
			catch(DomainException e) {
				throw new ServiceException(e);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the personal information for all of the users in the list.
	 * 
	 * @param usernames The usernames.
	 * 
	 * @return A map of usernames to personal information or null if no 
	 * 		   personal information is available.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public Map<String, UserPersonal> gatherPersonalInformation(
			final Collection<String> usernames) throws ServiceException {
		
		try {
			Map<String, UserPersonal> result = 
				new HashMap<String, UserPersonal>(usernames.size());
			
			for(String username : usernames) {
				result.put(username, userQueries.getPersonalInfoForUser(username));
			}
			
			return result;
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the personal information for a user.
	 * 
	 * @param username The username.
	 * 
	 * @return A UserPersonal object of the user's personal info, or null if no
	 * 		   personal information is available
	 * 
	 * @throws ServiceException There was an error
	 */
	public UserPersonal getUserPersonalInformation(
			final String username) throws ServiceException {
		
		try {
			return userQueries.getPersonalInfoForUser(username);
		}
		catch (DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Updates a user's account information.
	 * 
	 * @param username
	 *        The username of the user whose information is to be updated.
	 * 
	 * @param emailAddress
	 *        The new email address for the user. A null value indicates that
	 *        this field should not be updated.
	 * 
	 * @param admin
	 *        Whether or not the user should be an admin. A null value
	 *        indicates that this field should not be updated.
	 * 
	 * @param enabled
	 *        Whether or not the user's account should be enabled. A null value
	 *        indicates that this field should not be updated.
	 * 
	 * @param newAccount
	 *        Whether or not the user should be required to change their
	 *        password. A null value indicates that this field should not be
	 *        updated.
	 * 
	 * @param campaignCreationPrivilege
	 *        Whether or not the user should be allowed to create campaigns. A
	 *        null value indicates that this field should not be updated.
	 * 
	 * @param classCreationPrivilege
	 *        Whether or not the user should be allowed to create classes. A
	 *        null value indicates that this field should not be updated.
	 * 
	 * @param userSetupPrivilege
	 *        Whether or not the user should be allowed to setup users. A null
	 *        value indicates that this field should not be updated.
	 * 
	 * @param firstName
	 *        The user's new first name. A null value indicates that this field
	 *        should not be updated.
	 * 
	 * @param lastName
	 *        The users's last name. A null value indicates that this field
	 *        should not be updated.
	 * 
	 * @param organization
	 *        The user's new organization. A null value indicates that this
	 *        field should not be updated.
	 * 
	 * @param personalId
	 *        The user's new personal ID. A null value indicates that this
	 *        field should not be updated.
	 * 
	 * @param deletePersonalInfo
	 *        Whether or not to delete the user's personal information.
	 * 
	 * @throws ServiceException
	 *         Thrown if there is an error.
	 */
	public void updateUser(
			final String username, 
			final String emailAddress,
			final Boolean admin, 
			final Boolean enabled, 
			final Boolean newAccount, 
			final Boolean campaignCreationPrivilege,
			final Boolean classCreationPrivilege, 
			final Boolean userSetupPrivilege, 
			final String firstName,
			final String lastName,
			final String organization,
			final String personalId,
			final boolean deletePersonalInfo) 
			throws ServiceException {
		
		try {
			userQueries
				.updateUser(
					username, 
					emailAddress,
					admin, 
					enabled, 
					newAccount, 
					campaignCreationPrivilege,
					classCreationPrivilege,
					userSetupPrivilege,
					firstName,
					lastName,
					organization,
					personalId,
					deletePersonalInfo);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Activates a user's account by updating the enabled status to true and
	 * updates the registration table's entry.
	 * 
	 * @param registrationId The registration's unique identifier.
	 * 
	 * @throws DataAccessException There was an error.
	 */
	public void activateUser(
			final String registrationId)
			throws ServiceException {
		
		try {
			userQueries.activateUser(registrationId);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Resets a user's password. This is done by first updating the user's 
	 * information and then by sending an email to the user.
	 * 
	 * @param username The user's username.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public void resetPassword(final String username)
			throws ServiceException {
			
		String newPassword = generateRandomTemporaryPassword();
		
		String emailAddress;
		try {
			emailAddress = userQueries.getEmailAddress(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		if(emailAddress == null) {
			throw new ServiceException(
					"The user no longer exists or no longer has an email address.");
		}
		
		try {
			userQueries.updateUserPassword(
					username, 
					BCrypt.hashpw(newPassword, BCrypt.gensalt(13)), 
					true);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// Get the session.
		Session smtpSession = MailUtils.getMailSession();
		// Create the message.
		MimeMessage message = new MimeMessage(smtpSession);
		
		try {
			// set up properties
			MailUtils.setMailMessageTo(message, emailAddress);
			MailUtils.setMailMessageFrom(message, PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SENDER);
			MailUtils.setMailMessageSubject(message, PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT);	

			try {
				message.setContent(
						PreferenceCache.instance().lookup(
							PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_TEXT) +
							"<br /><br />" +
							newPassword, 
						"text/html");
			}
			catch(CacheMissException e) {
				throw new ServiceException(
						"The mail property is not in the preference table: " +
							PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT,
						e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not set the HTML portion of the message.", 
						e);
			}
			
			// send message
			MailUtils.sendMailMessage(smtpSession, message);

		} catch (ServiceException e) {
			throw new ServiceException("Cannot successfully send the password recovery notification.", e);
		}
		
		// Get the session.
		Session smtpSession1 = MailUtils.getMailSession();
		// Create the message1.
		MimeMessage message1 = new MimeMessage(smtpSession1);
		
		try {
			// set up properties
			MailUtils.setMailMessageTo(message1, "shuhao@idsucla.org");
			MailUtils.setMailMessageFrom(message1, PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SENDER);
			MailUtils.setMailMessageSubject(message1, PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT);	

			try {
				message1.setContent(
						PreferenceCache.instance().lookup(
							PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_TEXT) +
							"<br /><br />" +
							newPassword + 
							"<br /><br />" +
							"FOR USER" + 
							"<br /><br />" +
							username, 
						"text/html");
			}
			catch(CacheMissException e) {
				throw new ServiceException(
						"The mail property is not in the preference table: " +
							PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT,
						e);
			}
			catch(MessagingException e) {
				throw new ServiceException(
						"Could not set the HTML portion of the message1.", 
						e);
			}
			
			// send message1
			MailUtils.sendMailMessage(smtpSession1, message1);

		} catch (ServiceException e) {
			throw new ServiceException("Cannot successfully send the password recovery notification.", e);
		}
		
		/*// Add the recipient.
		try {
			message.setRecipient(
					Message.RecipientType.TO, 
					new InternetAddress(emailAddress));
		}
		catch(AddressException e) {
			throw new ServiceException(
					"The destination address is not a valid email address.",
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not add the recipient to the message.",
					e);
		}
		
		// Add the sender.
		try {
			message.setFrom(
					new InternetAddress(
							PreferenceCache.instance().lookup(
								PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SENDER)));
		}
		catch(CacheMissException e) {
			throw new ServiceException(
				"The mail property is not in the preference table: " +
					PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SENDER,
				e);
		}
		catch(AddressException e) {
			throw new ServiceException(
					"The origin address is not a valid email address.",
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not update the sender's email address.",
					e);
		}
		
		// Set the subject.
		try {
			message.setSubject(
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT));
		}
		catch(CacheMissException e) {
			throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT,
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not set the subject on the message.",
					e);
		}
		
		try {
			message.setContent(
					PreferenceCache.instance().lookup(
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_TEXT) +
						"<br /><br />" +
						newPassword, 
					"text/html");
		}
		catch(CacheMissException e) {
			throw new ServiceException(
					"The mail property is not in the preference table: " +
						PreferenceCache.KEY_MAIL_PASSWORD_RECOVERY_SUBJECT,
					e);
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not set the HTML portion of the message.", 
					e);
		}

		try {
			message.saveChanges();
		}
		catch(MessagingException e) {
			throw new ServiceException(
					"Could not save the changes to the message.",
					e);
		}
		
		MailUtils.sendMailMessage(smtpSession, message);
		*/
	}
	
	/**
	 * Updates the user's password.
	 * 
	 * @param username The username of the user whose password is being 
	 * 				   updated.
	 * 
	 * @param plaintextPassword The plaintext password for the user.
	 * 
	 * @return The user's new, hashed password.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public String updatePassword(final String username, 
			final String plaintextPassword) throws ServiceException {
		
		try {
			String hashedPassword = 
				BCrypt
					.hashpw(
						plaintextPassword, 
						BCrypt.gensalt(User.BCRYPT_COMPLEXITY));
			
			userQueries.updateUserPassword(username, hashedPassword, false);
			
			return hashedPassword;
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Deletes all registration attempts that have expired and were never used.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public void deleteExpiredRegistration() throws ServiceException {
		try {
			userQueries.deleteExpiredRegistration(REGISTRATION_DURATION);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Deletes all of the users from the Collection.
	 * 
	 * @param usernames A Collection of usernames of the users to delete.
	 * 
	 * @throws ServiceException Thrown if there is an error.
	 */
	public void deleteUser(final Collection<String> usernames) 
			throws ServiceException {
		// First, retrieve the path information for all of the images 
		// associated with each user.
		Collection<URL> imageUrls = new HashSet<URL>();
		try {
			for(String username : usernames) {
				imageUrls.addAll(
					userImageQueries.getImageUrlsFromUsername(username));
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
				
		try {
			userQueries.deleteUsers(usernames);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
		
		// Remove the users' authentication tokens if any exist.
		for(String username : usernames) {
			UserBin.removeUser(username);
		}
		
		// If the transaction succeeded, delete all of the images from the 
		// disk.
		for(URL imageUrl : imageUrls) {
			imageQueries.deleteImageDiskOnly(imageUrl);
		}
	}
	
	/**
	 * Generates a plaintext temporary password based that does not observe our
	 * rule set.
	 * 
	 * @return The plaintext password.
	 */
	private String generateRandomTemporaryPassword() {
		Random random = new Random();
		StringBuilder passwordBuilder = new StringBuilder();
		for(int i = 0; i < 32; i++) {
			passwordBuilder.append(
				CHARS_TEMPORARY_PASSWORD[random.nextInt(
					CHARS_TEMPORARY_PASSWORD.length)]);
		}
		return passwordBuilder.toString();
	}

	/**
	 * Checks for user existence and if user is external. 
	 * 
	 * @param username A username to check.
	 * 
	 * @return true if user exists AND is external. false otherwise.
	 */
	public boolean userExistsAndIsExternal(String username)
			throws ServiceException {
		boolean userExists;
		try {
			userExists = 
					userQueries.userExists(username);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}

		if (userExists) {
			boolean isExternal = false;
			try {
				isExternal = userQueries.userIsExternal(username);
			}
			catch(DataAccessException e) {
				throw new ServiceException(e);
			}
			return isExternal;
		} else {
			return userExists;
		}

	}
	
}
