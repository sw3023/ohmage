package org.ohmage.request.user;

import java.io.IOException;
import org.json.JSONException;

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

public class UserViewResetRequest extends Request {
	private static final Logger LOGGER = 
			Logger.getLogger(UserViewResetRequest.class);
	
	private final String username;
	private final String emailAddress;
	
	//private final boolean validSession;
	
	private final String message;
	
	private String messageR;
	
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
	public UserViewResetRequest(final HttpServletRequest httpRequest) throws IOException, InvalidRequestException {
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
				
				//====Validation EMAIL_ADDRESS
				t = getParameterValues(InputKeys.EMAIL_ADDRESS);
				if(t.length > 1) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_EMAIL_ADDRESS,
							"Multiple email addresses were given: " +
								InputKeys.EMAIL_ADDRESS);
				}
				else if(t.length == 0) {
					throw new ValidationException(
							ErrorCode.USER_INVALID_EMAIL_ADDRESS,
							"The email address is missing: " +
								InputKeys.EMAIL_ADDRESS);
				}
				else {
					//tEmailAddress = UserValidators.validateEmailAddress(t[0]);
					tEmailAddress = t[0];
					if(tEmailAddress == null) {
						throw new ValidationException(
								ErrorCode.USER_INVALID_EMAIL_ADDRESS,
								"The email address is missing: " +
									InputKeys.EMAIL_ADDRESS);
					}
				}
			}
			catch(ValidationException e) {
				e.failRequest(this);
				e.logException(LOGGER);
			}
		}
		
		username = tUsername;
		emailAddress = tEmailAddress;
		message = tEmailAddress;
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
			
			//LOGGER.info("Checking if the email address is correct.");
			//UserServices.instance().isUserEmailCorrect(username, emailAddress);
			
			//LOGGER.info("Sending a password reset email.");
			//UserServices.instance().resetPassword(username);
			
			messageR = UserServices.instance().resetView(username, emailAddress);
			
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
		JSONObject jsonResult = new JSONObject();
			try {
				jsonResult.put("view", messageR);
			}
			catch(JSONException e) {
				LOGGER.error("Error building the result JSONObject for UserViewResetRequest.", e);
				setFailed();
			}
		super.respond(httpRequest, httpResponse, jsonResult);
	}
}