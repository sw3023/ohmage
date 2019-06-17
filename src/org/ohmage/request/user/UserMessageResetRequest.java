package org.ohmage.request.user;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.exception.InvalidRequestException;
import org.ohmage.exception.ServiceException;
import org.ohmage.exception.ValidationException;
import org.ohmage.request.InputKeys;
import org.ohmage.request.Request;
import org.ohmage.service.UserServices;
import org.ohmage.validator.UserValidators;

public class UserMessageResetRequest extends Request {
	private static final Logger LOGGER = 
			Logger.getLogger(UserMessageResetRequest.class);
	
	private final String username;
	private final String emailAddress;
	
	private final boolean validSession;
	
	private final String Message;
	
	/**
	 * Creates a password reset request.
	 * 
	 * @param httpRequest The HTTP request.
	 * 
	 * @throws InvalidRequestException Thrown if the parameters cannot be 
	 * 								   parsed.
	 * 
	 * @throws IOException There was an error reading from the request.
	 */
	public UserMessageResetRequest(final HttpServletRequest httpRequest) throws IOException, InvalidRequestException {
		super(httpRequest, null);
		
		String tUsername = null;
		String tEmailAddress = null;
		
		if(! isFailed()) {
			String[] t;
			LOGGER.info("Creating a new password reset request.");
			
			//====Validation USERNAME
			try {
				t = getParameterValues(InputKeys.USERNAME);
				if(t.length > 1) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_USERNAME,
							"Multiple usernames were given: " +
								InputKeys.USERNAME);
				}
				else if(t.length == 0) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_USERNAME,
							"The username is missing: " +
								InputKeys.USERNAME);
				}
				else {
					tUsername = UserValidators.validateUsername(t[0]);
					
					if(tUsername == null) {
						throw new ValidationException(
								ErrorCode.USER_INVALID_USERNAME,
								"The username is missing: " +
									InputKeys.USERNAME);
					}
				}
				
				//====Validation MESSAGE
				t = getParameterValues(InputKeys.MESSAGE);
				if(t.length > 1) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_MESSAGE,
							"Multiple message were given: " +
								InputKeys.MESSAGE);
				}
				else if(t.length == 0) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_MESSAGE,
							"The message is missing: " +
								InputKeys.MESSAGE);
				}
				else {
					//tEmailAddress = UserValidators.validateEmailAddress(t[0]);
					//
					//if(tEmailAddress == null) {
					//	throw new ValidationException(
					//			ErrorCode.USER_INVALID_MESSAGE,
					//			"The email address is missing: " +
					//				InputKeys.MESSAGE);
					//}
				}
			}
			catch(ValidationException e) {
				e.failRequest(this);
				e.logException(LOGGER);
			}
		}
		
		username = tUsername;
		emailAddress = tEmailAddress;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.ohmage.request.Request#service()
	 */
	@Override
	public void service() {
		try {
			LOGGER.info("Checking if the user exists.");
			UserServices.instance().checkUserExistance(username, true);
			
			LOGGER.info("Checking if the email address is correct.");
			UserServices.instance().isUserEmailCorrect(username, emailAddress);
			
			LOGGER.info("Sending a password reset email.");
			UserServices.instance().resetPassword(username);
		}
		catch(ServiceException e) {
			LOGGER.info("Something failed validation. We do not fail the request when things fail because we do not want to leak information.");
			e.logException(LOGGER);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.ohmage.request.Request#respond(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public void respond(
		final HttpServletRequest httpRequest,
		final HttpServletResponse httpResponse) {
		
		super.respond(httpRequest, httpResponse, new JSONObject());
	}
}